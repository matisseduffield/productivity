package com.bento.calendar.data

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Result of decoding a calendar file before it is merged into [AppData]. */
data class IcsImportResult(
    val events: List<EventItem>,
    val duplicates: Int,
    val skipped: Int,
    val sourceEvents: Int,
    val validCalendar: Boolean,
)

private data class IcsProperty(
    val name: String,
    val params: Map<String, String>,
    val value: String,
)

private data class IcsStamp(
    val date: LocalDate,
    val time: LocalTime?,
) {
    val allDay: Boolean get() = time == null
}

private data class DecodedIcsBlock(
    val event: EventItem?,
    val uid: String,
    val recurrenceDate: LocalDate?,
    val cancelled: Boolean,
)

private val DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE
private val DATE_TIME_SECONDS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT)
private val DATE_TIME_MINUTES = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm", Locale.ROOT)
private val UTC_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
    .withZone(ZoneOffset.UTC)

/**
 * Export Bento events as an RFC 5545 calendar. The file carries standard
 * VEVENT fields for other calendar apps plus small X-BENTO hints so a later
 * round-trip can retain category ids and reminder minutes exactly.
 */
fun exportEventsToIcs(
    events: List<EventItem>,
    categories: List<Category>,
    generatedAt: Instant = Instant.now(),
): String {
    val labels = categories.associate { it.id to it.label }
    val lines = mutableListOf(
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Bento Calendar//Calendar Export//EN",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        "X-WR-CALNAME:Bento Calendar",
    )

    events.sortedWith(compareBy<EventItem> { it.date }.thenBy { it.start }.thenBy { it.title })
        .forEach { event ->
            val startDate = event.date.toDate()
            val lastDate = event.spanEnd()
            lines += "BEGIN:VEVENT"
            lines += "UID:${icsText(event.id)}@bento.calendar"
            lines += "DTSTAMP:${UTC_STAMP.format(generatedAt)}"
            lines += "X-BENTO-ID:${icsText(event.id)}"
            lines += "SUMMARY:${icsText(event.title.ifBlank { "Untitled" })}"

            if (event.allDay) {
                lines += "DTSTART;VALUE=DATE:${DATE_FMT.format(startDate)}"
                // DTEND for DATE values is exclusive.
                lines += "DTEND;VALUE=DATE:${DATE_FMT.format((lastDate ?: startDate).plusDays(1))}"
            } else {
                lines += "DTSTART:${dateTimeValue(startDate, event.start.toTime())}"
                lines += "DTEND:${dateTimeValue(lastDate ?: startDate, event.end.toTime())}"
            }

            when (event.recur) {
                Recur.DAILY -> lines += "RRULE:FREQ=DAILY"
                Recur.WEEKLY -> lines += "RRULE:FREQ=WEEKLY"
                Recur.MONTHLY -> lines += "RRULE:FREQ=MONTHLY"
            }
            if (event.exDates.isNotEmpty() && event.recur != Recur.NONE) {
                val values = event.exDates.distinct().sorted().map { excluded ->
                    val date = excluded.toDate()
                    if (event.allDay) DATE_FMT.format(date)
                    else dateTimeValue(date, event.start.toTime())
                }
                val kind = if (event.allDay) ";VALUE=DATE" else ""
                lines += "EXDATE$kind:${values.joinToString(",")}"
            }

            labels[event.cat]?.takeIf { it.isNotBlank() }?.let {
                lines += "CATEGORIES:${icsText(it)}"
            }
            lines += "X-BENTO-CATEGORY:${icsText(event.cat)}"
            event.loc.takeIf { it.isNotBlank() }?.let { lines += "LOCATION:${icsText(it)}" }
            event.remind?.coerceAtLeast(0)?.let { minutes ->
                lines += "X-BENTO-REMIND:$minutes"
                lines += "BEGIN:VALARM"
                lines += "TRIGGER:${if (minutes == 0) "PT0M" else "-PT${minutes}M"}"
                lines += "ACTION:DISPLAY"
                lines += "DESCRIPTION:${icsText(event.title.ifBlank { "Event reminder" })}"
                lines += "END:VALARM"
            }
            lines += "END:VEVENT"
        }

    lines += "END:VCALENDAR"
    return lines.flatMap(::foldIcsLine).joinToString("\r\n", postfix = "\r\n")
}

