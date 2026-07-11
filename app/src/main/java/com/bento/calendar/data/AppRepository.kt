package com.bento.calendar.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object AppDataSerializer : Serializer<AppData> {
    override val defaultValue: AppData
        get() = AppData()

    override suspend fun readFrom(input: InputStream): AppData = try {
        json.decodeFromString(AppData.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot parse app data", e)
    }

    override suspend fun writeTo(t: AppData, output: OutputStream) {
        output.write(json.encodeToString(AppData.serializer(), t).encodeToByteArray())
    }
}

private val Context.appDataStore: DataStore<AppData> by dataStore(
    fileName = "bento.calendar.v1.json",
    serializer = AppDataSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { AppData() },
)

class AppRepository(context: Context) {
    private val store = context.applicationContext.appDataStore
    private val domain = DomainStore(BentoDatabase.get(context.applicationContext))
    private val writeMutex = Mutex()

    private data class PrimaryRows(
        val events: List<EventRecord>,
        val tasks: List<TaskRecord>,
        val notes: List<NoteRecord>,
    )

    private data class SecondaryRows(
        val categories: List<CategoryRecord>,
        val trash: List<TrashRecord>,
    )

    private data class PlannerRows(
        val taskBlocks: List<TaskBlockRecord>,
        val dayPlans: List<DayPlanRecord>,
        val focusSessions: List<FocusSessionRecord>,
        val focusDailyTotals: List<FocusDailyTotalRecord>,
    )

    /**
     * Compatibility snapshot for the Compose UI. Domain rows now come from
     * Room; preferences and the PIN remain in DataStore. Feature repositories
     * can move to focused Room queries without forcing every screen to change
     * in the same release.
     */
    val data: Flow<AppData> = flow {
        val legacy = store.data.first()
        domain.ensureLegacyImported(legacy)
        val primary = combine(domain.events, domain.tasks, domain.notes) { events, tasks, notes ->
            PrimaryRows(events, tasks, notes)
        }
        val secondary = combine(domain.categories, domain.trash) { categories, trash ->
            SecondaryRows(categories, trash)
        }
        val planner = combine(
            domain.taskBlocks, domain.dayPlans, domain.focusSessions, domain.focusDailyTotals,
        ) { blocks, plans, sessions, totals ->
            PlannerRows(blocks, plans, sessions, totals)
        }
        emitAll(
            combine(primary, secondary, planner, store.data) { a, b, p, settings ->
                AppData(
                    events = domain.decodeEvents(a.events),
                    tasks = domain.decodeTasks(a.tasks),
                    notes = domain.decodeNotes(a.notes),
                    prefs = settings.prefs,
                    pin = settings.pin,
                    categories = domain.decodeCategories(b.categories),
                    trash = domain.decodeTrash(b.trash),
                    taskBlocks = domain.decodeTaskBlocks(p.taskBlocks),
                    dayPlans = domain.decodeDayPlans(p.dayPlans),
                    focusSessions = domain.decodeFocusSessions(p.focusSessions),
                    focusDailyTotals = domain.decodeFocusDailyTotals(p.focusDailyTotals),
                )
            }.distinctUntilChanged(),
        )
    }

    suspend fun update(transform: (AppData) -> AppData): AppData = writeMutex.withLock {
        val settings = store.data.first()
        domain.ensureLegacyImported(settings)
        val current = domain.snapshot(settings)
        val next = transform(current)
        domain.replaceChanged(current, next)
        if (settings.prefs != next.prefs || settings.pin != next.pin) {
            store.updateData { old -> old.copy(prefs = next.prefs, pin = next.pin) }
        }
        next
    }
}

/** Process-wide singletons shared by the UI and broadcast receivers. */
object AppGraph {
    @Volatile
    private var repo: AppRepository? = null

    fun repository(context: Context): AppRepository =
        repo ?: synchronized(this) {
            repo ?: AppRepository(context.applicationContext).also { repo = it }
        }
}
