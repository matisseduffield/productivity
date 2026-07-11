package com.bento.calendar.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

private const val LEGACY_IMPORT_KEY = "legacy-app-data-v1"

@Entity(
    tableName = "events",
    indices = [Index("date"), Index("endDate")],
)
data class EventRecord(
    @PrimaryKey val id: String,
    val position: Int,
    val date: String,
    val endDate: String?,
    val payload: String,
)

@Entity(
    tableName = "tasks",
    indices = [Index("due"), Index("done"), Index("priority")],
)
data class TaskRecord(
    @PrimaryKey val id: String,
    val position: Int,
    val due: String?,
    val done: Boolean,
    val priority: Int,
    val payload: String,
)

@Entity(
    tableName = "notes",
    indices = [Index("updated"), Index("pinned"), Index("locked")],
)
data class NoteRecord(
    @PrimaryKey val id: String,
    val position: Int,
    val updated: Long,
    val pinned: Boolean,
    val locked: Boolean,
    val payload: String,
)

@Entity(tableName = "categories")
data class CategoryRecord(
    @PrimaryKey val id: String,
    val position: Int,
    val payload: String,
)

@Entity(
    tableName = "trash",
    indices = [Index("deletedAt")],
)
data class TrashRecord(
    @PrimaryKey val id: String,
    val position: Int,
    val deletedAt: Long,
    val payload: String,
)

@Entity(tableName = "domain_meta")
data class DomainMetaRecord(@PrimaryKey val key: String, val value: String)

@Entity(
    tableName = "task_blocks",
    indices = [Index("taskId"), Index("date"), Index("state"), Index("occurrenceKey")],
)
data class TaskBlockRecord(
    @PrimaryKey val id: String,
    val taskId: String,
    val occurrenceKey: String?,
    val date: String,
    val startMin: Int,
    val durationMin: Int,
    val state: String,
    val createdAt: Long,
    val payload: String,
)

@Entity(tableName = "day_plans", indices = [Index("plannedAt")])
data class DayPlanRecord(
    @PrimaryKey val date: String,
    val plannedAt: Long,
    val reviewedAt: Long?,
    val payload: String,
)

@Entity(
    tableName = "focus_sessions",
    indices = [Index("taskId"), Index("blockId"), Index("startedAt"), Index("outcome")],
)
data class FocusSessionRecord(
    @PrimaryKey val id: String,
    val taskId: String?,
    val blockId: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val outcome: String,
    val payload: String,
)

@Entity(tableName = "focus_daily_totals", primaryKeys = ["date", "categoryId"])
data class FocusDailyTotalRecord(
    val date: String,
    val categoryId: String,
    val activeSeconds: Long,
    val payload: String,
)

@Dao
interface DomainDao {
    @Query("SELECT * FROM events ORDER BY position")
    fun observeEvents(): Flow<List<EventRecord>>

    @Query("SELECT * FROM tasks ORDER BY position")
    fun observeTasks(): Flow<List<TaskRecord>>

    @Query("SELECT * FROM notes ORDER BY position")
    fun observeNotes(): Flow<List<NoteRecord>>

    @Query("SELECT * FROM categories ORDER BY position")
    fun observeCategories(): Flow<List<CategoryRecord>>

    @Query("SELECT * FROM trash ORDER BY position")
    fun observeTrash(): Flow<List<TrashRecord>>

    @Query("SELECT * FROM task_blocks ORDER BY date, startMin, createdAt")
    fun observeTaskBlocks(): Flow<List<TaskBlockRecord>>

    @Query("SELECT * FROM day_plans ORDER BY date")
    fun observeDayPlans(): Flow<List<DayPlanRecord>>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt")
    fun observeFocusSessions(): Flow<List<FocusSessionRecord>>

    @Query("SELECT * FROM focus_daily_totals ORDER BY date, categoryId")
    fun observeFocusDailyTotals(): Flow<List<FocusDailyTotalRecord>>

    @Query("SELECT * FROM events ORDER BY position")
    suspend fun events(): List<EventRecord>

    @Query("SELECT * FROM tasks ORDER BY position")
    suspend fun tasks(): List<TaskRecord>

    @Query("SELECT * FROM notes ORDER BY position")
    suspend fun notes(): List<NoteRecord>

    @Query("SELECT * FROM categories ORDER BY position")
    suspend fun categories(): List<CategoryRecord>

    @Query("SELECT * FROM trash ORDER BY position")
    suspend fun trash(): List<TrashRecord>

    @Query("SELECT * FROM task_blocks ORDER BY date, startMin, createdAt")
    suspend fun taskBlocks(): List<TaskBlockRecord>

    @Query("SELECT * FROM day_plans ORDER BY date")
    suspend fun dayPlans(): List<DayPlanRecord>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt")
    suspend fun focusSessions(): List<FocusSessionRecord>