/**
 * Decode common VEVENT files into Bento's event model. Import is intentionally
 * additive. Stable ids derived from UID (or Bento's own X-BENTO-ID) make
 * importing the same file twice a no-op instead of duplicating every event.
 *
 * Recurrence is kept only when Bento can represent it without changing its
 * meaning: a simple daily/weekly/monthly rule with interval 1 and no end/count.
 */
fun importEventsFromIcs(
    text: String,
    categories: List<Category>,
    existingIds: Set<String> = emptySet(),
    localZone: ZoneId = ZoneId.systemDefault(),
): IcsImportResult {
    val lines = unfoldIcsLines(text)
    val hasStart = lines.any { it.equals("BEGIN:VCALENDAR", ignoreCase = true) }
    val hasEnd = lines.any { it.equals("END:VCALENDAR", ignoreCase = true) }
    if (!hasStart || !hasEnd) {
        return IcsImportResult(emptyList(), 0, 0, 0, validCalendar = false)
    }

    val blocks = mutableListOf<List<String>>()
    var current: MutableList<String>? = null
    for (line in lines) {
        when {
            line.equals("BEGIN:VEVENT", ignoreCase = true) -> current = mutableListOf()
            line.equals("END:VEVENT", ignoreCase = true) -> {
                current?.let(blocks::add)
                current = null
            }
            current != null -> current += line
        }
    }

    val decoded = blocks.map { block ->
        val properties = block.mapNotNull(::parseProperty)
        val uid = properties.firstOrNull { it.name == "UID" }
            ?.value?.let(::unescapeIcsText).orEmpty()
        val recurrenceDate = properties.firstOrNull { it.name == "RECURRENCE-ID" }
            ?.let { parseStamp(it, localZone) }?.date
        val cancelled = properties.firstOrNull { it.name == "STATUS" }
            ?.value.equals("CANCELLED", ignoreCase = true)
        val event = if (cancelled) null else {
            runCatching { decodeEvent(properties, categories, localZone, block) }.getOrNull()
        }
        DecodedIcsBlock(event, uid, recurrenceDate, cancelled)
    }

    // Google Calendar and Outlook represent a changed/cancelled occurrence as
    // another VEVENT with the same UID plus RECURRENCE-ID. Bento represents
    // that shape as an exDate on the series and (for a change) a standalone
    // event. Apply the exDates before deduping the decoded list.
    val baseUids = decoded.filter {
        it.uid.isNotBlank() && it.recurrenceDate == null &&
            it.event?.recur?.let { recur -> recur != Recur.NONE } == true
    }.mapTo(mutableSetOf()) { it.uid }
    val overridesByUid = decoded.filter {
        it.uid in baseUids && it.recurrenceDate != null && (it.cancelled || it.event != null)
    }.groupBy { it.uid }

    val imported = mutableListOf<EventItem>()
    val seen = existingIds.toMutableSet()
    var duplicates = 0
    var skipped = 0
    for (item in decoded) {
        if (item.cancelled) {
            // A cancellation is meaningful only when its base series is in
            // this import. It contributes an exDate but no standalone event.
            if (item.uid !in baseUids || item.recurrenceDate == null) skipped++
            continue
        }
        var event = item.event
        if (event == null) {
            skipped++
            continue
        }
        if (item.recurrenceDate == null && event.recur != Recur.NONE) {
            val overrideDates = overridesByUid[item.uid].orEmpty()
                .mapNotNull { it.recurrenceDate?.toIso() }
            event = event.copy(exDates = (event.exDates + overrideDates).distinct().sorted())
        } else if (item.recurrenceDate != null) {
            // The override's DTSTART may intentionally move it to another
            // day/time; keep that concrete value as a standalone event.
            event = event.copy(recur = Recur.NONE, exDates = emptyList())
        }
        if (!seen.add(event.id)) {
            duplicates++
        } else {
            imported += event
        }
    }

    return IcsImportResult(
        events = imported,
        duplicates = duplicates,
        skipped = skipped,
        sourceEvents = blocks.size,
        validCalendar = true,
    )
}

