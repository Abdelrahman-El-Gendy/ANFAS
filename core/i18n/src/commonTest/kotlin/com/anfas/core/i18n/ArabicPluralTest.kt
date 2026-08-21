package com.anfas.core.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Arabic has six CLDR plural categories against English's two. This is the concrete reason the
 * project uses a typed string table instead of `values-ar/strings.xml` with %d placeholders.
 */
class ArabicPluralTest {

    @Test
    fun `boundaries of every CLDR category`() {
        assertEquals(PluralCategory.ZERO, arabicPlural(0))
        assertEquals(PluralCategory.ONE, arabicPlural(1))
        assertEquals(PluralCategory.TWO, arabicPlural(2))

        // few: n % 100 in 3..10
        assertEquals(PluralCategory.FEW, arabicPlural(3))
        assertEquals(PluralCategory.FEW, arabicPlural(10))
        assertEquals(PluralCategory.FEW, arabicPlural(103))
        assertEquals(PluralCategory.FEW, arabicPlural(110))

        // many: n % 100 in 11..99
        assertEquals(PluralCategory.MANY, arabicPlural(11))
        assertEquals(PluralCategory.MANY, arabicPlural(99))
        assertEquals(PluralCategory.MANY, arabicPlural(111))

        // other: everything else, e.g. exact hundreds
        assertEquals(PluralCategory.OTHER, arabicPlural(100))
        assertEquals(PluralCategory.OTHER, arabicPlural(200))
        assertEquals(PluralCategory.OTHER, arabicPlural(101))
    }

    @Test
    fun `negative counts are treated by magnitude rather than crashing`() {
        assertEquals(PluralCategory.ONE, arabicPlural(-1))
    }

    @Test
    fun `the Arabic day-count string changes shape across categories`() {
        val c = ArabicStrings.common
        // If these collapsed to one form, the plural machinery would be doing nothing.
        val forms = setOf(c.daysAgo(1), c.daysAgo(2), c.daysAgo(3), c.daysAgo(15))
        assertEquals(4, forms.size, "expected four distinct plural forms, got $forms")
    }
}
