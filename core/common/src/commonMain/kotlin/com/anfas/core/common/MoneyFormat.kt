package com.anfas.core.common

import com.anfas.core.model.Money

/**
 * Renders [Money] the way the design writes prices: "600 EGP", "1,650 EGP", "−140 EGP".
 *
 * Shared from :core:common because prices appear in the renewal sheet, on member profiles and
 * on the dashboard — and features cannot depend on each other.
 *
 * Two details that are easy to get wrong and that the export is specific about:
 *  - whole amounts drop the decimals entirely (`600 EGP`, never `600.00 EGP`), while a
 *    fractional amount keeps two places;
 *  - a negative amount uses U+2212 MINUS SIGN, not a hyphen, because "−140 EGP" is typeset
 *    text rather than a code expression.
 */
object MoneyFormat {

    private const val MINUS_SIGN = '−'

    fun format(money: Money, withSign: Boolean = false): String {
        val negative = money.minorUnits < 0
        val magnitude = if (negative) -money.minorUnits else money.minorUnits
        val per = money.currency.minorUnitsPerMajor
        val major = magnitude / per
        val minor = magnitude % per

        val digits = group(major)
        val amount = if (minor == 0L) digits else "$digits.${minor.toString().padStart(2, '0')}"
        val prefix = when {
            negative -> MINUS_SIGN.toString()
            withSign && !money.isZero -> "+"
            else -> ""
        }
        return "$prefix$amount ${money.currency.code}"
    }

    /** Explicitly signed, for a discount line that must read as a reduction. */
    fun formatNegated(money: Money): String = format(Money(-money.minorUnits, money.currency))

    private fun group(value: Long): String {
        val s = value.toString()
        if (s.length <= 3) return s
        return s.reversed().chunked(3).joinToString(",").reversed()
    }
}