private fun decodeEvent(
    properties: List<IcsProperty>,
    categories: List<Category>,
    localZone: ZoneId,
    rawBlock: List<String>,
): EventItem? {
    fun first(name: String) = properties.firstOrNull { it.name == name }

    val startProp = first("DTSTART") ?: return null
    val start = parseStamp(startProp, localZone) ?: return null
    val endProp = first("DTEND")
    val parsedEnd = endProp?.let { parseStamp(it, localZone) }
    val duration = first("DURATION")?.value?.let(::positiveDuration)
    val title = first("SUMMARY")?.value?.let(::unescapeIcsText)
        ?.trim()?.takeCodePoints(500)?.ifBlank { null } ?: "Untitled"

    val (endDate, endTime, allDay) = if (start.allDay) {
        val exclusive = parsedEnd?.date
            ?: duration?.toDays()?.takeIf { it > 0 }?.let(start.date::plusDays)
            ?: start.date.plusDays(1)
        if (!exclusive.isAfter(start.date)) return null
        Triple(exclusive.minusDays(1).takeIf { it.isAfter(start.date) }, LocalTime.of(23, 59), true)
    } else {
        val startTime = start.time ?: return null
        val endStamp = parsedEnd ?: run {
            val fallback = LocalDateTime.of(start.date, startTime)
                .plus(duration ?: Duration.ofHours(1))
            IcsStamp(fallback.toLocalDate(), fallback.toLocalTime())
        }
        val finishTime = endStamp.time ?: return null
        val begins = LocalDateTime.of(start.date, startTime)
        val finishes = LocalDateTime.of(endStamp.date, finishTime)
        if (!finishes.isAfter(begins)) return null
        Triple(endStamp.date.takeIf { it.isAfter(start.date) }, finishTime, false)
    }

    val recurrenceId = first("RECURRENCE-ID")?.value.orEmpty()
    val bentoId = first("X-BENTO-ID")?.value?.let(::unescapeIcsText)
        ?.takeIf { SAFE_ID.matches(it) && recurrenceId.isEmpty() }
    val uid = first("UID")?.value?.let(::unescapeIcsText).orEmpty()
    val stableKey = if (uid.isNotBlank()) "$uid|$recurrenceId"
    else rawBlock.joinToString("\n")
    val id = bentoId ?: stableIcsId("$uid|$recurrenceId|$stableKey")

    val recurrence = simpleRecurrence(first("RRULE")?.value, start.date)
    // A multi-day span and recurrence cannot coexist in Bento. Preserve the
    // concrete span, which is safer than turning it into an infinite series.
    val recur = if (endDate != null) Recur.NONE else recurrence
    val exDates = if (recur == Recur.NONE) emptyList() else {
        properties.filter { it.name == "EXDATE" }
            .flatMap { property ->
                splitIcsList(property.value).mapNotNull { value ->
                    parseStamp(property.copy(value = value), localZone)?.date?.toIso()
                }
            }
            .distinct()
            .sorted()
    }

    val categoryId = categoryFor(properties, categories)
    val remind = first("X-BENTO-REMIND")?.value?.trim()?.toIntOrNull()
        ?.takeIf { it >= 0 }
        ?: properties.firstOrNull { it.name == "TRIGGER" }?.value?.let(::triggerMinutes)

    return EventItem(
        id = id,
        title = title,
        date = start.date.toIso(),
        start = (start.time ?: LocalTime.MIDNIGHT).toHm(),
        end = endTime.toHm(),
        cat = categoryId,
        recur = recur,
        remind = remind,
        loc = first("LOCATION")?.value?.let(::unescapeIcsText)
            ?.trim()?.takeCodePoints(1000).orEmpty(),
        allDay = allDay,
        exDates = exDates,
        endDate = endDate?.toIso(),
    )
}

