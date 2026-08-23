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

        /**
         * Content description for the top bar's overflow button. "More" alone is what a screen
         * reader would announce with no hint of what is inside, so this names the group.
         */
        val moreOptions: String
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

        /** Manual add. The membership number is allocated, never typed. */
        val addTitle: String
        val addMessage: String
        val addFullName: String
        val addPhone: String
        val addPhoneOptional: String
        val addConfirm: String
        val addSaving: String
        fun added(name: String, number: String): String
        val addNameRequired: String
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

        /**
         * Camera access denied. Distinct copy from States.permissionDenied*, which is about a
         * staff *role* — telling someone to ask the gym owner to change their role would be
         * useless advice for an OS permission they can change themselves.
         */
        val cameraDeniedTitle: String
        val cameraDeniedMessage: String
        val cameraDeniedAction: String
    }

    /**
     * The four cross-cutting states from the export: offline, session expired, permission denied
     * and sync conflict. Their own section because none belongs to a feature — any screen can be
     * interrupted by them.
     */
    val states: States

    interface States {
        /** Persistent banner. Says only what is true today: nothing syncs yet. */
        val offlineTitle: String

        val sessionExpiredTitle: String
        val sessionExpiredMessage: String
        val sessionExpiredAction: String

        fun permissionDeniedTitle(area: String): String
        val permissionDeniedMessage: String
        val permissionDeniedAction: String

        val syncConflictTitle: String
        fun syncConflictMessage(count: Int): String
        val syncConflictOnThisDevice: String
        val syncConflictOnTheServer: String
        val syncConflictField: String
        val syncConflictKeepMine: String
        val syncConflictKeepServer: String
        val syncConflictDiffers: String
        val syncConflictEmptyValue: String

        /** Labels for MemberConflict's ConflictField entries. */
        val fieldFullName: String
        val fieldMembershipNumber: String
        val fieldPhone: String
        val fieldStatus: String
    }

    val auth: Auth

    interface Auth {
        val signInTitle: String
        val signInTagline: String
        val username: String
        val password: String
        val showPassword: String
        val hidePassword: String
        val rememberMe: String
        val signIn: String
        val signingIn: String
        val signOut: String

        /** Sign-in failures. Deliberately one message for both wrong user and wrong password. */
        val invalidCredentials: String
        val accountDisabled: String

        /** First run: no accounts exist, so the app offers setup rather than an unusable login. */
        val setupTitle: String
        val setupMessage: String
        val displayName: String
        val createOwner: String
        val creating: String

        val problemUsernameTooShort: String
        val problemUsernameTaken: String
        val problemPasswordTooShort: String
        val problemDisplayNameBlank: String
    }

    val staff: Staff

    interface Staff {
        val title: String
        val subtitle: String
        val addStaff: String
        val you: String
        val disabled: String
        val enable: String
        val disable: String
        val resetPassword: String
        fun resetPasswordFor(name: String): String
        val newPassword: String
        val save: String
        val saving: String
        val creating: String
        val emptyTitle: String
        val emptyMessage: String
        val loadFailedTitle: String
        fun showingStaff(count: Int): String

        /** Column headings. */
        val columnName: String
        val columnRoles: String
        val columnActions: String

        /** Notices. */
        fun created(name: String): String
        val passwordReset: String
        val accountEnabled: String
        val accountDisabled: String

        /**
         * The refusal that keeps a device recoverable. Worded as a rule, not an error, because
         * there is no server to recover from and the user needs to know it is deliberate.
         */
        val wouldLockOutDevice: String
        val accountGone: String

        /** Role names. */
        val roleOwner: String
        val roleAdmin: String
        val roleTherapist: String
        val roleCoach: String
        val roleReceptionist: String
        val roleMember: String
    }

    val dashboard: Dashboard

    interface Dashboard {
        val title: String
        val subtitle: String

        /** Tiles. Each one is backed by stored data — see the screen's KDoc for what is not. */
        val activeMembers: String
        fun ofTotal(total: Int): String
        val needingRenewal: String
        val needingRenewalHint: String
        val failedReminders: String
        val failedRemindersHint: String
        val nothingToChase: String

        val renewalQueueTitle: String
        val renewalQueueEmpty: String
        val allClearTitle: String
        val allClearMessage: String
        val loadFailedTitle: String

        /** Row copy. */
        val columnMember: String
        val columnPlan: String
        val columnEnds: String
        val expired: String
        fun inDays(days: Int): String

        /** Shown under the check-in tile only when somebody was refused. */
        fun turnedAwayHint(count: Int): String
    }

    val checkIn: CheckIn

    interface CheckIn {
        val title: String
        val subtitle: String
        val searchPlaceholder: String
        val searchPrompt: String
        val action: String
        val recording: String

        /** Today's header figures. Capacity is absent — see the screen's KDoc. */
        val totalToday: String
        val deniedToday: String
        val peakHour: String
        fun hourLabel(hour: Int): String
        val noPeakYet: String

        val logTitle: String
        val logEmpty: String
        val loadFailedTitle: String
        fun noMatches(query: String): String

        /** Outcomes. Each one names what the desk does next, not just that it failed. */
        val granted: String
        val outcomeExpired: String
        val outcomeSuspended: String
        val outcomePaused: String
        val outcomeNoMembership: String

        fun grantedNotice(name: String): String
        fun deniedNotice(name: String): String
    }
}
