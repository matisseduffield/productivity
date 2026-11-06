package com.bento.calendar

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.bento.calendar.data.AppGraph
import com.bento.calendar.data.BentoDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric swaps its SQLite between tests but these stay process-wide, so
 * each test starts from a closed database and no repository.
 */
internal fun resetAppSingletons() {
    AppGraph::class.java.getDeclaredField("repo").apply { isAccessible = true }.set(AppGraph, null)
    val field = BentoDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }
    (field.get(null) as BentoDatabase?)?.close()
    field.set(null, null)
}

/** Launches the real activity and opens every top-level screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppScreensTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `every tab and Settings render on the real activity`() {
        resetAppSingletons()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasContentDescription("Settings")).fetchSemanticsNodes().isNotEmpty()
            }
            listOf("Calendar", "Notes", "Tasks", "Today").forEach { tab ->
                compose.onNodeWithContentDescription(tab, useUnmergedTree = true).performClick()
                compose.waitForIdle()
            }
            // Both editors, including the repeat fields added in 3.5.
            compose.onNodeWithContentDescription("Tasks", useUnmergedTree = true).performClick()
            compose.onNodeWithContentDescription("New task").performClick()
            compose.onNodeWithText("Save task").assertExists()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithContentDescription("Calendar", useUnmergedTree = true).performClick()
            compose.onNodeWithContentDescription("New event").performClick()
            compose.onNodeWithText("Save event").assertExists()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithContentDescription("Today", useUnmergedTree = true).performClick()
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodes(hasText("Morning summary")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Daily backup").assertExists()
        }
        resetAppSingletons()
    }
}
