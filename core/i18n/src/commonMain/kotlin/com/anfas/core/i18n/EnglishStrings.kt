package com.anfas.core.i18n

/**
 * English copy, lifted verbatim from the literals that were previously inline in the screens, so
 * English output is byte-identical to before this refactor and the existing tests keep meaning.
 */
object EnglishStrings : AppStrings {

    override val common = object : AppStrings.Common {
        override val appName = "ANFAS"
        override val appTagline = "GYM MANAGEMENT"
        override val search = "Search"
        override val clearSearch = "Clear search"
        override val close = "Close"
        override val dismiss = "Dismiss"
        override val cancel = "CANCEL"
        override val discard = "DISCARD"
        override val retry = "Retry"
        override val openMember = "Open member"
        override val open = "Open"
        override val total = "Total"
        override val zoomIn = "Zoom in"
        override val zoomOut = "Zoom out"
        override val selected = "selected"

        override fun today(time: String, separator: String) = "Today$separator$time"
        override fun yesterday(time: String, separator: String) = "Yesterday$separator$time"
        override fun daysAgo(days: Int) = "$days days ago"
        override val never = "—"

        override fun date(day: Int, monthIndex: Int, year: Int) =
            "${MONTHS_SHORT_EN[monthIndex]} $day, $year"

        override fun dateLong(day: Int, monthIndex: Int, year: Int) =
            "$day ${MONTHS_SHORT_EN[monthIndex]} $year"
        override val back = "Back"
    }

    override val members = object : AppStrings.Members {
        override val title = "Members"
        override val subtitle = "Manage and track membership status."
        override val addMember = "ADD MEMBER"
        override val scanSheet = "SCAN SHEET"
        override val searchPlaceholder = "Search members"
        override val columnMember = "MEMBER"
        override val columnStatus = "STATUS"
        override val columnLastCheckIn = "LAST CHECK-IN"
        override val columnActions = "ACTIONS"
        override fun idPrefix(number: String) = "ID: $number"
        override fun actionsFor(memberName: String) = "Actions for $memberName"
        override fun showingMembers(count: Int) =
            if (count == 1) "Showing 1 member" else "Showing $count members"
        override val emptyTitle = "No members yet"
        override val emptyMessage = "Add one manually or scan a sign-up sheet to get started."
        override fun noMatchesTitle(query: String) = "No members match \"$query\""
        override val noMatchesMessage =
            "We couldn't find any member profiles matching this search query. " +
                "Try adjusting your spelling or search by membership number."
        override val loadFailedTitle = "Couldn't load members"
        override val clearSearchAction = "CLEAR SEARCH"

        override val statusActive = "Active"
        override val statusExpired = "Expired"
        override val statusSuspended = "Suspended"
        override val statusPaused = "Paused"
        override val profileTitle = "Member profile"
        override val profileCurrentMembership = "Current membership"
        override val profileStartDate = "Start date"
        override val profileEndDate = "End date"
        override val profileTimeRemaining = "Time remaining"
        override val profilePlan = "Plan"
        override val profileLastCheckIn = "Last check-in"
        override val profilePaid = "Paid"
        override val profileRenew = "Renew"
        override val profileSendReminder = "Send reminder"
        override val profileNoActivePlan = "No active membership"
        override val profileNoActivePlanMessage =
            "This member has no subscription on record. Renew to start one."
        override val profileNotFoundTitle = "Member not found"
        override val profileNotFoundMessage =
            "This member may have been deleted on another device."
        override val profileNoPhone = "No phone number"
        override fun profileExpiresInDays(days: Int) =
            if (days == 1) "Expires tomorrow" else "Expires in $days days"
        override val profileExpired = "Expired"
        override fun profileStartsOn(date: String) = "Starts $date"
        override fun profilePercent(percent: Int) = "$percent%"
    }

