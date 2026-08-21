package com.anfas.core.i18n

import com.anfas.core.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatTest {

    @Test
    fun `whole amounts drop the decimals entirely`() {
        assertEquals("600 EGP", formatMoney(Money.of(600)))
    }

    @Test
    fun `thousands are grouped`() {
        assertEquals("1,650 EGP", formatMoney(Money.of(1_650)))
        assertEquals("5,800 EGP", formatMoney(Money.of(5_800)))
        assertEquals("1,234,567 EGP", formatMoney(Money.of(1_234_567)))
    }

    @Test
    fun `fractional amounts keep two places`() {
        assertEquals("12.50 EGP", formatMoney(Money(1_250)))
        assertEquals("12.05 EGP", formatMoney(Money(1_205)))
    }

    @Test
    fun `a discount renders with a true minus sign not a hyphen`() {
        val formatted = formatMoneyNegated(Money.of(140))
        assertEquals("−140 EGP", formatted)
        // U+2212, not U+002D — the export typesets this, it is not a code expression.
        assertEquals('−', formatted.first())
    }

    @Test
    fun `Arabic uses Latin digits with an Arabic currency word`() {
        // Deliberate: Egypt uses Western numerals in software, and an Arabic-Indic amount cannot
        // be compared against a printed sheet.
        assertEquals("1,650 ج.م", formatMoney(Money.of(1_650), MoneyStyle.Arabic))
    }

    @Test
    fun `the Arabic-Indic digit set works when it is asked for`() {
        // Reversible decision: the mapping exists and is tested even though nothing selects it.
        val arabicIndic = MoneyStyle.Arabic.copy(digits = DigitSet.ArabicIndic)
        assertEquals("١,٦٥٠ ج.م", formatMoney(Money.of(1_650), arabicIndic))
    }

    @Test
    fun `zero never gets a sign even when signs are requested`() {
        assertEquals("0 EGP", formatMoney(Money.zero(), withSign = true))
    }
}
