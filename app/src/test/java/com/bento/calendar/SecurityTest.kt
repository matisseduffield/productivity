package com.bento.calendar

import com.bento.calendar.security.PinGuard
import com.bento.calendar.security.PinHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityTest {
    @Test
    fun `hashed PIN verifies and never contains the digits`() {
        val stored = PinHash.hash("4821", iterations = 1_000)
        assertFalse(stored.contains("4821"))
        assertFalse(PinHash.isLegacy(stored))
        assertTrue(PinHash.verify("4821", stored))
        assertFalse(PinHash.verify("4822", stored))
    }

    @Test
    fun `each hash uses a fresh salt`() {
        assertNotEquals(PinHash.hash("1111", iterations = 1_000), PinHash.hash("1111", iterations = 1_000))
    }

    @Test
    fun `legacy plaintext PIN still unlocks and is flagged for upgrade`() {
        assertTrue(PinHash.isLegacy("0042"))
        assertTrue(PinHash.verify("0042", "0042"))
        assertFalse(PinHash.verify("0043", "0042"))
    }

    @Test
    fun `missing or malformed stored PIN never verifies`() {
        assertFalse(PinHash.verify("1234", null))
        assertFalse(PinHash.verify("1234", "pbkdf2-sha256\$x\$abc\$def"))
        assertFalse(PinHash.verify("1234", "pbkdf2-sha256\$1000\$!!\$!!"))
    }

    @Test
    fun `wrong PIN lockout starts after the free attempts and is capped`() {
        assertEquals(0L, PinGuard.lockoutMs(PinGuard.FREE_ATTEMPTS - 1))
        assertEquals(30_000L, PinGuard.lockoutMs(PinGuard.FREE_ATTEMPTS))
        assertEquals(60_000L, PinGuard.lockoutMs(PinGuard.FREE_ATTEMPTS + 1))
        assertEquals(15 * 60_000L, PinGuard.lockoutMs(PinGuard.FREE_ATTEMPTS + 40))
    }
}
