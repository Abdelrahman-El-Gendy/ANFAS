package com.anfas.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Runs on every target, which is the point: Android and JVM use `javax.crypto` while Apple uses
 * CommonCrypto, and the two must derive the *same bytes* or an account created on the reception
 * iPad cannot sign in on the Android phone.
 *
 * Deliberately low iteration counts. The real cost is 210,000 rounds; paying that per assertion
 * would make this suite slow enough that someone would be tempted to lower the production value.
 */
class PasswordHashTest {

    private val hasher = Pbkdf2PasswordHasher(iterations = TEST_ITERATIONS)

    @Test
    fun `the right password verifies and a wrong one does not`() {
        val stored = hasher.hash("correct horse battery staple")

        assertTrue(hasher.verify("correct horse battery staple", stored))
        assertFalse(hasher.verify("Correct horse battery staple", stored))
        assertFalse(hasher.verify("", stored))
    }

    /** A shared salt would let one cracked hash reveal every account using that password. */
    @Test
    fun `the same password hashes differently every time`() {
        val a = hasher.hash("s3cret")
        val b = hasher.hash("s3cret")

        assertNotEquals(a.salt.toList(), b.salt.toList())
        assertNotEquals(a.hash.toList(), b.hash.toList())
        // Both still verify: the salt travels with the hash.
        assertTrue(hasher.verify("s3cret", a))
        assertTrue(hasher.verify("s3cret", b))
    }

    /**
     * The interoperability test. This is RFC 6070's PBKDF2 shape adapted to HMAC-SHA256 with a
     * fixed salt and one iteration, so both platform implementations must produce this exact
     * output. A divergence here means accounts are device-locked, which would only surface when
     * a gym tries to use a second device.
     */
    @Test
    fun `derivation matches a known vector on every platform`() {
        val derived = derivePbkdf2Sha256(
            password = "password",
            salt = byteArrayOf(115, 97, 108, 116), // "salt"
            iterations = 1,
            bits = 256,
        )

        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            derived.toHex(),
        )
    }

    /**
     * Raising the cost must not lock existing accounts out: the stored iteration count is what
     * verification uses, not the current default.
     */
    @Test
    fun `an account hashed at a lower cost still verifies after the default rises`() {
        val old = Pbkdf2PasswordHasher(iterations = TEST_ITERATIONS).hash("unchanged")
        val stricter = Pbkdf2PasswordHasher(iterations = TEST_ITERATIONS * 4)

        assertTrue(stricter.verify("unchanged", old))
        assertEquals(TEST_ITERATIONS, old.iterations)
    }

    @Test
    fun `an unknown algorithm is refused rather than assumed`() {
        val stored = hasher.hash("pw").copy(algorithm = "MD5")

        assertFalse(hasher.verify("pw", stored))
    }

    @Test
    fun `constant time comparison still reports equality correctly`() {
        assertTrue(byteArrayOf(1, 2, 3) constantTimeEquals byteArrayOf(1, 2, 3))
        assertFalse(byteArrayOf(1, 2, 3) constantTimeEquals byteArrayOf(1, 2, 4))
        assertFalse(byteArrayOf(1, 2, 3) constantTimeEquals byteArrayOf(1, 2))
    }

    /** Non-ASCII passwords must hash by UTF-8 bytes, not by character count. */
    @Test
    fun `an arabic password round trips`() {
        val stored = hasher.hash("كلمةالسر")

        assertTrue(hasher.verify("كلمةالسر", stored))
        assertFalse(hasher.verify("كلمةالسري", stored))
    }

    private companion object {
        const val TEST_ITERATIONS = 1_000
    }
}

private fun ByteArray.toHex(): String = joinToString("") {
    val v = it.toInt() and 0xFF
    v.toString(16).padStart(2, '0')
}