    @Query("SELECT * FROM focus_daily_totals ORDER BY date, categoryId")
    suspend fun focusDailyTotals(): List<FocusDailyTotalRecord>

    @Query("SELECT value FROM domain_meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(rows: List<EventRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(rows: List<TaskRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(rows: List<NoteRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(rows: List<CategoryRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrash(rows: List<TrashRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskBlocks(rows: List<TaskBlockRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDayPlans(rows: List<DayPlanRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusSessions(rows: List<FocusSessionRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusDailyTotals(rows: List<FocusDailyTotalRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(row: DomainMetaRecord)

    @Query("DELETE FROM events")
    suspend fun clearEvents()

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM trash")
    suspend fun clearTrash()

    @Query("DELETE FROM task_blocks")
    suspend fun clearTaskBlocks()

    @Query("DELETE FROM day_plans")
    suspend fun clearDayPlans()

    @Query("DELETE FROM focus_sessions")
    suspend fun clearFocusSessions()

    @Query("DELETE FROM focus_daily_totals")
    suspend fun clearFocusDailyTotals()
}

@Database(
    entities = [
        EventRecord::class,
        TaskRecord::class,
        NoteRecord::class,
        CategoryRecord::class,
        TrashRecord::class,
        DomainMetaRecord::class,
        TaskBlockRecord::class,
        DayPlanRecord::class,
        FocusSessionRecord::class,
        FocusDailyTotalRecord::class,
    ],
    version = 1,
    // Schema evolution is covered by explicit migration tests. Room 2.8's
    // schema exporter is incompatible with this project's serialization
    // compiler/runtime combination on repeated KSP builds.
    exportSchema = false,
)
abstract class BentoDatabase : RoomDatabase() {
    abstract fun domainDao(): DomainDao

    companion object {
        @Volatile private var instance: BentoDatabase? = null

        fun get(context: Context): BentoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                BentoDatabase::class.java,
                "bento.calendar.v3.db",
            ).build().also { instance = it }
        }
    }
}

/**
 * Room-backed domain storage. The payload columns keep the established
 * serialization contract while indexed columns make date/range queries cheap.
 * This lets UI call sites migrate incrementally without a flag-day rewrite.
 */
class DomainStore(private val db: BentoDatabase) {
    private val dao = db.domainDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val events = dao.observeEvents()
    val tasks = dao.observeTasks()
    val notes = dao.observeNotes()
    val categories = dao.observeCategories()
    val trash = dao.observeTrash()
    val taskBlocks = dao.observeTaskBlocks()
    val dayPlans = dao.observeDayPlans()
    val focusSessions = dao.observeFocusSessions()
    val focusDailyTotals = dao.observeFocusDailyTotals()

    fun decodeEvents(rows: List<EventRecord>): List<EventItem> =
        rows.map { json.decodeFromString(EventItem.serializer(), it.payload) }

    fun decodeTasks(rows: List<TaskRecord>): List<TaskItem> =
        rows.map { json.decodeFromString(TaskItem.serializer(), it.payload) }

    fun decodeNotes(rows: List<NoteRecord>): List<NoteItem> =
        rows.map { json.decodeFromString(NoteItem.serializer(), it.payload) }

    fun decodeCategories(rows: List<CategoryRecord>): List<Category> =
        rows.map { json.decodeFromString(Category.serializer(), it.payload) }

    fun decodeTrash(rows: List<TrashRecord>): List<TrashEntry> =
        rows.map { json.decodeFromString(TrashEntry.serializer(), it.payload) }

    fun decodeTaskBlocks(rows: List<TaskBlockRecord>): List<TaskBlock> =
        rows.map { json.decodeFromString(TaskBlock.serializer(), it.payload) }

    fun decodeDayPlans(rows: List<DayPlanRecord>): List<DayPlan> =
        rows.map { json.decodeFromString(DayPlan.serializer(), it.payload) }

    fun decodeFocusSessions(rows: List<FocusSessionRecord>): List<FocusSession> =
        rows.map { json.decodeFromString(FocusSession.serializer(), it.payload) }

    fun decodeFocusDailyTotals(rows: List<FocusDailyTotalRecord>): List<FocusDailyTotal> =
        rows.map { json.decodeFromString(FocusDailyTotal.serializer(), it.payload) }

    suspend fun ensureLegacyImported(legacy: AppData) {
        if (dao.meta(LEGACY_IMPORT_KEY) != null) return
        db.withTransaction {
            if (dao.meta(LEGACY_IMPORT_KEY) != null) return@withTransaction
            replaceLocked(legacy)
            dao.putMeta(DomainMetaRecord(LEGACY_IMPORT_KEY, "1"))
        }
    }

