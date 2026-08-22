package com.anfas.core.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BidiIsolateTest {

    @Test
    fun `an isolate wraps without altering the content`() {
        val isolated = "#10003".asLtrIsolate()

        assertTrue(isolated.hasBidiIsolate())
        assertEquals("#10003", isolated.trim('⁦', '⁩'))
    }

    /**
     * The Arabic member id is the case this exists for: without an isolate the neutral `#` is
     * resolved from its RTL surroundings and staff read `10003#`.
     */
    @Test
    fun `the arabic member id isolates its number`() {
        val line = ArabicStrings.members.idPrefix("#10003")

        assertTrue(line.contains('⁦'), "expected an LTR isolate in: $line")
        assertTrue(line.contains('⁩'))
        assertTrue(line.startsWith("رقم"), "the Arabic label itself must not be isolated")
    }

    /**
     * English needs no isolate — the run and the paragraph already agree — and adding invisible
     * control characters there would show up in every string assertion for no benefit.
     */
    @Test
    fun `english does not add control characters`() {
        assertEquals("ID: #10003", EnglishStrings.members.idPrefix("#10003"))
    }
}
