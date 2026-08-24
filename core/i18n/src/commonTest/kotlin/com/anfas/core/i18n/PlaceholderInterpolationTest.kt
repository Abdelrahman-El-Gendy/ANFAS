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

            val therapist = s.therapy.therapistPrefix("Dr. Youssef")
            assertTrue(
                therapist.contains("Dr. Youssef"),
                "$name.therapy.therapistPrefix dropped the name",
            )
            val trend = s.therapy.painScoreTrend(7, 3)
            assertTrue(
                trend.contains("7") && trend.contains("3"),
                "$name.therapy.painScoreTrend: $trend",
            )

            val createdBy = s.announcements.createdBy("Ahmed Owner")
            assertTrue(
                createdBy.contains("Ahmed Owner"),
                "$name.announcements.createdBy dropped the name",
            )
            val reaches = s.announcements.reaches(412)
            assertTrue(reaches.contains("412"), "$name.announcements.reaches dropped the count")
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

        val t = s.therapy
        add("therapy.durationMinutes" to t.durationMinutes(45))
        add("therapy.therapistPrefix" to t.therapistPrefix("Dr. Youssef"))
        add("therapy.referredByPrefix" to t.referredByPrefix("Dr. Amira Saleh"))
        add("therapy.caseOpenedOn" to t.caseOpenedOn("3 Jun 2026"))
        add("therapy.painScoreTrend" to t.painScoreTrend(7, 3))
        add("therapy.caseOpened" to t.caseOpened("Right shoulder impingement"))

        val a = s.announcements
        add("announcements.reaches(1)" to a.reaches(1))
        add("announcements.reaches(3)" to a.reaches(3))
        add("announcements.reaches(11)" to a.reaches(11))
        add("announcements.publishConfirmMessage" to a.publishConfirmMessage(412))
        add("announcements.createdBy" to a.createdBy("Ahmed Owner"))
        add("announcements.createdOn" to a.createdOn("3 Jun 2026"))
        add("announcements.publishedOn" to a.publishedOn("3 Jun 2026"))
        add("announcements.reachedAtPublish" to a.reachedAtPublish(412))
    }
}
