package com.anfas.core.i18n

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the one failure mode a typed string table cannot catch on its own: a member that was
 * implemented by copy-pasting the English value.
 *
 * commonTest has no reflection, so this samples across every group rather than enumerating all
 * ~90 keys. That is enough to catch a wholesale copy-paste, which is the realistic mistake.
 */
class TranslationCoverageTest {

    private val en = EnglishStrings
    private val ar = ArabicStrings

    private val pairs: List<Pair<String, Pair<String, String>>> = listOf(
        "common.search" to (en.common.search to ar.common.search),
        "common.dismiss" to (en.common.dismiss to ar.common.dismiss),
        "common.total" to (en.common.total to ar.common.total),
        "members.title" to (en.members.title to ar.members.title),
        "members.subtitle" to (en.members.subtitle to ar.members.subtitle),
        "members.addMember" to (en.members.addMember to ar.members.addMember),
        "members.emptyTitle" to (en.members.emptyTitle to ar.members.emptyTitle),
        "members.statusActive" to (en.members.statusActive to ar.members.statusActive),
        "reminders.title" to (en.reminders.title to ar.reminders.title),
        "reminders.tabFailed" to (en.reminders.tabFailed to ar.reminders.tabFailed),
        "reminders.failureDialogTitle" to
            (en.reminders.failureDialogTitle to ar.reminders.failureDialogTitle),
        "reminders.notOptedInExplanation" to (
            en.reminders.failureNotOptedInExplanation to
                ar.reminders.failureNotOptedInExplanation
            ),
        "renewal.confirm" to (en.renewal.confirm to ar.renewal.confirm),
        "renewal.tierQuarterly" to (en.renewal.tierQuarterly to ar.renewal.tierQuarterly),
        "renewal.paymentCash" to (en.renewal.paymentCash to ar.renewal.paymentCash),
        "intake.title" to (en.intake.title to ar.intake.title),
        "intake.subtitle" to (en.intake.subtitle to ar.intake.subtitle),
        "intake.issueDuplicate" to (en.intake.issueDuplicate to ar.intake.issueDuplicate),
        "intake.sheetDiscarded" to (en.intake.sheetDiscarded to ar.intake.sheetDiscarded),
    )

    @Test
    fun `no sampled Arabic string is left as its English value`() {
        val untranslated = pairs.filter { (_, v) -> v.first == v.second }.map { it.first }
        assertTrue(untranslated.isEmpty(), "still English: $untranslated")
    }

    @Test
    fun `sampled Arabic strings contain Arabic script`() {
        val arabicRange = '؀'..'ۿ'
        val missingScript = pairs
            .filterNot { (_, v) -> v.second.any { it in arabicRange } }
            .map { it.first }
        assertTrue(missingScript.isEmpty(), "no Arabic characters in: $missingScript")
    }

    @Test
    fun `the plan tier labels match the vocabulary IntakeValidator parses off sheets`() {
        // The OCR validator accepts these exact Arabic words; if the UI and the parser drifted,
        // a sheet written in Arabic would be labelled differently from how the app displays it.
        assertTrue(
            com.anfas.core.model.IntakeValidator.parsePlanLabel(
                ar.renewal.tierMonthly,
            ) == "Monthly",
        )
        assertTrue(
            com.anfas.core.model.IntakeValidator
                .parsePlanLabel(ar.renewal.tierQuarterly) == "Quarterly",
        )
        assertTrue(
            com.anfas.core.model.IntakeValidator.parsePlanLabel(ar.renewal.tierAnnual) == "Annual",
        )
    }
}
