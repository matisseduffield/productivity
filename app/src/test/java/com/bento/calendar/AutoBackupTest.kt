package com.bento.calendar

import com.bento.calendar.data.AutoBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AutoBackupTest {
    private val hour = 60 * 60 * 1000L

    @Test
    fun `backup is due once a day and right away when never run`() {
        val now = 1_800_000_000_000L
        assertTrue(AutoBackup.isDue(0, now))
        assertFalse(AutoBackup.isDue(now - 2 * hour, now))
        assertTrue(AutoBackup.isDue(now - 21 * hour, now))
        // A clock moved backwards must not stall backups forever.
        assertTrue(AutoBackup.isDue(now + 5 * hour, now))
    }

    @Test
    fun `only the newest dated backups are kept and other files are ignored`() {
        val names = (1..9).map { AutoBackup.fileName(LocalDate.of(2026, 10, it)) } +
            listOf("notes.txt", "bento-backup.json", "bento-backup-2026-10-01 (1).json")
        assertEquals(
            listOf("bento-backup-2026-10-01.json", "bento-backup-2026-10-02.json"),
            AutoBackup.toPrune(names),
        )
        assertEquals(emptyList<String>(), AutoBackup.toPrune(names.take(3)))
    }
}
