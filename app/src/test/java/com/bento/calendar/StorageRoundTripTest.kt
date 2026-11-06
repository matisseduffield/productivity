package com.bento.calendar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bento.calendar.data.AppData
import com.bento.calendar.data.BentoDatabase
import com.bento.calendar.data.DomainStore
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.NoteItem
import com.bento.calendar.data.Recur
import com.bento.calendar.data.TaskItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Row-level writes against a real Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageRoundTripTest {
    private lateinit var db: BentoDatabase
    private lateinit var store: DomainStore

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BentoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = DomainStore(db)
    }

    @After
    fun close() = db.close()

    private fun event(id: String, title: String = id) =
        EventItem(id = id, title = title, date = "2026-11-02", start = "09:00", end = "10:00")

    private suspend fun write(before: AppData, after: AppData): AppData {
        store.replaceChanged(before, after)
        return store.snapshot(AppData())
    }

    @Test
    fun `edits, removals and inserts land in list order`() = runBlocking {
        val start = AppData(events = listOf(event("a"), event("b"), event("c")))
        val saved = write(AppData(), start)
        assertEquals(start.events, saved.events)

        val edited = saved.copy(events = listOf(event("a"), event("b", "Renamed"), event("c")))
        assertEquals(edited.events, write(saved, edited).events)

        val removed = edited.copy(events = edited.events.filter { it.id != "a" })
        assertEquals(listOf("b", "c"), write(edited, removed).events.map { it.id })

        val inserted = removed.copy(events = listOf(event("z")) + removed.events)
        assertEquals(listOf("z", "b", "c"), write(removed, inserted).events.map { it.id })
    }

    @Test
    fun `new fields survive the database and untouched tables stay intact`() = runBlocking {
        val series = event("s").copy(recur = Recur.WEEKDAYS, recurUntil = "2026-12-24")
        val start = AppData(
            events = listOf(series),
            tasks = listOf(TaskItem(id = "t", title = "Bins", due = "2026-11-02", recur = Recur.FORTNIGHTLY)),
            notes = listOf(NoteItem(id = "n", title = "Note", updated = 1)),
        )
        val saved = write(AppData(), start)
        assertEquals(series, saved.events.single())
        assertEquals(Recur.FORTNIGHTLY, saved.tasks.single().recur)

        val typed = saved.copy(notes = saved.notes.map { it.copy(body = "typed") })
        val after = write(saved, typed)
        assertEquals("typed", after.notes.single().body)
        assertEquals(saved.events, after.events)
        assertEquals(saved.tasks, after.tasks)
    }

    @Test
    fun `deleting more rows than one statement allows still works`() = runBlocking {
        val many = AppData(events = (1..1200).map { event("e$it") })
        val saved = write(AppData(), many)
        assertEquals(1200, saved.events.size)
        assertEquals(emptyList<EventItem>(), write(saved, saved.copy(events = emptyList())).events)
    }
}
