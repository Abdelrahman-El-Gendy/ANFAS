package com.anfas.core.auth

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * javax.crypto's PBKDF2. Identical to the JVM/Android sibling — the two source sets have no
 * shared parent in this module, and adding one for six lines is not worth the build-file
 * complexity. If a third JVM-family target appears, introduce a shared source set then.
 */
internal actual fun derivePbkdf2Sha256(
    password: String,
    salt: ByteArray,
    iterations: Int,
    bits: Int,
): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, bits)
    return try {
        SecretKeyFactory.getInstance(PasswordHash.PBKDF2_SHA256).generateSecret(spec).encoded
    } finally {
        // Zeroes the copy PBEKeySpec made of the password, so it does not sit in the heap until
        // the next GC where a memory dump could find it.
        spec.clearPassword()
    }
}

internal actual fun secureRandomBytes(count: Int): ByteArray =
    ByteArray(count).also(SecureRandom()::nextBytes)
