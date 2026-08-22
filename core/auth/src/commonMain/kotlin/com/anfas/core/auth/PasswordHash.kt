package com.anfas.core.auth

/**
 * A stored password verifier: PBKDF2-HMAC-SHA256 over a per-account random salt.
 *
 * Never stores or transmits the password itself. [iterations] and [salt] are stored alongside the
 * hash so the cost can be raised later without invalidating existing accounts — an account
 * created at 210,000 iterations still verifies after the default moves, and can be re-hashed on
 * the next successful sign-in.
 *
 * PBKDF2 rather than bcrypt/argon2 for one reason: it is available from vetted platform
 * primitives on every target this app ships to (`javax.crypto` on Android and JVM, CommonCrypto
 * on Apple), so there is no new dependency and no hand-rolled cryptography. Argon2 would be the
 * better algorithm and is not worth a hand-written KMP implementation.
 */
data class PasswordHash(
    val algorithm: String,
    val iterations: Int,
    val salt: ByteArray,
    val hash: ByteArray,
) {
    // Generated: ByteArray uses identity equality, so the default data-class equals would report
    // two identical hashes as different. This type is compared in tests and in verification.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasswordHash) return false
        return algorithm == other.algorithm &&
            iterations == other.iterations &&
            salt.contentEquals(other.salt) &&
            hash.contentEquals(other.hash)
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + iterations
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + hash.contentHashCode()
        return result
    }

    companion object {
        const val PBKDF2_SHA256: String = "PBKDF2WithHmacSHA256"

        /**
         * OWASP's 2023 floor for PBKDF2-HMAC-SHA256. High enough to be costly to attack, low
         * enough that sign-in on a low-end reception tablet stays under a noticeable pause.
         */
        const val DEFAULT_ITERATIONS: Int = 210_000

        const val SALT_BYTES: Int = 16
        const val HASH_BITS: Int = 256
    }
}

/**
 * Derives and verifies password hashes. An interface, not a top-level function, so a test can
 * substitute a cheap implementation — 210,000 iterations per assertion would make the auth test
 * suite unusably slow, and that slowness is exactly what tempts someone to lower the real cost.
 */
interface PasswordHasher {
    fun hash(password: String): PasswordHash

    /**
     * Must compare in constant time with respect to the stored hash. A verifier that returns
     * early on the first differing byte leaks the hash one byte at a time.
     */
    fun verify(password: String, against: PasswordHash): Boolean
}

/**
 * The real hasher. [derive] is the only platform-specific part; salt generation and the
 * constant-time comparison are shared so they cannot diverge between platforms.
 */
class Pbkdf2PasswordHasher(private val iterations: Int = PasswordHash.DEFAULT_ITERATIONS) :
    PasswordHasher {

    override fun hash(password: String): PasswordHash {
        val salt = secureRandomBytes(PasswordHash.SALT_BYTES)
        return PasswordHash(
            algorithm = PasswordHash.PBKDF2_SHA256,
            iterations = iterations,
            salt = salt,
            hash = derivePbkdf2Sha256(password, salt, iterations, PasswordHash.HASH_BITS),
        )
    }

    override fun verify(password: String, against: PasswordHash): Boolean {
        if (against.algorithm != PasswordHash.PBKDF2_SHA256) return false
        val candidate = derivePbkdf2Sha256(
            password = password,
            salt = against.salt,
            iterations = against.iterations,
            bits = against.hash.size * BITS_PER_BYTE,
        )
        return candidate constantTimeEquals against.hash
    }

    private companion object {
        const val BITS_PER_BYTE = 8
    }
}

/**
 * Compares every byte regardless of where the first difference is. `contentEquals` short-circuits,
 * which turns a hash comparison into a timing oracle.
 */
internal infix fun ByteArray.constantTimeEquals(other: ByteArray): Boolean {
    // Length is not secret — hash length is a fixed property of the algorithm — so returning
    // early on a mismatch here leaks nothing.
    if (size != other.size) return false
    var difference = 0
    for (i in indices) {
        difference = difference or (this[i].toInt() xor other[i].toInt())
    }
    return difference == 0
}

internal expect fun derivePbkdf2Sha256(
    password: String,
    salt: ByteArray,
    iterations: Int,
    bits: Int,
): ByteArray

/** Cryptographically secure, not `Random`. A predictable salt defeats the point of salting. */
internal expect fun secureRandomBytes(count: Int): ByteArray
