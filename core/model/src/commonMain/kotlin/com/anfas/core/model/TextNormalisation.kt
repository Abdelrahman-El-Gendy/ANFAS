package com.anfas.core.model

/**
 * Text cleanup shared by the intake validator and (later) the OCR sheet parser.
 *
 * Pure and dependency-free, because it lives in `:core:model` — which may depend on nothing.
 * That constraint is a feature here: parsing vocabulary is *data*, not localised UI copy, so it
 * belongs with the domain rather than behind a resource lookup.
 */

/**
 * Folds Arabic-Indic (U+0660..U+0669) and Eastern Arabic / Persian (U+06F0..U+06F9) digits to
 * ASCII.
 *
 * This is not cosmetic. `Char.isDigit()` returns **true** for '٥', so a naive
 * `filter { it.isDigit() }` keeps those characters and the same phone number written in Arabic
 * numerals normalises to a different key — silently defeating duplicate detection on exactly the
 * sheets this app is meant to read.
 */
internal fun String.foldDigitsToAscii(): String = buildString(this@foldDigitsToAscii.length) {
    for (ch in this@foldDigitsToAscii) {
        append(
            when (ch) {
                in ARABIC_INDIC_ZERO..ARABIC_INDIC_NINE -> '0' + (ch - ARABIC_INDIC_ZERO)
                in EASTERN_ARABIC_ZERO..EASTERN_ARABIC_NINE -> '0' + (ch - EASTERN_ARABIC_ZERO)
                else -> ch
            },
        )
    }
}

/**
 * Strips the decorations Arabic handwriting and OCR add without changing the word: tatweel (the
 * kashida stretching character) and the harakat diacritics. Without this, "يناير" and a
 * tatweel-stretched or vowelled spelling of it compare unequal.
 */
internal fun String.stripArabicDecorations(): String =
    filterNot { it == TATWEEL || it in HARAKAT_START..HARAKAT_END }

/** Everything a comparison against a fixed vocabulary needs, in one call. */
internal fun String.normaliseForMatching(): String =
    trim().foldDigitsToAscii().stripArabicDecorations().lowercase()

private const val ARABIC_INDIC_ZERO = '٠'
private const val ARABIC_INDIC_NINE = '٩'
private const val EASTERN_ARABIC_ZERO = '۰'
private const val EASTERN_ARABIC_NINE = '۹'
private const val TATWEEL = 'ـ'
private const val HARAKAT_START = 'ً'
private const val HARAKAT_END = 'ْ'
