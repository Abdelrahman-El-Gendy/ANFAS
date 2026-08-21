package com.anfas.core.model

/**
 * An amount of money, held in **minor units** (piastres for EGP) so arithmetic is exact.
 * Never model prices as Double — 1,650.00 EGP split three ways must not drift.
 *
 * Formatting lives in the presentation layer; this type deliberately has no `toString`
 * override, so a stray string interpolation of a price is obvious in review rather than
 * silently rendering "Money(minorUnits=165000)".
 */
data class Money(val minorUnits: Long, val currency: Currency = Currency.EGP) : Comparable<Money> {

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = minorUnits + other.minorUnits)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = minorUnits - other.minorUnits)
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    val isZero: Boolean get() = minorUnits == 0L

    private fun requireSameCurrency(other: Money) = require(currency == other.currency) {
        "Cannot combine ${currency.code} with ${other.currency.code}"
    }

    companion object {
        fun zero(currency: Currency = Currency.EGP) = Money(0, currency)

        /** Whole units, e.g. `Money.of(600)` is 600.00 EGP. */
        fun of(majorUnits: Long, currency: Currency = Currency.EGP) =
            Money(majorUnits * currency.minorUnitsPerMajor, currency)
    }
}

enum class Currency(val code: String, val minorUnitsPerMajor: Int) {
    /** Egyptian pound; 100 piastres to the pound. The only currency the design shows. */
    EGP("EGP", 100),
}
