package com.anfas.core.i18n

/**
 * Every user-facing string, as a typed interface.
 *
 * Grouped by area so ~90 keys stay navigable. Quantified strings are **functions**, not
 * properties, because Arabic has six CLDR plural categories and a placeholder-substituted
 * property cannot express that.
 *
 * Adding a member here fails the build until every language implements it — which is the whole
 * point. A `values-ar/strings.xml` would silently fall back to English instead, and with this
 * many keys and one translator, silent fallback is the actual failure mode.
 */
interface AppStrings {
    val common: Common
    val members: Members
    val reminders: Reminders
    val renewal: Renewal
    val intake: Intake

    interface Common {
        val appName: String
        val appTagline: String
        val search: String
        val clearSearch: String
        val close: String
        val dismiss: String
        val cancel: String
        val discard: String
        val retry: String
        val openMember: String
        val open: String
        val total: String
        val zoomIn: String
        val zoomOut: String
        val selected: String

        /** "Today 08:15" / "Yesterday, 17:30" — [separator] differs per table in the design. */
        fun today(time: String, separator: String): String
        fun yesterday(time: String, separator: String): String
        fun daysAgo(days: Int): String
        val never: String

        /** Absolute date, e.g. "Oct 12, 2023" (English) or "12 أكتوبر 2023" (Arabic). */
        fun date(day: Int, monthIndex: Int, year: Int): String

        /** Longer form used on the renewal sheet: "12 Aug 2026". */
        fun dateLong(day: Int, monthIndex: Int, year: Int): String

        /** Content description for a back affordance. The icon auto-mirrors; this does not. */
        val back: String
    }

    interface Members {
        val title: String
        val subtitle: String
        val addMember: String
        val scanSheet: String
        val searchPlaceholder: String
        val columnMember: String
        val columnStatus: String
        val columnLastCheckIn: String
        val columnActions: String
        fun idPrefix(number: String): String
        fun actionsFor(memberName: String): String
        fun showingMembers(count: Int): String
        val emptyTitle: String
        val emptyMessage: String
        fun noMatchesTitle(query: String): String
        val noMatchesMessage: String
        val loadFailedTitle: String

        /** Button copy, distinct from common.clearSearch which labels the field's clear icon. */
        val clearSearchAction: String

        val statusActive: String
        val statusExpired: String
        val statusSuspended: String
        val statusPaused: String

        /** Member profile. */
        val profileTitle: String
        val profileCurrentMembership: String
        val profileStartDate: String
        val profileEndDate: String
        val profileTimeRemaining: String
        val profilePlan: String
        val profileLastCheckIn: String
        val profilePaid: String
        val profileRenew: String
        val profileSendReminder: String
        val profileNoActivePlan: String
        val profileNoActivePlanMessage: String
        val profileNotFoundTitle: String
        val profileNotFoundMessage: String
        val profileNoPhone: String

        /** Status pill above the membership card. */
        fun profileExpiresInDays(days: Int): String
        val profileExpired: String
        fun profileStartsOn(date: String): String

        /** "93%" — Latin digits, like every other number staff cross-reference. */
        fun profilePercent(percent: Int): String
    }

    interface Reminders {
        val title: String
        val subtitle: String
        val searchPlaceholder: String
        val allTemplates: String
        val tabQueued: String
        val tabSent: String
        val tabFailed: String
        val columnMember: String
        val columnPhone: String
        val columnTemplate: String
        val columnScheduled: String
        val columnStatus: String
        val columnActions: String
        fun showingMessages(count: Int, status: String): String
        fun retrySelected(count: Int): String
        val retryNow: String
        val retryUnavailable: String
        val loadFailedTitle: String

        val noFailedTitle: String
        fun noFailedMessageWithCount(delivered: Int): String
        val noFailedMessage: String
        val nothingQueuedTitle: String
        val nothingQueuedMessage: String
        val nothingSentTitle: String
        val nothingSentMessage: String
        val filteredEmptyTitle: String
        val filteredEmptyMessage: String
        val clearFilters: String

        val failureDialogTitle: String
        val technicalDetails: String
        val showDetails: String
        val hideDetails: String
        fun attempts(count: Int): String
        fun errorCode(code: Int): String

        /** Retry outcome, which must never overstate what happened. */
        fun requeuedAll(count: Int): String
        fun requeuedPartial(requeued: Int, requested: Int): String
        val requeuedNone: String

        val failureNotOptedInTitle: String
        val failureNotOptedInExplanation: String
        val failureInvalidPhoneTitle: String
        val failureInvalidPhoneExplanation: String
        val failureRateLimitedTitle: String
        val failureRateLimitedExplanation: String
        val failureTemplatePausedTitle: String
        val failureTemplatePausedExplanation: String
        val failureUnknownTitle: String
        val failureUnknownExplanation: String
    }

    interface Renewal {
        val selectDuration: String
        val startDate: String
        val paymentMethod: String
        val startToday: String
        val startWhenCurrentEnds: String
        val confirm: String
        val confirming: String
        val sendWhatsAppConfirmation: String
        val discount: String
        val noActivePlan: String
        fun currentPlanEnds(date: String): String
        fun planLine(tier: String): String
        fun newEndDate(date: String): String
        fun savePercent(percent: Int): String

        val tierMonthly: String
        val tierQuarterly: String
        val tierAnnual: String

        val paymentCash: String
        val paymentCard: String
        val paymentInstapay: String
        val paymentVodafoneCash: String
    }

    interface Intake {
        val title: String
        val subtitle: String
        val sourceDocument: String
        val noSourceImage: String
        val sourceImageNotRendered: String
        val columnOrdinal: String
        val columnName: String
        val columnPhone: String
        val columnStart: String
        val columnEnd: String
        val columnPlan: String
        fun rowsReady(ready: Int, total: Int): String
        fun importCount(count: Int): String
        val importing: String
        val emptyTitle: String
        val emptyMessage: String

        /** Desktop has no camera and no OCR engine; the empty state says so plainly. */
        val emptyMessageNoCapture: String
        val loadFailedTitle: String

        /** Capture. */
        val newScan: String
        val choosePhoto: String
        val scanning: String
        fun scannedRows(count: Int): String
        val scanFoundNothing: String
        val scanFailed: String

        /** Import outcome. The leftovers must be named, or staff think the sheet is done. */
        fun importedAll(count: Int): String
        fun importedPartial(imported: Int, skipped: Int): String
        fun importedNoneAllBlocked(skipped: Int): String
        val importedNothingToDo: String
        val sheetDiscarded: String

        val issueMissingName: String
        val issueMissingPhone: String
        val issueDuplicate: String
        val issueDuplicateInSheet: String
        val issueEndBeforeStart: String
        val issueUnreadableDate: String
        val issueUnknownPlan: String
        val issueLowConfidence: String
    }
}
