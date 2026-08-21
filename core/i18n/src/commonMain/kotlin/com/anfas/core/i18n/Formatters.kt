package com.anfas.core.i18n

import com.anfas.core.common.RelativeStamp
import com.anfas.core.model.Currency
import com.anfas.core.model.Money
import kotlinx.datetime.LocalDate

/**
 * Renders the structures `:core:common` and `:core:model` produce, in the active language. This is
 * the layer that knows about words; the layers below it deliberately do not.
 */

/**
 * @param separator between the day word and the time. The export uses ", " in the members table
 *   and " " in the reminder queue, so it stays a parameter rather than a silent choice.
 */
fun AppStrings.format(stamp: RelativeStamp, separator: String = ", "): String = when (stamp) {
    is RelativeStamp.Today -> common.today(time(stamp.hour, stamp.minute), separator)
    is RelativeStamp.Yesterday -> common.yesterday(time(stamp.hour, stamp.minute), separator)
    is RelativeStamp.DaysAgo -> common.daysAgo(stamp.days)
    is RelativeStamp.On -> common.date(stamp.date.day, stamp.date.month.ordinal, stamp.date.year)
    RelativeStamp.None -> common.never
}

/** "12 Aug 2026" — the renewal sheet's date style. */
fun AppStrings.formatLong(date: LocalDate): String =
    common.dateLong(date.day, date.month.ordinal, date.year)

/** 24-hour, zero-padded. The export shows "17:30 PM", which is malformed; this drops the meridiem. */
private fun time(hour: Int, minute: Int): String = "${pad2(hour)}:${pad2(minute)}"

private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()

/**
 * How money is written in a given language.
 *
 * Digits stay **Latin in both languages**, deliberately. Egypt overwhelmingly uses Western Arabic
 * numerals in software, the currency code is Latin anyway, and an Arabic-Indic amount cannot be
 * compared against a printed sheet or pasted anywhere useful. [DigitSet.ArabicIndic] exists and is
 * tested so the decision is reversible — but phone numbers must stay Latin regardless.
 */
enum class DigitSet(private val zero: Char) {
    Latin('0'),
    ArabicIndic('٠'),
    ;

    fun render(digits: String): String = if (this == Latin) {
        digits
    } else {
        digits.map { if (it in '0'..'9') zero + (it - '0') else it }.joinToString("")
    }
}

data class MoneyStyle(
    val groupSeparator: String = ",",
    val decimalSeparator: String = ".",
    val digits: DigitSet = DigitSet.Latin,
    val currencyLabel: (Currency) -> String = { it.code },
    val minusSign: String = "−",
) {
    companion object {
        val English = MoneyStyle()

        /** Same numerals, Arabic currency word. */
        val Arabic = MoneyStyle(currencyLabel = { if (it == Currency.EGP) "ج.م" else it.code })
    }
}

fun AppLanguage.moneyStyle(): MoneyStyle = when (this) {
    AppLanguage.EN -> MoneyStyle.English
    AppLanguage.AR -> MoneyStyle.Arabic
}

/**
 * Renders [Money] the way the design writes prices: "600 EGP", "1,650 EGP", "−140 EGP".
 *
 * Whole amounts drop the decimals entirely, and a negative uses U+2212 MINUS SIGN rather than a
 * hyphen — the export typesets "−140 EGP" as text, not as a code expression.
 */
fun formatMoney(
    money: Money,
    style: MoneyStyle = MoneyStyle.English,
    withSign: Boolean = false,
): String {
    val negative = money.minorUnits < 0
    val magnitude = if (negative) -money.minorUnits else money.minorUnits
    val per = money.currency.minorUnitsPerMajor
    val major = magnitude / per
    val minor = magnitude % per

    val grouped = group(major, style.groupSeparator)
    val amount = if (minor == 0L) {
        grouped
    } else {
        grouped + style.decimalSeparator + minor.toString().padStart(2, '0')
    }
    val prefix = when {
        negative -> style.minusSign
        withSign && !money.isZero -> "+"
        else -> ""
    }
    return "$prefix${style.digits.render(amount)} ${style.currencyLabel(money.currency)}"
}

/** Explicitly signed, for a discount line that must read as a reduction. */
fun formatMoneyNegated(money: Money, style: MoneyStyle = MoneyStyle.English): String =
    formatMoney(Money(-money.minorUnits, money.currency), style)

private fun group(value: Long, separator: String): String {
    val s = value.toString()
    if (s.length <= 3) return s
    return s.reversed().chunked(3).joinToString(separator).reversed()
}
