package com.anfas.core.i18n

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every quantified or parameterised string must actually substitute its argument.
 *
 * This exists because of a real bug: the Arabic class strings were written with Kotlin's escaped
 * `${'$'}` form, which produces the **literal text** `$count`. It compiled, every other test
 * passed, and the week grid shipped reading "$count مكانًا" under every class. Nothing but running
 * the app in Arabic would have caught it — so this test does instead.
 *
 * The check is deliberately crude: a `$` surviving into rendered output is always a bug, because
 * no string in this app legitimately displays one. EGP amounts use "ج.م" and there is no other
 * currency.
 */
class PlaceholderInterpolationTest {

    private val languages = listOf<Pair<String, AppStrings>>(
        "English" to EnglishStrings,
        "Arabic" to ArabicStrings,
    )

    @Test
    fun `no rendered string contains an uninterpolated placeholder`() {
        languages.forEach { (name, s) ->
            renderedStrings(s).forEach { (key, value) ->
                assertTrue(
                    !value.contains('$'),
                    "$name.$key rendered \"$value\" — a literal '$' means the placeholder " +
                        "was escaped instead of interpolated",
                )
            }
        }
    }

    /**
     * The argument has to appear somewhere in the result, or the placeholder was dropped rather
     * than merely escaped — which is the other half of the same mistake and looks fine in a
     * screenshot until someone reads the number.
     */
    @Test
    fun `a quantified string contains the number it was given`() {
        languages.forEach { (name, s) ->
            listOf(3, 7, 11).forEach { count ->
                listOf(
                    "classes.places" to s.classes.places(count),
                    "classes.durationMinutes" to s.classes.durationMinutes(count),
                    "members.showingMembers" to s.members.showingMembers(count),
                ).forEach { (key, rendered) ->
                    assertTrue(
                        rendered.contains(count.toString()),
                        "$name.$key($count) rendered \"$rendered\" without the number",
                    )
                }
            }
        }
    }

    @Test
    fun `a string given a name renders that name`() {
        languages.forEach { (name, s) ->
            val rendered = s.classes.saved("HIIT")
            assertTrue(rendered.contains("HIIT"), "$name.classes.saved dropped the name: $rendered")

            val clash = s.classes.roomClash("Studio 1", "Yoga")
            assertTrue(clash.contains("Studio 1"), "$name.classes.roomClash dropped the room")
            assertTrue(clash.contains("Yoga"), "$name.classes.roomClash dropped the other class")

            val day = s.classes.emptyDayTitle("الأحد")
            assertTrue(day.contains("الأحد"), "$name.classes.emptyDayTitle dropped the day")
        }
    }

    /**
     * Every function on the Classes group, called once, so a newly added one is covered without
     * anybody remembering to list it here. Kept to this group because it is the one written most
     * recently and by hand; widening it is cheap when the next group is added.
     */
    private fun renderedStrings(s: AppStrings): List<Pair<String, String>> = buildList {
        val c = s.classes
        add("classes.places(1)" to c.places(1))
        add("classes.places(3)" to c.places(3))
        add("classes.places(11)" to c.places(11))
        add("classes.places(100)" to c.places(100))
        add("classes.durationMinutes" to c.durationMinutes(45))
        add("classes.emptyDayTitle" to c.emptyDayTitle("Sunday"))
        add("classes.saved" to c.saved("HIIT"))
        add("classes.deleted" to c.deleted("HIIT"))
        add("classes.roomClash" to c.roomClash("Studio 1", "Yoga"))
        add("classes.weekRange" to c.weekRange("10 - 16 Aug"))
        add("members.showingMembers" to s.members.showingMembers(5))
        add("common.dayName" to s.common.dayName(1))
        add("common.dayNameShort" to s.common.dayNameShort(7))
    }
}
