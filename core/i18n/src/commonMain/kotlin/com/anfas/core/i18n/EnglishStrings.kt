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
        override val addTitle = "Add member"
        override val addMessage =
            "Their membership number is assigned automatically. Sell them a plan afterwards " +
                "with Renew."
        override val addFullName = "Full name"
        override val addPhone = "Phone number"
        override val addPhoneOptional = "Optional — needed for WhatsApp reminders."
        override val addConfirm = "Add member"
        override val addSaving = "Adding…"
        override fun added(name: String, number: String) = "$name added as $number."
        override val addNameRequired = "Enter a name."
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
        override val cameraDeniedTitle = "Camera access is off"
        override val cameraDeniedMessage =
            "ANFAS needs the camera to photograph sign-up sheets. Turn it on in Settings, " +
                "or choose an existing photo instead."
        override val cameraDeniedAction = "Open Settings"
    }

    internal val MONTHS_SHORT_EN = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    override val states = object : AppStrings.States {
        // Deliberately not "changes will sync when you reconnect". There is no server and no
        // sync; promising one would be a lie the app cannot keep. Say what is true: the data is
        // on this device.
        override val offlineTitle = "Working offline — data is stored on this device"

        override val sessionExpiredTitle = "Your session ended"
        override val sessionExpiredMessage = "Sign in again to continue."
        override val sessionExpiredAction = "Sign in"

        override fun permissionDeniedTitle(area: String) = "You don't have access to $area"
        override val permissionDeniedMessage = "Ask the gym owner to update your role."
        override val permissionDeniedAction = "Go back"

        override val syncConflictTitle = "Sync conflict"
        override fun syncConflictMessage(count: Int) = if (count == 1) {
            "1 field differs between this device and the server. Choose which copy to keep."
        } else {
            "$count fields differ between this device and the server. " +
                "Choose which copy to keep."
        }
        override val syncConflictOnThisDevice = "On this device"
        override val syncConflictOnTheServer = "On the server"
        override val syncConflictField = "Field"
        override val syncConflictKeepMine = "Keep mine"
        override val syncConflictKeepServer = "Keep server"
        override val syncConflictDiffers = "Differs"
        override val syncConflictEmptyValue = "(empty)"

        override val fieldFullName = "Full name"
        override val fieldMembershipNumber = "Membership number"
        override val fieldPhone = "Phone number"
        override val fieldStatus = "Status"
    }

    override val auth = object : AppStrings.Auth {
        override val signInTitle = "ANFAS"
        override val signInTagline = "Speed is strength."
        override val username = "Username"
        override val password = "Password"
        override val showPassword = "Show password"
        override val hidePassword = "Hide password"
        override val rememberMe = "Keep me signed in"
        override val signIn = "Sign in"
        override val signingIn = "Signing in…"
        override val signOut = "Sign out"

        // One message for "no such user" and "wrong password", on purpose: naming which one
        // was wrong tells whoever is holding the device which usernames exist.
        override val invalidCredentials = "That username and password don't match."
        override val accountDisabled = "This account has been switched off. Ask the owner."

        override val setupTitle = "Set up this device"
        override val setupMessage =
            "Create the owner account. It can add the rest of your staff afterwards."
        override val displayName = "Your name"
        override val createOwner = "Create owner account"
        override val creating = "Creating…"

        override val problemUsernameTooShort = "At least 3 characters."
        override val problemUsernameTaken = "That username is taken."
        override val problemPasswordTooShort = "At least 8 characters."
        override val problemDisplayNameBlank = "Enter a name."
    }

    override val staff = object : AppStrings.Staff {
        override val title = "Staff"
        override val subtitle = "Who can sign in to this device, and what they can do."
        override val addStaff = "Add staff"
        override val you = "You"
        override val disabled = "Disabled"
        override val enable = "Enable"
        override val disable = "Disable"
        override val resetPassword = "Reset password"
        override fun resetPasswordFor(name: String) = "Reset password for $name"
        override val newPassword = "New password"
        override val save = "Save"
        override val saving = "Saving…"
        override val creating = "Creating…"
        override val emptyTitle = "No other staff yet"
        override val emptyMessage = "Add the people who work at the gym so they can sign in."
        override val loadFailedTitle = "Couldn't load staff"
        override fun showingStaff(count: Int) =
            if (count == 1) "Showing 1 account" else "Showing $count accounts"

        override val columnName = "Name"
        override val columnRoles = "Roles"
        override val columnActions = "Actions"

        override fun created(name: String) = "$name can now sign in."
        override val passwordReset = "Password changed."
        override val accountEnabled = "Account enabled."
        override val accountDisabled = "Account disabled."
        override val wouldLockOutDevice =
            "This is the only account that can manage staff. Add another first, " +
                "or nobody could turn it back on."
        override val accountGone = "That account is no longer available."

        override val roleOwner = "Owner"
        override val roleAdmin = "Admin"
        override val roleTherapist = "Therapist"
        override val roleCoach = "Coach"
        override val roleReceptionist = "Receptionist"
        override val roleMember = "Member"
    }

    override val dashboard = object : AppStrings.Dashboard {
        override val title = "Today"
        override val subtitle = "What needs doing at the desk."
        override val activeMembers = "Active members"
        override fun ofTotal(total: Int) = "of $total"
        override val needingRenewal = "Need renewing"
        override val needingRenewalHint = "Expired or expiring within a week"
        override val failedReminders = "Failed reminders"
        override val failedRemindersHint = "Not delivered"
        override val nothingToChase = "Nothing to chase"

        override val renewalQueueTitle = "Renewals to chase"
        override val renewalQueueEmpty = "No memberships are expiring this week."
        override val allClearTitle = "All clear"
        override val allClearMessage =
            "No memberships expiring this week and no reminders failing."
        override val loadFailedTitle = "Couldn't load today's figures"

        override val columnMember = "Member"
        override val columnPlan = "Plan"
        override val columnEnds = "Ends"
        override val expired = "Expired"
        override fun inDays(days: Int) = when (days) {
            0 -> "Today"
            1 -> "Tomorrow"
            else -> "In $days days"
        }
    }
}