    suspend fun snapshot(settings: AppData): AppData = AppData(
        events = decodeEvents(dao.events()),
        tasks = decodeTasks(dao.tasks()),
        notes = decodeNotes(dao.notes()),
        prefs = settings.prefs,
        pin = settings.pin,
        categories = decodeCategories(dao.categories()),
        trash = decodeTrash(dao.trash()),
        taskBlocks = decodeTaskBlocks(dao.taskBlocks()),
        dayPlans = decodeDayPlans(dao.dayPlans()),
        focusSessions = decodeFocusSessions(dao.focusSessions()),
        focusDailyTotals = decodeFocusDailyTotals(dao.focusDailyTotals()),
    )

    suspend fun replace(data: AppData) = db.withTransaction { replaceLocked(data) }

    /** Compatibility transforms rewrite only collections that actually changed. */
    suspend fun replaceChanged(before: AppData, after: AppData) = db.withTransaction {
        if (before.events != after.events) replaceEvents(after.events)
        if (before.tasks != after.tasks) replaceTasks(after.tasks)
        if (before.notes != after.notes) replaceNotes(after.notes)
        if (before.categories != after.categories) replaceCategories(after.categories)
        if (before.trash != after.trash) replaceTrash(after.trash)
        if (before.taskBlocks != after.taskBlocks) replaceTaskBlocks(after.taskBlocks)
        if (before.dayPlans != after.dayPlans) replaceDayPlans(after.dayPlans)
        if (before.focusSessions != after.focusSessions) replaceFocusSessions(after.focusSessions)
        if (before.focusDailyTotals != after.focusDailyTotals) replaceFocusDailyTotals(after.focusDailyTotals)
    }

    private suspend fun replaceLocked(data: AppData) {
        replaceEvents(data.events)
        replaceTasks(data.tasks)
        replaceNotes(data.notes)
        replaceCategories(data.categories)
        replaceTrash(data.trash)
        replaceTaskBlocks(data.taskBlocks)
        replaceDayPlans(data.dayPlans)
        replaceFocusSessions(data.focusSessions)
        replaceFocusDailyTotals(data.focusDailyTotals)
    }

    private suspend fun replaceEvents(items: List<EventItem>) {
        dao.clearEvents()
        dao.insertEvents(items.mapIndexed { index, item ->
            EventRecord(item.id, index, item.date, item.endDate, json.encodeToString(EventItem.serializer(), item))
        })
    }

    private suspend fun replaceTasks(items: List<TaskItem>) {
        dao.clearTasks()
        dao.insertTasks(items.mapIndexed { index, item ->
            TaskRecord(item.id, index, item.due, item.done, item.priority, json.encodeToString(TaskItem.serializer(), item))
        })
    }

    private suspend fun replaceNotes(items: List<NoteItem>) {
        dao.clearNotes()
        dao.insertNotes(items.mapIndexed { index, item ->
            NoteRecord(item.id, index, item.updated, item.pinned, item.locked, json.encodeToString(NoteItem.serializer(), item))
        })
    }

    private suspend fun replaceCategories(items: List<Category>) {
        dao.clearCategories()
        dao.insertCategories(items.mapIndexed { index, item ->
            CategoryRecord(item.id, index, json.encodeToString(Category.serializer(), item))
        })
    }

    private suspend fun replaceTrash(items: List<TrashEntry>) {
        dao.clearTrash()
        dao.insertTrash(items.mapIndexed { index, item ->
            val itemId = item.event?.id ?: item.task?.id ?: item.note?.id ?: index.toString()
            TrashRecord("${item.deletedAt}:$itemId", index, item.deletedAt, json.encodeToString(TrashEntry.serializer(), item))
        })
    }

    private suspend fun replaceTaskBlocks(items: List<TaskBlock>) {
        dao.clearTaskBlocks()
        dao.insertTaskBlocks(items.map { item ->
            TaskBlockRecord(
                item.id, item.taskId, item.occurrenceKey, item.date, item.startMin,
                item.durationMin, item.state, item.createdAt,
                json.encodeToString(TaskBlock.serializer(), item),
            )
        })
    }

    private suspend fun replaceDayPlans(items: List<DayPlan>) {
        dao.clearDayPlans()
        dao.insertDayPlans(items.map { item ->
            DayPlanRecord(item.date, item.plannedAt, item.reviewedAt, json.encodeToString(DayPlan.serializer(), item))
        })
    }

    private suspend fun replaceFocusSessions(items: List<FocusSession>) {
        dao.clearFocusSessions()
        dao.insertFocusSessions(items.map { item ->
            FocusSessionRecord(
                item.id, item.taskId, item.blockId, item.startedAt, item.endedAt,
                item.outcome, json.encodeToString(FocusSession.serializer(), item),
            )
        })
    }

    private suspend fun replaceFocusDailyTotals(items: List<FocusDailyTotal>) {
        dao.clearFocusDailyTotals()
        dao.insertFocusDailyTotals(items.map { item ->
            FocusDailyTotalRecord(
                item.date, item.categoryId, item.activeSeconds,
                json.encodeToString(FocusDailyTotal.serializer(), item),
            )
        })
    }
}
