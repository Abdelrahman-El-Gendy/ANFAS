package com.anfas.core.common

import com.anfas.core.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatTest {

    @Test
    fun `whole amounts drop the decimals entirely`() {
        assertEquals("600 EGP", MoneyFormat.format(Money.of(600)))
    }

    @Test
    fun `thousands are grouped`() {
        assertEquals("1,650 EGP", MoneyFormat.format(Money.of(1_650)))
        assertEquals("5,800 EGP", MoneyFormat.format(Money.of(5_800)))
        assertEquals("1,234,567 EGP", MoneyFormat.format(Money.of(1_234_567)))
    }

    @Test
    fun `fractional amounts keep two places`() {
        assertEquals("12.50 EGP", MoneyFormat.format(Money(1_250)))
        assertEquals("12.05 EGP", MoneyFormat.format(Money(1_205)))
    }

    @Test
    fun `a discount renders with a true minus sign not a hyphen`() {
        val formatted = MoneyFormat.formatNegated(Money.of(140))
        assertEquals("−140 EGP", formatted)
        // U+2212, not U+002D — the export typesets this, it is not a code expression.
        assertEquals('−', formatted.first())
    }

    @Test
    fun `zero never gets a sign even when signs are requested`() {
        assertEquals("0 EGP", MoneyFormat.format(Money.zero(), withSign = true))
    }
}
