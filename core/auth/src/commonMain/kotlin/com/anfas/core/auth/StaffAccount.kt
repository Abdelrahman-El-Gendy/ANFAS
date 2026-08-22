package com.anfas.core.auth

import kotlin.time.Instant

/**
 * A staff login. Local to the device: there is no server, so accounts are created on the device
 * by whoever already holds the Owner role.
 *
 * [username] is stored already-normalised by [normaliseUsername]. Storing the raw text and
 * normalising on read would let two accounts exist that a human cannot tell apart.
 */
data class StaffAccount(
    val id: String,
    val username: String,
    val displayName: String,
    val roles: Set<Role>,
    val passwordHash: PasswordHash,
    val createdAt: Instant,
    /** False for an account the Owner has switched off without deleting its history. */
    val isEnabled: Boolean = true,
)

/**
 * Case- and whitespace-insensitive, because staff type their own name at a reception desk and
 * "Fahd " must reach the same account as "fahd".
 *
 * Deliberately not `lowercase()` alone: Turkish and Azeri lowercase a dotted capital I to a
 * dotless one, so a locale-sensitive lowercase would map "FAHD" to a different string on a
 * Turkish device than on an Egyptian one — and the account would become unreachable. Kotlin's
 * `lowercase()` is locale-independent, which is what makes it safe here; that is the reason it is
 * used rather than any platform locale-aware variant.
 */
fun normaliseUsername(raw: String): String = raw.trim().lowercase()

/**
 * What a sign-in attempt can produce. Typed rather than exceptions, and deliberately coarse: see
 * [SignInResult.InvalidCredentials].
 */
sealed interface SignInResult {
    data class Success(val session: Session) : SignInResult

    /**
     * One case for "no such user" *and* "wrong password", on purpose. Distinguishing them tells
     * an attacker which usernames exist, and staff gain nothing from the distinction.
     */
    data object InvalidCredentials : SignInResult

    /** The account exists and the password was right, but the Owner has disabled it. */
    data object AccountDisabled : SignInResult

    /** No accounts exist at all, so the app must offer setup rather than a login it cannot pass. */
    data object NoAccounts : SignInResult
}

/** Why a proposed username or password cannot be used. Checked before any account is created. */
sealed interface CredentialProblem {
    data object UsernameTooShort : CredentialProblem
    data object UsernameTaken : CredentialProblem
    data object PasswordTooShort : CredentialProblem
    data object DisplayNameBlank : CredentialProblem
}

/**
 * Validation for account creation. Pure and separate from storage so the rules are testable and
 * stated once.
 */
object CredentialRules {
    /** Short enough to type at a busy desk, long enough not to collide by accident. */
    const val MIN_USERNAME_LENGTH: Int = 3

    /**
     * Eight, matching NIST SP 800-63B's minimum for a memorised secret. No composition rules
     * (no "must contain a symbol"): NIST advises against them because they push people toward
     * predictable substitutions and sticky notes, and this password protects a gym's member list
     * on a device that is already physically controlled.
     */
    const val MIN_PASSWORD_LENGTH: Int = 8

    fun validate(
        username: String,
        password: String,
        displayName: String,
        existingUsernames: Set<String>,
    ): Set<CredentialProblem> {
        val problems = mutableSetOf<CredentialProblem>()
        val normalised = normaliseUsername(username)

        if (normalised.length < MIN_USERNAME_LENGTH) problems += CredentialProblem.UsernameTooShort
        if (normalised in existingUsernames.map(::normaliseUsername)) {
            problems += CredentialProblem.UsernameTaken
        }
        // Length is counted in code points, not UTF-16 units, so an emoji or an Arabic
        // presentation form does not count double.
        if (password.codePointCount() < MIN_PASSWORD_LENGTH) {
            problems += CredentialProblem.PasswordTooShort
        }
        if (displayName.isBlank()) problems += CredentialProblem.DisplayNameBlank

        return problems
    }
}

/** Surrogate pairs count once. `String.length` would count an emoji as two characters. */
internal fun String.codePointCount(): Int {
    var count = 0
    var i = 0
    while (i < length) {
        val isHighSurrogate = this[i].isHighSurrogate() && i + 1 < length &&
            this[i + 1].isLowSurrogate()
        i += if (isHighSurrogate) 2 else 1
        count++
    }
    return count
}
