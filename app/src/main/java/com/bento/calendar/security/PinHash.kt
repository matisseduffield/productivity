package com.bento.calendar.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The note PIN is stored as a salted PBKDF2 hash, never the digits. The hash
 * deliberately lives in the same DataStore as before, so Android's Google
 * backup still carries the PIN to a new phone.
 *
 * Stored form: `pbkdf2-sha256$<iterations>$<salt b64>$<hash b64>`. Stores
 * from before 3.5 hold the plain 4 digits; [verify] still accepts those and
 * [isLegacy] tells the caller to re-save them hashed.
 *
 * A 4-digit PIN can't resist a determined offline attack whatever the hash;
 * this keeps the digits out of plain sight in backups and on-disk files.
 */
object PinHash {
    private const val PREFIX = "pbkdf2-sha256"
    private const val ITERATIONS = 60_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun hash(pin: String, iterations: Int = ITERATIONS): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val enc = Base64.getEncoder()
        return "$PREFIX$$iterations$${enc.encodeToString(salt)}$${enc.encodeToString(derive(pin, salt, iterations))}"
    }

    fun verify(pin: String, stored: String?): Boolean {
        if (stored == null) return false
        if (isLegacy(stored)) return MessageDigest.isEqual(pin.toByteArray(), stored.toByteArray())
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        return try {
            val dec = Base64.getDecoder()
            val salt = dec.decode(parts[2])
            val expected = dec.decode(parts[3])
            MessageDigest.isEqual(derive(pin, salt, iterations), expected)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** True for a pre-3.5 plaintext PIN that should be re-saved hashed. */
    fun isLegacy(stored: String): Boolean = !stored.startsWith("$PREFIX$")

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/**
 * Wrong-PIN back-off: the first few misses are free (typos), then each
 * further miss doubles the wait, capped at 15 minutes.
 */
object PinGuard {
    const val FREE_ATTEMPTS = 5
    private const val BASE_MS = 30_000L
    private const val MAX_MS = 15 * 60_000L

    fun lockoutMs(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0
        val steps = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return (BASE_MS shl steps).coerceAtMost(MAX_MS)
    }
}
