package com.anfas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A wrong number here is worse than no number: the send is charged, counts against the business's
 * quality rating with Meta, and reaches a stranger. So every case that could plausibly be stored in
 * the `phone` column gets an assertion, and anything implausible must come back null rather than as
 * a best guess.
 */
class PhoneE164Test {

    @Test
    fun `a local Egyptian number gains the country code in place of the trunk zero`() {
        // The trunk 0 stands in for the country code rather than being part of the subscriber
        // number, so it is replaced and not prepended to.
        assertEquals("201001112222", PhoneE164.of("01001112222"))
    }

    @Test
    fun `an already international number is left alone`() {
        assertEquals("201001112222", PhoneE164.of("+20 100 111 2222"))
        assertEquals("201001112222", PhoneE164.of("201001112222"))
    }

    @Test
    fun `double zero is treated as the plus it stands for`() {
        assertEquals("201001112222", PhoneE164.of("00201001112222"))
    }

    /**
     * The same trap `normalisePhone` documents: `Char.isDigit()` is true of Arabic-Indic digits, so
     * filtering without folding first would let them through unconverted and produce a number that
     * is not digits at all.
     */
    @Test
    fun `Arabic-Indic digits are folded before anything else`() {
        assertEquals("201001112222", PhoneE164.of("٠١٠٠١١١٢٢٢٢"))
    }

    @Test
    fun `formatting is ignored`() {
        assertEquals("201001112222", PhoneE164.of("(010) 0111-2222"))
        assertEquals("201001112222", PhoneE164.of(" 010 0111 2222 "))
    }

    @Test
    fun `a number too short to reach anyone is refused`() {
        assertNull(PhoneE164.of("12"))
        assertNull(PhoneE164.of("0100"))
        assertNull(PhoneE164.of(""))
        assertNull(PhoneE164.of("not a phone number"))
    }

    @Test
    fun `a number longer than E164 allows is refused`() {
        assertNull(PhoneE164.of("0100111222233334444"))
    }

    /**
     * A member with a foreign number keeps it. Rewriting a Saudi number into an Egyptian one would
     * send a renewal notice to whoever happens to own the resulting Egyptian number.
     */
    @Test
    fun `a foreign country code is preserved`() {
        assertEquals("966501234567", PhoneE164.of("+966 50 123 4567"))
        assertEquals("971501234567", PhoneE164.of("00971501234567"))
    }

    /**
     * The distinction this function exists for. `normalisePhone` deliberately produces the *local*
     * form because that is what the indexed duplicate-detection column stores; the two must not be
     * confused, and neither may be changed into the other.
     */
    @Test
    fun `it deliberately disagrees with normalisePhone`() {
        val raw = "+20 100 111 2222"
        assertEquals("01001112222", IntakeValidator.normalisePhone(raw))
        assertEquals("201001112222", PhoneE164.of(raw))
    }
}