    override val reminders = object : AppStrings.Reminders {
        override val title = "Reminders"
        override val subtitle = "Subscription reminders sent over WhatsApp."
        override val searchPlaceholder = "Search name or phone"
        override val allTemplates = "All templates"
        override val tabQueued = "Queued"
        override val tabSent = "Sent"
        override val tabFailed = "Failed"
        override val columnMember = "MEMBER"
        override val columnPhone = "PHONE"
        override val columnTemplate = "TEMPLATE"
        override val columnScheduled = "SCHEDULED"
        override val columnStatus = "STATUS"
        override val columnActions = "ACTIONS"
        override fun showingMessages(count: Int, status: String) =
            "Showing $count $status " + if (count == 1) "message" else "messages"
        override fun retrySelected(count: Int) = "RETRY $count"
        override val retryNow = "RETRY NOW"
        override val retryUnavailable = "Retry unavailable"
        override val loadFailedTitle = "Couldn't load the queue"

        override val noFailedTitle = "No failed reminders"
        override fun noFailedMessageWithCount(delivered: Int) =
            "The queue is clear. Last $delivered messages delivered."
        override val noFailedMessage = "The queue is clear and running without interruptions."
        override val nothingQueuedTitle = "Nothing queued"
        override val nothingQueuedMessage =
            "Reminders appear here once the daily job schedules them."
        override val nothingSentTitle = "Nothing sent yet"
        override val nothingSentMessage = "Delivered reminders will be listed here."
        override val filteredEmptyTitle = "No reminders match these filters"
        override val filteredEmptyMessage = "Try a different template, or clear the search."
        override val clearFilters = "CLEAR FILTERS"

        override val failureDialogTitle = "Message not delivered"
        override val technicalDetails = "Technical details"
        override val showDetails = "Show"
        override val hideDetails = "Hide"
        override fun attempts(count: Int) = "$count attempt" + if (count == 1) "" else "s"
        override fun errorCode(code: Int) = "Error code: $code"

        override fun requeuedAll(count: Int) =
            if (count == 1) "Message requeued." else "$count messages requeued."
        override fun requeuedPartial(requeued: Int, requested: Int) =
            "$requeued of $requested requeued; the rest need action first."
        override val requeuedNone =
            "Nothing to retry — these failures need action before they can be resent."

        override val failureNotOptedInTitle = "Recipient has not opted in"
        override val failureNotOptedInExplanation =
            "Meta requires members to opt in before receiving template messages. " +
                "Ask them to send any message to the gym's WhatsApp number first."
        override val failureInvalidPhoneTitle = "Invalid phone number"
        override val failureInvalidPhoneExplanation =
            "The number could not be reached. Correct it on the member's profile " +
                "and the next scheduled run will pick it up."
        override val failureRateLimitedTitle = "Rate limited"
        override val failureRateLimitedExplanation =
            "The provider is throttling sends. The queue retries automatically; " +
                "no action is needed."
        override val failureTemplatePausedTitle = "Template paused by Meta"
        override val failureTemplatePausedExplanation =
            "This template is paused and cannot be sent. Choose another template " +
                "or wait for Meta to reinstate it."
        override val failureUnknownTitle = "Message not delivered"
        override val failureUnknownExplanation =
            "The provider did not say why. Check the technical details below."
    }

    override val renewal = object : AppStrings.Renewal {
        override val selectDuration = "SELECT DURATION"
        override val startDate = "START DATE"
        override val paymentMethod = "PAYMENT METHOD"
        override val startToday = "Start today"
        override val startWhenCurrentEnds = "Start when current ends"
        override val confirm = "CONFIRM RENEWAL"
        override val confirming = "CONFIRMING…"
        override val sendWhatsAppConfirmation = "Send WhatsApp confirmation"
        override val discount = "Discount"
        override val noActivePlan = "No active plan"
        override fun currentPlanEnds(date: String) = "Current plan ends $date"
        override fun planLine(tier: String) = "$tier plan"
        override fun newEndDate(date: String) = "New end date: $date"
        override fun savePercent(percent: Int) = "Save $percent%"

        override val tierMonthly = "Monthly"
        override val tierQuarterly = "Quarterly"
        override val tierAnnual = "Annual"

        override val paymentCash = "Cash"
        override val paymentCard = "Card"
        override val paymentInstapay = "InstaPay"
        override val paymentVodafoneCash = "Vodafone Cash"
    }

    override val intake = object : AppStrings.Intake {
        override val title = "Intake"
        override val subtitle = "Review parsed data before import. Resolve warnings."
        override val sourceDocument = "Source document"
        override val noSourceImage = "No source image"
        override val sourceImageNotRendered = "Source image not rendered"
        override val columnOrdinal = "#"
        override val columnName = "NAME"
        override val columnPhone = "PHONE"
        override val columnStart = "START"
        override val columnEnd = "END"
        override val columnPlan = "PLAN"
        override fun rowsReady(ready: Int, total: Int) = "$ready of $total rows ready for import"
        override fun importCount(count: Int) = "IMPORT $count"
        override val importing = "IMPORTING…"
        override val emptyTitle = "No scans yet"
        override val emptyMessage =
            "Photograph a paper sign-up sheet to import members in bulk. " +
                "Text is read on the device — English script only for now."
        override val emptyMessageNoCapture =
            "Photograph a paper sign-up sheet on the phone or tablet app to import members " +
                "in bulk. This computer has no camera or text recognition."
        override val newScan = "New scan"
        override val choosePhoto = "Choose photo"
        override val scanning = "Reading the sheet…"
        override fun scannedRows(count: Int) =
            if (count == 1) "Found 1 row to review" else "Found $count rows to review"
        override val scanFoundNothing =
            "No rows could be read. Try again with the sheet flat and well lit."
        override val scanFailed = "The sheet could not be read."
        override val loadFailedTitle = "Couldn't load the sheet"

        override fun importedAll(count: Int) =
            if (count == 1) "1 member imported." else "$count members imported."
        override fun importedPartial(imported: Int, skipped: Int) =
            "$imported imported; $skipped still need fixing."
        override fun importedNoneAllBlocked(skipped: Int) =
            "Nothing imported — all $skipped rows still need fixing."
        override val importedNothingToDo = "Nothing on this sheet to import."
        override val sheetDiscarded = "Sheet discarded."

        override val issueMissingName = "Name missing"
        override val issueMissingPhone = "Phone missing"
        override val issueDuplicate = "Duplicate"
        override val issueDuplicateInSheet = "Duplicate in this sheet"
        override val issueEndBeforeStart = "End date before start"
        override val issueUnreadableDate = "Date unreadable"
        override val issueUnknownPlan = "Plan not recognised"
        override val issueLowConfidence = "Check this row"
    }

    internal val MONTHS_SHORT_EN = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}