private fun categoryFor(properties: List<IcsProperty>, categories: List<Category>): String {
    val byId = properties.firstOrNull { it.name == "X-BENTO-CATEGORY" }
        ?.value?.let(::unescapeIcsText)
    categories.firstOrNull { it.id == byId }?.let { return it.id }

    val labels = properties.filter { it.name == "CATEGORIES" }
        .flatMap { splitIcsList(it.value) }
        .map { unescapeIcsText(it).trim() }
    categories.firstOrNull { cat -> labels.any { it.equals(cat.label, ignoreCase = true) } }
        ?.let { return it.id }
    return categories.firstOrNull()?.id ?: Cats.WORK
}

private fun simpleRecurrence(value: String?, start: LocalDate): String {
    if (value.isNullOrBlank()) return Recur.NONE
    val parts = value.split(';').mapNotNull {
        val index = it.indexOf('=')
        if (index <= 0) null else it.substring(0, index).uppercase(Locale.ROOT) to
            it.substring(index + 1).uppercase(Locale.ROOT)
    }.toMap()
    if (parts["INTERVAL"]?.toIntOrNull()?.let { it != 1 } == true) return Recur.NONE
    if ("COUNT" in parts || "UNTIL" in parts) return Recur.NONE

    return when (parts["FREQ"]) {
        "DAILY" -> if (parts.keys.none { it.startsWith("BY") }) Recur.DAILY else Recur.NONE
        "WEEKLY" -> {
            val byDay = parts["BYDAY"]?.split(',')
            if (byDay == null || byDay == listOf(dayCode(start))) Recur.WEEKLY else Recur.NONE
        }
        "MONTHLY" -> {
            val monthDay = parts["BYMONTHDAY"]?.toIntOrNull()
            if (monthDay == null || monthDay == start.dayOfMonth) Recur.MONTHLY else Recur.NONE
        }
        else -> Recur.NONE
    }
}

private fun dayCode(date: LocalDate): String =
    listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")[date.dayOfWeek.value - 1]

private fun triggerMinutes(value: String): Int? {
    val trimmed = value.trim().uppercase(Locale.ROOT)
    if (!trimmed.startsWith("-P") && trimmed != "PT0M") return null
    return runCatching {
        val duration = Duration.parse(if (trimmed.startsWith('-')) trimmed.drop(1) else trimmed)
        duration.toMinutes().takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
    }.getOrNull()
}

private fun positiveDuration(value: String): Duration? = runCatching {
    Duration.parse(value.trim().uppercase(Locale.ROOT)).takeIf { !it.isZero && !it.isNegative }
}.getOrNull()

private fun parseStamp(property: IcsProperty, localZone: ZoneId): IcsStamp? {
    val raw = property.value.trim()
    val dateOnly = property.params["VALUE"].equals("DATE", ignoreCase = true) ||
        raw.matches(Regex("\\d{8}"))
    if (dateOnly) {
        return runCatching { IcsStamp(LocalDate.parse(raw.take(8), DATE_FMT), null) }.getOrNull()
    }

    val utc = raw.endsWith('Z', ignoreCase = true)
    val body = if (utc) raw.dropLast(1) else raw
    val local = parseLocalDateTime(body) ?: return null
    if (utc) {
        val converted = local.atZone(ZoneOffset.UTC).withZoneSameInstant(localZone).toLocalDateTime()
        return IcsStamp(converted.toLocalDate(), converted.toLocalTime())
    }
    val tzid = property.params["TZID"]?.trim('"')
    if (!tzid.isNullOrBlank()) {
        val converted = runCatching {
            local.atZone(ZoneId.of(tzid)).withZoneSameInstant(localZone).toLocalDateTime()
        }.getOrNull()
        if (converted != null) return IcsStamp(converted.toLocalDate(), converted.toLocalTime())
    }
    return IcsStamp(local.toLocalDate(), local.toLocalTime())
}

