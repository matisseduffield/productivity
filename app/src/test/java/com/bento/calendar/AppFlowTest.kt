package com.bento.calendar

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.bento.calendar.data.Recur
import com.bento.calendar.data.occursOn
import com.bento.calendar.data.toIso
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.EditScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * The real view model on the real repository and Room database. The DataStore
 * file outlives a test, so nothing here assumes an empty store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppFlowTest {
    private val viewModels = ViewModelStore()

    @Before
    fun fresh() = resetAppSingletons()

    @After
    fun tearDown() {
        viewModels.clear()
        resetAppSingletons()
        // No composition ran to flush the view model's state writes; left
        // pending, a later Compose test could never reach idle.
        Snapshot.sendApplyNotifications()
    }

    private fun viewModel(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ViewModelProvider(viewModels, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[AppViewModel::class.java]
        settle { vm.data.value != null }
        return vm
    }

    /** Runs the main looper until [done]; Room works on its own threads. */
    private fun settle(done: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + 15_000
        while (true) {
            looper.idle()
            if (done()) return
            check(System.currentTimeMillis() < deadline) { "condition never became true" }
            Thread.sleep(5)
        }
    }

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    private fun AppViewModel.type(pin: String) = pin.forEach { pinPress(it.toString()) }

    @Test
    fun `PIN is confirmed, stored hashed and checked`() {
        val vm = viewModel()
        vm.startSetPin()
        vm.type("4821")
        assertEquals("4821", vm.pinCtx?.firstEntry)
        vm.type("1111")
        assertNull("a mismatch starts again", vm.pinCtx?.firstEntry)

        vm.type("4821")
        vm.type("4821")
        settle { vm.pinCtx == null && vm.data.value?.pin?.startsWith("pbkdf2-sha256$") == true }
        assertFalse(vm.data.value!!.pin!!.contains("4821"))

        vm.startChangePin()
        vm.type("0000")
        settle { vm.pinErr }
        assertNotNull("still asking", vm.pinCtx)
        vm.type("4821")
        settle { vm.pinCtx?.mode == com.bento.calendar.ui.PinMode.Set }
        vm.pinCancel()
    }

    @Test
    fun `note typing is buffered, then saved, and a blank note is discarded`() {
        val vm = viewModel()
        vm.newNote()
        settle { vm.openNoteId != null }
        val id = vm.openNoteId!!
        vm.setNoteTitle("Shopping")
        vm.setNoteBody("eggs")
        settle { true }
        assertEquals("", vm.data.value!!.notes.first { it.id == id }.body)
        advance(500)
        settle { vm.data.value!!.notes.first { it.id == id }.body == "eggs" }
        assertEquals("Shopping", vm.data.value!!.notes.first { it.id == id }.title)

        // Closing right after typing must not lose the last characters.
        vm.setNoteBody("eggs and milk")
        vm.closeNote()
        settle { vm.data.value!!.notes.first { it.id == id }.body == "eggs and milk" }

        vm.newNote()
        settle { vm.openNoteId != null }
        val blank = vm.openNoteId!!
        vm.closeNote()
        settle { vm.data.value!!.notes.none { it.id == blank } }
    }

    @Test
    fun `completing a repeating task can be undone`() {
        val vm = viewModel()
        val due = LocalDate.now()
        vm.newTask()
        vm.updateTaskDraft { it.copy(title = "Undo me", due = due, recur = Recur.WEEKLY) }
        vm.saveTask()
        settle { vm.data.value!!.tasks.any { it.title == "Undo me" } }
        val id = vm.data.value!!.tasks.first { it.title == "Undo me" }.id

        vm.toggleTask(id)
        settle { vm.data.value!!.tasks.first { it.id == id }.due == due.plusWeeks(1).toIso() }
        assertEquals("Task completed", vm.undoState?.label)

        vm.performUndo()
        settle { vm.data.value!!.tasks.first { it.id == id }.due == due.toIso() }
        assertNull(vm.undoState)
    }

    @Test
    fun `this and later splits a series without touching earlier dates`() {
        val vm = viewModel()
        val start = LocalDate.now().plusDays(1)
        vm.newEvent()
        vm.updateEventDraft { it.copy(title = "Split me", date = start, recur = Recur.DAILY) }
        vm.saveEvent()
        settle { vm.data.value!!.events.any { it.title == "Split me" } }
        val series = vm.data.value!!.events.first { it.title == "Split me" }
        val cut = start.plusDays(3)

        vm.openEvent(series.copy(date = cut.toIso()))
        vm.updateEventDraft { it.copy(title = "Moved", date = cut, scope = EditScope.Following) }
        vm.saveEvent()
        settle { vm.data.value!!.events.any { it.title == "Moved" } }

        val head = vm.data.value!!.events.first { it.id == series.id }
        val tail = vm.data.value!!.events.first { it.title == "Moved" }
        assertEquals(cut.minusDays(1).toIso(), head.recurUntil)
        assertTrue(head.occursOn(cut.minusDays(1)))
        assertFalse(head.occursOn(cut))
        assertTrue(tail.occursOn(cut))
        assertEquals(Recur.DAILY, tail.recur)

        // Deleting this & later from the tail's third day keeps its first two.
        vm.openEvent(tail.copy(date = cut.plusDays(2).toIso()))
        vm.updateEventDraft { it.copy(scope = EditScope.Following) }
        vm.deleteEvent()
        vm.deleteEvent()
        settle { vm.data.value!!.events.first { it.id == tail.id }.recurUntil == cut.plusDays(1).toIso() }
    }
}
