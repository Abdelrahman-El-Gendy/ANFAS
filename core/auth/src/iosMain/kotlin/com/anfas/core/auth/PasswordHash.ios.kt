@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.anfas.core.auth

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

/**
 * CommonCrypto's PBKDF2, which ships with Kotlin/Native's Apple platform libraries — so this
 * needs no cinterop, no CocoaPods and no third-party crypto dependency, matching how the rest of
 * this project stays off custom native interop.
 */
internal actual fun derivePbkdf2Sha256(
    password: String,
    salt: ByteArray,
    iterations: Int,
    bits: Int,
): ByteArray {
    val out = ByteArray(bits / BITS_PER_BYTE)
    val passwordBytes = password.encodeToByteArray()

    // Salt and output are pinned because CommonCrypto reads and writes them through raw
    // pointers. The password is not: cinterop maps that parameter to String?, so Kotlin/Native
    // hands over a temporary C string itself. passwordLen is still the UTF-8 *byte* count, not
    // the character count -- an Arabic or accented password would derive the wrong key otherwise.
    salt.usePinned { pinnedSalt ->
        out.usePinned { pinnedOut ->
            val status = CCKeyDerivationPBKDF(
                algorithm = kCCPBKDF2,
                password = password,
                passwordLen = passwordBytes.size.convert(),
                salt = pinnedSalt.addressOf(0).reinterpret(),
                saltLen = salt.size.convert(),
                prf = kCCPRFHmacAlgSHA256,
                rounds = iterations.convert(),
                derivedKey = pinnedOut.addressOf(0).reinterpret(),
                derivedKeyLen = out.size.convert(),
            )
            // Fail loudly. A silently-zeroed derived key would make every password verify
            // against every other, which is the worst possible way for this to break.
            check(status == 0) { "PBKDF2 failed with CommonCrypto status $status" }
        }
    }
    return out
}

internal actual fun secureRandomBytes(count: Int): ByteArray {
    val bytes = ByteArray(count)
    bytes.usePinned { pinned ->
        val status = SecRandomCopyBytes(
            kSecRandomDefault,
            count.convert(),
            pinned.addressOf(0),
        )
        check(status == 0) { "SecRandomCopyBytes failed with status $status" }
    }
    return bytes
}

private const val BITS_PER_BYTE = 8
