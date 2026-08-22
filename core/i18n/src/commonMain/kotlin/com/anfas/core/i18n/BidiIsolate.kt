package com.anfas.core.i18n

/**
 * Wraps a run of Latin/numeric text so it keeps its own internal order inside an Arabic sentence.
 *
 * This is the fix for a class of bug that is invisible in English and wrong in Arabic. A member id
 * rendered as `"رقم العضوية: #10003"` contains a direction-**neutral** `#`, and the Unicode
 * bidirectional algorithm resolves neutrals from their surroundings — so in an RTL paragraph the
 * `#` migrates to the far side and staff read `10003#`. The same thing happens to `+` in a phone
 * number, to `-` in a date and to a currency code.
 *
 * The alternative — forcing the whole `Text` to `TextDirection.Ltr` — is what this codebase tried
 * first, and it is worse: a string that also contains Arabic (a translated month name, a currency
 * abbreviation) then has *its* runs reordered, which turned `"22 أغسطس 2026"` into
 * `"22 2026 أغسطس"`. Isolating the Latin run instead leaves the Arabic alone.
 *
 * Uses LRI/PDI (U+2066/U+2069) rather than the deprecated LRE/PDF embedding pair: an isolate also
 * stops the wrapped run from influencing the direction of text *around* it, which is exactly the
 * guarantee wanted here.
 */
fun String.asLtrIsolate(): String = "$LTR_ISOLATE$this$POP_DIRECTIONAL_ISOLATE"

/**
 * True when the string carries bidi control characters, so a test can assert isolation without
 * hardcoding the code points at every call site.
 */
internal fun String.hasBidiIsolate(): Boolean =
    startsWith(LTR_ISOLATE) && endsWith(POP_DIRECTIONAL_ISOLATE)

/** U+2066 LEFT-TO-RIGHT ISOLATE. */
private const val LTR_ISOLATE = '⁦'

/** U+2069 POP DIRECTIONAL ISOLATE. */
private const val POP_DIRECTIONAL_ISOLATE = '⁩'
