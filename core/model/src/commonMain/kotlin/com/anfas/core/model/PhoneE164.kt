package com.anfas.core.model

/**
 * The phone number in the form a messaging provider wants: international digits, no `+`.
 *
 * Deliberately **not** a change to [IntakeValidator.normalisePhone], and the distinction matters.
 * That function folds to the *local* Egyptian form (`+20 100 111 2222` -> `01001112222`) because it
 * exists for duplicate detection: it is what the indexed `phone_normalised` column stores, and what
 * OCR intake compares a photographed sheet against. Two different normalisations of the same number
 * would silently stop matching, so the local form has to stay exactly as it is.
 *
 * WhatsApp wants `201001112222`. Hence a second function rather than a changed one.
 *
 * Returns null rather than a best guess when the result would not be a plausible number. A wrong
 * number is worse than no number here: the send would be charged, counted against the business's
 * quality rating, and delivered to a stranger.
 */
object PhoneE164 {

    /**
     * @param defaultCountryCode used only when the number is written in local form. A number that
     *   already carries a country code keeps it, so a member with a Saudi or UAE number is not
     *   rewritten into an Egyptian one.
     */
    fun of(raw: String, defaultCountryCode: String = EGYPT): String? {
        // Fold first, for the same reason normalisePhone does: Char.isDigit() is true of
        // Arabic-Indic digits, so filtering without folding lets them through unconverted.
        val digits = raw.foldDigitsToAscii().filter { it.isDigit() }
        if (digits.isEmpty()) return null

        val international = when {
            // 00 is the other way of writing +, and some sheets use it.
            digits.startsWith("00") -> digits.removePrefix("00")

            // Local form: the trunk 0 stands in for the country code rather than being part of the
            // subscriber number, so it is replaced, not prepended to.
            digits.startsWith("0") -> defaultCountryCode + digits.drop(1)

            // No trunk zero and no 00, so length is the only thing left to go on -- and it has to
            // be, because "966501234567" (a Saudi number) and "1001112222" (an Egyptian one with
            // its trunk zero dropped) are told apart by nothing else. Short enough to be a bare
            // subscriber number means local; anything longer already carries a country code.
            //
            // Prepending unconditionally here was a real bug, caught by the foreign-number test:
            // it turned every Saudi and Emirati number into an Egyptian one, which would have sent
            // a member's renewal notice to whoever owns the resulting number.
            digits.length <= LOCAL_SUBSCRIBER_MAX -> defaultCountryCode + digits

            else -> digits
        }

        return international.takeIf { it.length in MIN_LENGTH..MAX_LENGTH }
    }

    private const val EGYPT = "20"

    /**
     * The longest a bare national subscriber number gets here: an Egyptian mobile is 10 digits
     * once the trunk 0 is dropped (`1XXXXXXXXX`).
     */
    private const val LOCAL_SUBSCRIBER_MAX = 10

    /** E.164 allows 15 digits; nothing shorter than 8 is a reachable number. */
    private const val MIN_LENGTH = 8
    private const val MAX_LENGTH = 15
}
