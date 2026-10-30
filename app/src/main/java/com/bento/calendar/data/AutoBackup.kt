package com.bento.calendar.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.time.LocalDate

/**
 * Daily copy of the full backup file into a folder the user picked (Drive,
 * a USB stick, Downloads...). Complements Android's own Google backup, which
 * the user can't browse or restore a single day from.
 *
 * Files are named by date, so a day's backup is overwritten in place and the
 * oldest beyond [KEEP] are removed. Only files matching that name pattern are
 * ever touched; anything else in the folder is left alone.
 */
object AutoBackup {
    const val KEEP = 7

    /** A little under a day, so opening the app at a similar time each day still counts. */
    private const val INTERVAL_MS = 20 * 60 * 60 * 1000L

    private val NAME = Regex("""bento-backup-(\d{4}-\d{2}-\d{2})\.json""")

    fun fileName(date: LocalDate): String = "bento-backup-$date.json"

    fun isDue(lastAt: Long, now: Long): Boolean = lastAt <= 0 || now - lastAt >= INTERVAL_MS || now < lastAt

    /** Dated backup files beyond the newest [keep], oldest first. */
    fun toPrune(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter { NAME.matches(it) }.distinct().sortedDescending().drop(keep).reversed()

    /** Writes today's file and prunes old ones. False if the folder can't be used. */
    fun write(context: Context, folder: Uri, json: String, today: LocalDate): Boolean = try {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val children = listChildren(context, folder)
        val name = fileName(today)
        val target = children[name]
            ?: DocumentsContract.createDocument(resolver, parent, "application/json", name)
            ?: error("create failed")
        val out = try {
            resolver.openOutputStream(target, "wt")
        } catch (_: Exception) {
            resolver.openOutputStream(target)
        } ?: error("open failed")
        out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        toPrune(children.keys.toList() + name).forEach { old ->
            children[old]?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun listChildren(context: Context, folder: Uri): Map<String, Uri> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folder, DocumentsContract.getTreeDocumentId(folder),
        )
        val found = mutableMapOf<String, Uri>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if (NAME.matches(name)) found[name] = DocumentsContract.buildDocumentUriUsingTree(folder, id)
            }
        }
        return found
    }
}