private fun parseLocalDateTime(value: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(value, DATE_TIME_SECONDS) }.getOrElse {
        runCatching { LocalDateTime.parse(value, DATE_TIME_MINUTES) }.getOrNull()
    }

private fun parseProperty(line: String): IcsProperty? {
    val colon = line.indexOf(':')
    if (colon <= 0) return null
    val head = line.substring(0, colon).split(';')
    val name = head.first().uppercase(Locale.ROOT)
    val params = head.drop(1).mapNotNull { token ->
        val equals = token.indexOf('=')
        if (equals <= 0) null else token.substring(0, equals).uppercase(Locale.ROOT) to
            token.substring(equals + 1)
    }.toMap()
    return IcsProperty(name, params, line.substring(colon + 1))
}

private fun unfoldIcsLines(text: String): List<String> {
    val result = mutableListOf<String>()
    text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
        if ((line.startsWith(' ') || line.startsWith('\t')) && result.isNotEmpty()) {
            result[result.lastIndex] = result.last() + line.drop(1)
        } else if (line.isNotEmpty()) {
            result += line
        }
    }
    return result
}

private fun splitIcsList(value: String): List<String> {
    val result = mutableListOf<String>()
    val part = StringBuilder()
    var escaped = false
    for (char in value) {
        when {
            escaped -> {
                part.append('\\').append(char)
                escaped = false
            }
            char == '\\' -> escaped = true
            char == ',' -> {
                result += part.toString()
                part.clear()
            }
            else -> part.append(char)
        }
    }
    if (escaped) part.append('\\')
    result += part.toString()
    return result
}

private fun dateTimeValue(date: LocalDate, time: LocalTime): String =
    DATE_TIME_SECONDS.format(LocalDateTime.of(date, time))

private fun icsText(value: String): String = buildString(value.length) {
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '\n', '\r' -> append("\\n")
            ',' -> append("\\,")
            ';' -> append("\\;")
            else -> append(char)
        }
    }
}

private fun unescapeIcsText(value: String): String = buildString(value.length) {
    var index = 0
    while (index < value.length) {
        if (value[index] == '\\' && index + 1 < value.length) {
            when (val next = value[index + 1]) {
                'n', 'N' -> append('\n')
                '\\', ',', ';' -> append(next)
                else -> append(next)
            }
            index += 2
        } else {
            append(value[index++])
        }
    }
}

/** Fold at 75 UTF-8 octets without splitting a Unicode code point. */
private fun foldIcsLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var chunk = StringBuilder()
    var bytes = 0
    var index = 0
    var limit = 75
    while (index < line.length) {
        val codePoint = Character.codePointAt(line, index)
        val chars = String(Character.toChars(codePoint))
        val size = chars.toByteArray(StandardCharsets.UTF_8).size
        if (bytes + size > limit && chunk.isNotEmpty()) {
            result += if (result.isEmpty()) chunk.toString() else " $chunk"
            chunk = StringBuilder()
            bytes = 0
            limit = 74 // continuation's leading space is the 75th octet
        }
        chunk.append(chars)
        bytes += size
        index += Character.charCount(codePoint)
    }
    result += if (result.isEmpty()) chunk.toString() else " $chunk"
    return result
}

private fun stableIcsId(key: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(StandardCharsets.UTF_8))
    return "xics" + digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** Bound untrusted display text without cutting a UTF-16 surrogate pair. */
private fun String.takeCodePoints(max: Int): String {
    var index = 0
    var count = 0
    while (index < length && count < max) {
        index += Character.charCount(Character.codePointAt(this, index))
        count++
    }
    return substring(0, index)
}

private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
