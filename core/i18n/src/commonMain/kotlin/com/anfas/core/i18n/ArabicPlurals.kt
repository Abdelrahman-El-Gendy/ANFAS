package com.anfas.core.i18n

/**
 * CLDR plural categories for Arabic. Six of them, against English's two — which is the concrete
 * reason a `values-ar/strings.xml` with `%d` placeholders cannot express Arabic counting
 * correctly without `plurals` for every counted string.
 */
enum class PluralCategory { ZERO, ONE, TWO, FEW, MANY, OTHER }

/**
 * Arabic plural selection per CLDR:
 *  - 0            -> zero
 *  - 1            -> one
 *  - 2            -> two
 *  - n%100 in 3..10  -> few
 *  - n%100 in 11..99 -> many
 *  - otherwise    -> other
 */
fun arabicPlural(count: Int): PluralCategory {
    val n = if (count < 0) -count else count
    val mod100 = n % 100
    return when {
        n == 0 -> PluralCategory.ZERO
        n == 1 -> PluralCategory.ONE
        n == 2 -> PluralCategory.TWO
        mod100 in 3..10 -> PluralCategory.FEW
        mod100 in 11..99 -> PluralCategory.MANY
        else -> PluralCategory.OTHER
    }
}
