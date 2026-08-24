package com.anfas.core.i18n

/**
 * Arabic copy.
 *
 * Two conventions applied throughout, both deliberate:
 *
 * Numbers stay in **Latin digits**. Egypt overwhelmingly uses Western Arabic numerals in
 * software, a phone number rendered "+٢٠ ١٠٠ ١٢٣ ٤٥٦٧" cannot be pasted into a dialler or matched
 * against an OCR'd sheet, and the currency code "EGP" is Latin already.
 *
 * There is **no upper-casing**. Arabic has no letter case, so the design's SCREAMING labels
 * ("ADD MEMBER", "DISCARD") become ordinary sentence-case Arabic. The `labelCaps` type role keeps
 * its name but drops its letter-spacing for Arabic — see AnfasType.
 *
 * DRAFT TRANSLATIONS. These are written to be structurally correct and reviewable, not to be
 * shipped unreviewed; a native Egyptian Arabic speaker should pass over them before release.
 */
object ArabicStrings : AppStrings {

    override val common = object : AppStrings.Common {
        override val appName = "أنفاس"
        override val appTagline = "إدارة الصالة"
        override val search = "بحث"
        override val clearSearch = "مسح البحث"
        override val close = "إغلاق"
        override val dismiss = "تجاهل"
        override val cancel = "إلغاء"
        override val discard = "تجاهل"
        override val retry = "إعادة المحاولة"
        override val openMember = "فتح ملف العضو"
        override val open = "فتح"
        override val total = "الإجمالي"
        override val zoomIn = "تكبير"
        override val zoomOut = "تصغير"
        override val selected = "محدد"

        override fun today(time: String, separator: String) = "اليوم$separator$time"
        override fun yesterday(time: String, separator: String) = "أمس$separator$time"

        override fun daysAgo(days: Int) = when (arabicPlural(days)) {
            PluralCategory.ZERO -> "اليوم"
            PluralCategory.ONE -> "منذ يوم"
            PluralCategory.TWO -> "منذ يومين"
            PluralCategory.FEW -> "منذ $days أيام"
            else -> "منذ $days يومًا"
        }

        override val never = "—"

        override fun date(day: Int, monthIndex: Int, year: Int) =
            "$day ${MONTHS_AR[monthIndex]} $year"

        override fun dateLong(day: Int, monthIndex: Int, year: Int) =
            "$day ${MONTHS_AR[monthIndex]} $year"
        override fun dayName(isoDayNumber: Int) = ARABIC_DAYS
            .getOrElse(isoDayNumber - 1) { "" }

        // Arabic has no case and no three-letter convention, so the short form drops the
        // "يوم"-style prefix rather than truncating -- a truncated Arabic word is unreadable.
        override fun dayNameShort(isoDayNumber: Int) = ARABIC_DAYS_SHORT
            .getOrElse(isoDayNumber - 1) { "" }
        override val back = "رجوع"
        override val moreOptions = "الحساب والإعدادات"
    }

    override val members = object : AppStrings.Members {
        override val title = "الأعضاء"
        override val subtitle = "إدارة العضويات ومتابعة حالتها."
        override val addMember = "إضافة عضو"
        override val scanSheet = "تصوير كشف"
        override val searchPlaceholder = "ابحث عن عضو"
        override val columnMember = "العضو"
        override val columnStatus = "الحالة"
        override val columnLastCheckIn = "آخر حضور"
        override val columnActions = "إجراءات"

        // The number is isolated, the label is not: see BidiIsolate.kt. Without this the
        // neutral "#" resolves from its RTL surroundings and reads as "10003#".
        override fun idPrefix(number: String) = "رقم العضوية: ${number.asLtrIsolate()}"
        override fun actionsFor(memberName: String) = "إجراءات $memberName"

        override fun showingMembers(count: Int) = when (arabicPlural(count)) {
            PluralCategory.ZERO -> "لا يوجد أعضاء"
            PluralCategory.ONE -> "عرض عضو واحد"
            PluralCategory.TWO -> "عرض عضوين"
            PluralCategory.FEW -> "عرض $count أعضاء"
            else -> "عرض $count عضوًا"
        }

        override val emptyTitle = "لا يوجد أعضاء بعد"
        override val emptyMessage = "أضف عضوًا يدويًا أو صوّر كشف تسجيل للبدء."
        override fun noMatchesTitle(query: String) = "لا يوجد عضو مطابق لـ \"$query\""
        override val noMatchesMessage =
            "لم نجد أي ملف عضوية مطابق لهذا البحث. جرّب تصحيح الكتابة أو ابحث برقم العضوية."
        override val loadFailedTitle = "تعذّر تحميل الأعضاء"
        override val clearSearchAction = "مسح البحث"

        override val statusActive = "نشط"
        override val statusExpired = "منتهي"
        override val statusSuspended = "موقوف"
        override val statusPaused = "متوقف مؤقتًا"
        override val profileTitle = "ملف العضو"
        override val profileCurrentMembership = "العضوية الحالية"
        override val profileStartDate = "تاريخ البداية"
        override val profileEndDate = "تاريخ النهاية"
        override val profileTimeRemaining = "الوقت المتبقي"
        override val profilePlan = "الخطة"
        override val profileLastCheckIn = "آخر حضور"
        override val profilePaid = "المدفوع"
        override val profileRenew = "تجديد"
        override val profileSendReminder = "إرسال تذكير"
        override val profileNoActivePlan = "لا توجد عضوية حالية"
        override val profileNoActivePlanMessage =
            "لا يوجد اشتراك مسجل لهذا العضو. اضغط تجديد للبدء."
        override val profileNotFoundTitle = "العضو غير موجود"
        override val profileNotFoundMessage = "قد يكون هذا العضو حُذف من جهاز آخر."
        override val profileNoPhone = "لا يوجد رقم هاتف"
        override fun profileExpiresInDays(days: Int) = when (arabicPlural(days)) {
            PluralCategory.ZERO -> "انتهت"
            PluralCategory.ONE -> "تنتهي غدًا"
            PluralCategory.TWO -> "تنتهي بعد يومين"
            PluralCategory.FEW -> "تنتهي بعد $days أيام"
            PluralCategory.MANY -> "تنتهي بعد $days يومًا"
            PluralCategory.OTHER -> "تنتهي بعد $days يوم"
        }
        override val profileExpired = "منتهية"
        override fun profileStartsOn(date: String) = "تبدأ $date"
        override fun profilePercent(percent: Int) = "$percent%"
        override val addTitle = "إضافة عضو"
        override val addMessage =
            "يُخصَّص رقم العضوية تلقائيًا. يمكنك بيع خطة له بعد ذلك من زر التجديد."
        override val addFullName = "الاسم الكامل"
        override val addPhone = "رقم الهاتف"
        override val addPhoneOptional = "اختياري — مطلوب لتذكيرات واتساب."
        override val addConfirm = "إضافة عضو"
        override val addSaving = "جارٍ الإضافة…"
        override fun added(name: String, number: String) =
            "تمت إضافة $name برقم ${number.asLtrIsolate()}."
        override val addNameRequired = "أدخل اسمًا."
    }

    override val reminders = object : AppStrings.Reminders {
        override val title = "التذكيرات"
        override val subtitle = "تذكيرات الاشتراكات المُرسلة عبر واتساب."
        override val searchPlaceholder = "ابحث بالاسم أو الرقم"
        override val allTemplates = "كل القوالب"
        override val tabQueued = "في الانتظار"
        override val tabSent = "مُرسلة"
        override val tabFailed = "فاشلة"
        override val columnMember = "العضو"
        override val columnPhone = "الهاتف"
        override val columnTemplate = "القالب"
        override val columnScheduled = "الموعد"
        override val columnStatus = "الحالة"
        override val columnActions = "إجراءات"

        override fun showingMessages(count: Int, status: String) = when (arabicPlural(count)) {
            PluralCategory.ZERO -> "لا توجد رسائل $status"
            PluralCategory.ONE -> "عرض رسالة $status واحدة"
            PluralCategory.TWO -> "عرض رسالتين $status"
            PluralCategory.FEW -> "عرض $count رسائل $status"
            else -> "عرض $count رسالة $status"
        }

        override fun retrySelected(count: Int) = "إعادة إرسال $count"
        override val retryNow = "إعادة الإرسال الآن"
        override val retryUnavailable = "إعادة المحاولة غير متاحة"
        override val loadFailedTitle = "تعذّر تحميل قائمة التذكيرات"

        override val noFailedTitle = "لا توجد تذكيرات فاشلة"
        override fun noFailedMessageWithCount(delivered: Int) =
            "القائمة سليمة. تم تسليم آخر $delivered رسالة."
        override val noFailedMessage = "القائمة سليمة وتعمل دون انقطاع."
        override val nothingQueuedTitle = "لا شيء في الانتظار"
        override val nothingQueuedMessage = "تظهر التذكيرات هنا بعد أن تجدولها المهمة اليومية."
        override val nothingSentTitle = "لم يُرسل شيء بعد"
        override val nothingSentMessage = "ستظهر هنا التذكيرات التي تم تسليمها."
        override val filteredEmptyTitle = "لا توجد تذكيرات مطابقة لهذه الفلاتر"
        override val filteredEmptyMessage = "جرّب قالبًا آخر أو امسح البحث."
        override val clearFilters = "مسح الفلاتر"

        override val failureDialogTitle = "لم يتم تسليم الرسالة"
        override val technicalDetails = "تفاصيل فنية"
        override val showDetails = "إظهار"
        override val hideDetails = "إخفاء"

        override fun attempts(count: Int) = when (arabicPlural(count)) {
            PluralCategory.ONE -> "محاولة واحدة"
            PluralCategory.TWO -> "محاولتان"
            PluralCategory.FEW -> "$count محاولات"
            else -> "$count محاولة"
        }

        override fun errorCode(code: Int) = "رمز الخطأ: $code"

        override fun requeuedAll(count: Int) = when (arabicPlural(count)) {
            PluralCategory.ONE -> "تم إعادة جدولة الرسالة."
            PluralCategory.TWO -> "تم إعادة جدولة رسالتين."
            PluralCategory.FEW -> "تم إعادة جدولة $count رسائل."
            else -> "تم إعادة جدولة $count رسالة."
        }

        override fun requeuedPartial(requeued: Int, requested: Int) =
            "تم إعادة جدولة $requeued من $requested؛ الباقي يحتاج إلى إجراء أولًا."
        override val requeuedNone =
            "لا شيء لإعادة إرساله — هذه الأخطاء تحتاج إلى إجراء قبل إعادة الإرسال."

        override val failureNotOptedInTitle = "المستلم لم يوافق على الاستلام"
        override val failureNotOptedInExplanation =
            "تشترط Meta موافقة العضو قبل استلام رسائل القوالب. " +
                "اطلب منه إرسال أي رسالة إلى رقم واتساب الصالة أولًا."
        override val failureInvalidPhoneTitle = "رقم هاتف غير صالح"
        override val failureInvalidPhoneExplanation =
            "لم يمكن الوصول إلى الرقم. صحّحه في ملف العضو وستتولاه المهمة المجدولة التالية."
        override val failureRateLimitedTitle = "تم تقييد معدل الإرسال"
        override val failureRateLimitedExplanation =
            "المزوّد يقيّد الإرسال حاليًا. تعيد القائمة المحاولة تلقائيًا؛ لا حاجة لأي إجراء."
        override val failureTemplatePausedTitle = "القالب موقوف من Meta"
        override val failureTemplatePausedExplanation =
            "هذا القالب موقوف ولا يمكن إرساله. اختر قالبًا آخر أو انتظر إعادة تفعيله."
        override val failureUnknownTitle = "لم يتم تسليم الرسالة"
        override val failureUnknownExplanation =
            "لم يوضّح المزوّد السبب. راجع التفاصيل الفنية أدناه."
    }

    override val renewal = object : AppStrings.Renewal {
        override val selectDuration = "اختر المدة"
        override val startDate = "تاريخ البداية"
        override val paymentMethod = "طريقة الدفع"
        override val startToday = "تبدأ اليوم"
        override val startWhenCurrentEnds = "تبدأ بعد انتهاء الحالية"
        override val confirm = "تأكيد التجديد"
        override val confirming = "جارٍ التأكيد…"
        override val sendWhatsAppConfirmation = "إرسال تأكيد على واتساب"
        override val discount = "خصم"
        override val noActivePlan = "لا توجد خطة نشطة"
        override fun currentPlanEnds(date: String) = "تنتهي الخطة الحالية في $date"
        override fun planLine(tier: String) = "خطة $tier"
        override fun newEndDate(date: String) = "تاريخ الانتهاء الجديد: $date"
        override fun savePercent(percent: Int) = "وفّر $percent%"

        override val tierMonthly = "شهري"
        override val tierQuarterly = "ربع سنوي"
        override val tierAnnual = "سنوي"

        override val paymentCash = "نقدًا"
        override val paymentCard = "بطاقة"
        override val paymentInstapay = "إنستاباي"
        override val paymentVodafoneCash = "فودافون كاش"
    }

    override val intake = object : AppStrings.Intake {
        override val title = "الإدخال"
        override val subtitle = "راجع البيانات المقروءة قبل الاستيراد. عالج التحذيرات."
        override val sourceDocument = "المستند الأصلي"
        override val noSourceImage = "لا توجد صورة"
        override val sourceImageNotRendered = "لم تُعرض الصورة"
        override val columnOrdinal = "#"
        override val columnName = "الاسم"
        override val columnPhone = "الهاتف"
        override val columnStart = "البداية"
        override val columnEnd = "النهاية"
        override val columnPlan = "الخطة"
        override fun rowsReady(ready: Int, total: Int) = "$ready من $total صفوف جاهزة للاستيراد"
        override fun importCount(count: Int) = "استيراد $count"
        override val importing = "جارٍ الاستيراد…"
        override val emptyTitle = "لا توجد صور بعد"
        override val emptyMessage =
            "صوّر كشف تسجيل ورقي لاستيراد الأعضاء دفعة واحدة. " +
                "تُقرأ النصوص على الجهاز — بالحروف اللاتينية فقط حاليًا."
        override val emptyMessageNoCapture =
            "صوّر كشف التسجيل الورقي من تطبيق الهاتف أو التابلت " +
                "لاستيراد الأعضاء دفعة واحدة. هذا الجهاز لا يحتوي على كاميرا أو تعرّف على النصوص."
        override val newScan = "مسح جديد"
        override val choosePhoto = "اختر صورة"
        override val scanning = "جارٍ قراءة الكشف…"
        override fun scannedRows(count: Int) = when (arabicPlural(count)) {
            PluralCategory.ZERO -> "لم يتم العثور على صفوف"
            PluralCategory.ONE -> "تم العثور على صف واحد للمراجعة"
            PluralCategory.TWO -> "تم العثور على صفين للمراجعة"
            PluralCategory.FEW -> "تم العثور على $count صفوف للمراجعة"
            PluralCategory.MANY -> "تم العثور على $count صفًا للمراجعة"
            PluralCategory.OTHER -> "تم العثور على $count صف للمراجعة"
        }
        override val scanFoundNothing =
            "لم نتمكن من قراءة أي صف. حاول مرة أخرى مع فرد الكشف وإضاءة جيدة."
        override val scanFailed = "لم نتمكن من قراءة الكشف."
        override val loadFailedTitle = "تعذّر تحميل الكشف"

        override fun importedAll(count: Int) = when (arabicPlural(count)) {
            PluralCategory.ONE -> "تم استيراد عضو واحد."
            PluralCategory.TWO -> "تم استيراد عضوين."
            PluralCategory.FEW -> "تم استيراد $count أعضاء."
            else -> "تم استيراد $count عضوًا."
        }

        override fun importedPartial(imported: Int, skipped: Int) =
            "تم استيراد $imported؛ و$skipped لا تزال تحتاج إلى تصحيح."
        override fun importedNoneAllBlocked(skipped: Int) =
            "لم يتم استيراد أي شيء — جميع الصفوف ($skipped) تحتاج إلى تصحيح."
        override val importedNothingToDo = "لا يوجد ما يمكن استيراده من هذا الكشف."
        override val sheetDiscarded = "تم تجاهل الكشف."

        override val issueMissingName = "الاسم مفقود"
        override val issueMissingPhone = "الهاتف مفقود"
        override val issueDuplicate = "مكرر"
        override val issueDuplicateInSheet = "مكرر في هذا الكشف"
        override val issueEndBeforeStart = "تاريخ النهاية قبل البداية"
        override val issueUnreadableDate = "تاريخ غير مقروء"
        override val issueUnknownPlan = "خطة غير معروفة"
        override val issueLowConfidence = "راجع هذا الصف"
        override val cameraDeniedTitle = "الوصول إلى الكاميرا مُعطّل"
        override val cameraDeniedMessage =
            "يحتاج ANFAS إلى الكاميرا لتصوير كشوف التسجيل. شغّلها من الإعدادات، " +
                "أو اختر صورة موجودة بدلًا من ذلك."
        override val cameraDeniedAction = "فتح الإعدادات"
    }

    /** Egyptian Arabic month names, matching what IntakeValidator parses off the sheets. */
    internal val MONTHS_AR = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    override val states = object : AppStrings.States {
        override val offlineTitle = "يعمل بدون اتصال — البيانات محفوظة على هذا الجهاز"

        override val sessionExpiredTitle = "انتهت الجلسة"
        override val sessionExpiredMessage = "سجّل الدخول مرة أخرى للمتابعة."
        override val sessionExpiredAction = "تسجيل الدخول"

        override fun permissionDeniedTitle(area: String) = "لا تملك صلاحية الوصول إلى $area"
        override val permissionDeniedMessage = "اطلب من مالك الصالة تحديث دورك."
        override val permissionDeniedAction = "رجوع"

        override val syncConflictTitle = "تعارض في المزامنة"
        override fun syncConflictMessage(count: Int) = when (arabicPlural(count)) {
            PluralCategory.ZERO -> "لا توجد حقول مختلفة."
            PluralCategory.ONE -> "يوجد حقل واحد مختلف بين هذا الجهاز والسيرفر. اختر النسخة."
            PluralCategory.TWO -> "يوجد حقلان مختلفان بين هذا الجهاز والسيرفر. اختر النسخة."
            PluralCategory.FEW -> "توجد $count حقول مختلفة بين هذا الجهاز والسيرفر. اختر النسخة."
            PluralCategory.MANY -> "يوجد $count حقلًا مختلفًا بين هذا الجهاز والسيرفر. اختر النسخة."
            PluralCategory.OTHER -> "يوجد $count حقل مختلف بين هذا الجهاز والسيرفر. اختر النسخة."
        }
        override val syncConflictOnThisDevice = "على هذا الجهاز"
        override val syncConflictOnTheServer = "على السيرفر"
        override val syncConflictField = "الحقل"
        override val syncConflictKeepMine = "احتفظ بنسختي"
        override val syncConflictKeepServer = "احتفظ بنسخة السيرفر"
        override val syncConflictDiffers = "مختلف"
        override val syncConflictEmptyValue = "(فارغ)"

        override val fieldFullName = "الاسم الكامل"
        override val fieldMembershipNumber = "رقم العضوية"
        override val fieldPhone = "رقم الهاتف"
        override val fieldStatus = "الحالة"
    }

    override val auth = object : AppStrings.Auth {
        override val signInTitle = "ANFAS"
        override val signInTagline = "السرعة قوة."
        override val username = "اسم المستخدم"
        override val password = "كلمة المرور"
        override val showPassword = "إظهار كلمة المرور"
        override val hidePassword = "إخفاء كلمة المرور"
        override val rememberMe = "ابقِ الجلسة مفتوحة"
        override val signIn = "تسجيل الدخول"
        override val signingIn = "جارٍ تسجيل الدخول…"
        override val signOut = "تسجيل الخروج"

        override val invalidCredentials = "اسم المستخدم أو كلمة المرور غير صحيحة."
        override val accountDisabled = "هذا الحساب مُعطّل. راجع المالك."

        override val setupTitle = "إعداد هذا الجهاز"
        override val setupMessage = "أنشئ حساب المالك. يمكنه بعد ذلك إضافة بقية الموظفين."
        override val displayName = "اسمك"
        override val createOwner = "إنشاء حساب المالك"
        override val creating = "جارٍ الإنشاء…"

        override val problemUsernameTooShort = "٣ أحرف على الأقل."
        override val problemUsernameTaken = "اسم المستخدم مستخدم بالفعل."
        override val problemPasswordTooShort = "٨ أحرف على الأقل."
        override val problemDisplayNameBlank = "أدخل اسمًا."
    }

    override val staff = object : AppStrings.Staff {
        override val title = "الموظفون"
        override val subtitle = "من يمكنه تسجيل الدخول على هذا الجهاز، وما يستطيع فعله."
        override val addStaff = "إضافة موظف"
        override val you = "أنت"
        override val disabled = "مُعطّل"
        override val enable = "تشغيل"
        override val disable = "تعطيل"
        override val resetPassword = "تغيير كلمة المرور"
        override fun resetPasswordFor(name: String) = "تغيير كلمة المرور لـ $name"
        override val newPassword = "كلمة المرور الجديدة"
        override val save = "حفظ"
        override val saving = "جارٍ الحفظ…"
        override val creating = "جارٍ الإنشاء…"
        override val emptyTitle = "لا يوجد موظفون آخرون"
        override val emptyMessage = "أضف العاملين في الصالة ليتمكنوا من تسجيل الدخول."
        override val loadFailedTitle = "تعذّر تحميل الموظفين"
        override fun showingStaff(count: Int) = when (arabicPlural(count)) {
            PluralCategory.ZERO -> "لا توجد حسابات"
            PluralCategory.ONE -> "حساب واحد"
            PluralCategory.TWO -> "حسابان"
            PluralCategory.FEW -> "$count حسابات"
            PluralCategory.MANY -> "$count حسابًا"
            PluralCategory.OTHER -> "$count حساب"
        }

        override val columnName = "الاسم"
        override val columnRoles = "الأدوار"
        override val columnActions = "إجراءات"

        override fun created(name: String) = "يمكن لـ $name تسجيل الدخول الآن."
        override val passwordReset = "تم تغيير كلمة المرور."
        override val accountEnabled = "تم تشغيل الحساب."
        override val accountDisabled = "تم تعطيل الحساب."
        override val wouldLockOutDevice =
            "هذا هو الحساب الوحيد الذي يمكنه إدارة الموظفين. أضف حسابًا آخر أولًا، " +
                "وإلا لن يتمكن أحد من إعادة تشغيله."
        override val accountGone = "هذا الحساب غير متوفر."

        override val roleOwner = "المالك"
        override val roleAdmin = "مسؤول"
        override val roleTherapist = "أخصائي علاج"
        override val roleCoach = "مدرب"
        override val roleReceptionist = "موظف استقبال"
        override val roleMember = "عضو"
    }

    override val dashboard = object : AppStrings.Dashboard {
        override val title = "اليوم"
        override val subtitle = "ما يجب عمله على المكتب."
        override val activeMembers = "الأعضاء النشطون"
        override fun ofTotal(total: Int) = "من ${total.toString().asLtrIsolate()}"
        override val needingRenewal = "بحاجة إلى تجديد"
        override val needingRenewalHint = "منتهية أو تنتهي خلال أسبوع"
        override val failedReminders = "تذكيرات فاشلة"
        override val failedRemindersHint = "لم تُسلَّم"
        override val nothingToChase = "لا شيء للمتابعة"

        override val renewalQueueTitle = "تجديدات للمتابعة"
        override val renewalQueueEmpty = "لا توجد عضويات تنتهي هذا الأسبوع."
        override val allClearTitle = "كل شيء تمام"
        override val allClearMessage = "لا عضويات تنتهي هذا الأسبوع ولا تذكيرات فاشلة."
        override val loadFailedTitle = "تعذّر تحميل أرقام اليوم"

        override val columnMember = "العضو"
        override val columnPlan = "الخطة"
        override val columnEnds = "تنتهي"
        override val expired = "منتهية"
        override fun inDays(days: Int) = when (days) {
            0 -> "اليوم"

            1 -> "غدًا"

            2 -> "بعد يومين"

            else -> when (arabicPlural(days)) {
                PluralCategory.FEW -> "بعد $days أيام"
                PluralCategory.MANY -> "بعد $days يومًا"
                else -> "بعد $days يوم"
            }
        }
        override fun turnedAwayHint(count: Int) = when (arabicPlural(count)) {
            PluralCategory.ONE -> "مُنع شخص واحد"
            PluralCategory.TWO -> "مُنع شخصان"
            PluralCategory.FEW -> "مُنع $count أشخاص"
            PluralCategory.MANY -> "مُنع $count شخصًا"
            else -> "مُنع $count شخص"
        }
    }

    override val classes = object : AppStrings.Classes {
        override val title = "الحصص"
        override val subtitle = "الجدول الأسبوعي."
        override val todayTitle = "جدول اليوم"
        override val weekTitle = "الجدول الأسبوعي"
        override val addClass = "إضافة حصة"
        override val editClass = "تعديل الحصة"
        override val deleteClass = "حذف الحصة"
        override val columnTime = "الوقت"
        override val columnClass = "الحصة"
        override val columnInstructor = "المدرب"
        override val columnRoom = "القاعة"
        override val columnCapacity = "السعة"
        override fun places(count: Int) = when (arabicPlural(count)) {
            PluralCategory.ZERO -> "لا أماكن"
            PluralCategory.ONE -> "مكان واحد"
            PluralCategory.TWO -> "مكانان"
            PluralCategory.FEW -> "$count أماكن"
            PluralCategory.MANY -> "$count مكانًا"
            PluralCategory.OTHER -> "$count مكان"
        }
        override val unassigned = "غير محدد"
        override val finishedToday = "انتهت اليوم"
        override val inProgress = "جارية الآن"
        override val categoryGeneral = "عام"
        override val categoryWomensOnly = "للنساء فقط"
        override val categoryRecovery = "استشفاء"
        override val emptyTitle = "لا حصص بعد"
        override val emptyMessage = "أضف الجدول الأسبوعي للصالة وسيظهر هنا."
        override fun emptyDayTitle(day: String) = "لا حصص $day"
        override val emptyDayMessage = "اختر يومًا آخر، أو انتقل إلى عرض الأسبوع."
        override val loadFailedTitle = "تعذّر تحميل الجدول"
        override fun weekRange(range: String) = range
        override val previousWeek = "الأسبوع السابق"
        override val nextWeek = "الأسبوع التالي"
        override val today = "اليوم"
        override val filterAllCoaches = "كل المدربين"
        override val filterAllRooms = "كل القاعات"
        override val fieldName = "اسم الحصة"
        override val fieldCategory = "التصنيف"
        override val fieldRoom = "القاعة"
        override val fieldCapacity = "السعة"
        override val fieldInstructor = "المدرب"
        override val fieldDay = "اليوم"
        override val fieldStart = "تبدأ"
        override val fieldDuration = "المدة"
        override fun durationMinutes(count: Int) = "$count دقيقة"
        override val save = "حفظ الحصة"
        override val errorNameBlank = "اكتب اسمًا للحصة."
        override val errorRoomBlank = "حدّد القاعة."
        override val errorCapacity = "السعة بين ١ و٥٠٠."
        override val errorDuration = "المدة بين ٥ دقائق و٨ ساعات."
        override fun roomClash(room: String, other: String) =
            "تم الحفظ، لكن $room محجوزة أيضًا لـ $other في نفس الوقت."
        override fun saved(name: String) = "تم حفظ $name."
        override fun deleted(name: String) = "تم حذف $name من الجدول."
    }

    override val announcements = object : AppStrings.Announcements {
        override val title = "الإعلانات"
        override val subtitle = "بلاغات عامة للصالة."
        override val newAnnouncement = "إعلان جديد"
        override val editAnnouncement = "تعديل الإعلان"
        override val fieldTitle = "عنوان الإعلان"
        override val fieldBody = "النص"
        override val fieldEventDate = "تاريخ الفعالية"
        override val fieldEventTime = "وقت الفعالية"
        override val audienceTitle = "الفئة المستهدفة"
        override val audienceAll = "كل الأعضاء"
        override val audienceActive = "النشطون فقط"
        override val audienceExpiring = "تنتهي عضويتهم هذا الشهر"
        override fun reaches(count: Int) = when (arabicPlural(count)) {
            PluralCategory.ZERO -> "لا يصل إلى أحد"
            PluralCategory.ONE -> "يصل إلى عضو واحد"
            PluralCategory.TWO -> "يصل إلى عضوين"
            PluralCategory.FEW -> "يصل إلى $count أعضاء"
            PluralCategory.MANY -> "يصل إلى $count عضوًا"
            PluralCategory.OTHER -> "يصل إلى $count عضو"
        }
        override val statusDraft = "مسودة"
        override val statusPublished = "منشور"
        override val saveDraft = "حفظ المسودة"
        override val saveChanges = "حفظ التغييرات"
        override val publish = "نشر"
        override val publishConfirmTitle = "نشر هذا الإعلان؟"
        override fun publishConfirmMessage(count: Int) = when (arabicPlural(count)) {
            PluralCategory.ONE ->
                "سيتم تثبيت الفئة المستهدفة عند عضو واحد. لا يمكن التراجع عن النشر."

            PluralCategory.TWO -> "سيتم تثبيت الفئة المستهدفة عند عضوين. لا يمكن التراجع عن النشر."

            else ->
                "سيتم تثبيت الفئة المستهدفة عند $count أعضاء. لا يمكن التراجع عن النشر."
        }
        override val deleteDraft = "حذف المسودة"
        override val deleteConfirmTitle = "حذف هذه المسودة؟"
        override val deleteConfirmMessage = "لا يمكن التراجع عن هذا الإجراء."
        override fun createdBy(name: String) = "بواسطة $name"
        override val createdByUnknown = "بواسطة حساب لم يعد موجودًا"
        override fun createdOn(date: String) = "أُنشئ في $date"
        override fun publishedOn(date: String) = "نُشر في $date"
        override fun reachedAtPublish(count: Int) = when (arabicPlural(count)) {
            PluralCategory.ONE -> "وصل إلى عضو واحد"
            PluralCategory.TWO -> "وصل إلى عضوين"
            PluralCategory.FEW -> "وصل إلى $count أعضاء"
            PluralCategory.MANY -> "وصل إلى $count عضوًا"
            else -> "وصل إلى $count عضو"
        }
        override val emptyTitle = "لا توجد إعلانات بعد"
        override val emptyMessage = "اكتب إعلانًا لبدء سجل بما أُبلغ الأعضاء به."
        override val loadFailedTitle = "تعذّر تحميل الإعلانات"
        override val draftSaved = "تم حفظ المسودة."
        override val published = "تم نشر الإعلان."
        override val deleted = "تم حذف المسودة."
        override val errorTitleBlank = "اكتب عنوانًا للإعلان."
        override val errorBodyBlank = "اكتب نص الإعلان."
        override val errorEventDateUnreadable =
            "تعذّرت قراءة هذا التاريخ. جرّب \"1 نوفمبر 2026\" أو \"2026-11-01\"."
    }

    override val therapy = object : AppStrings.Therapy {
        override val title = "الاستشفاء"
        override val subtitle = "ملف العلاج الطبيعي."
        override val restrictedBanner = "سجل مقيّد — للمعالج ومالك الصالة فقط"
        override val statusActive = "نشطة"
        override val statusClosed = "مغلقة"
        override val openCase = "فتح حالة"
        override val editCase = "تعديل الحالة"
        override val closeCase = "إغلاق الحالة"
        override val save = "حفظ"
        override val closeCaseConfirmTitle = "إغلاق هذه الحالة؟"
        override val closeCaseConfirmMessage =
            "يبقى السجل محفوظًا. يمكنك فتح حالة جديدة لهذا العضو لاحقًا."
        override val fieldCondition = "الحالة المرضية"
        override val fieldReferredBy = "محوّل من"
        override val fieldTherapist = "المعالج"
        override val fieldOnset = "بداية الإصابة"
        override val fieldMechanism = "آلية الإصابة"
        override val fieldContraindications = "موانع الاستعمال"
        override val contraindicationsTitle = "موانع الاستعمال"
        override val intakeTitle = "التقييم الأولي"
        override val unassignedTherapist = "غير محدد"
        override fun therapistPrefix(name: String) = "المعالج: $name"
        override fun referredByPrefix(name: String) = "محوّل من $name"
        override fun caseOpenedOn(date: String) = "فُتحت الحالة في $date"
        override val sessionsTitle = "الجلسات"
        override val noSessionsYet = "لا توجد جلسات مسجَّلة بعد."
        override val logSession = "تسجيل جلسة"
        override val fieldDate = "التاريخ"
        override val fieldDuration = "المدة"
        override fun durationMinutes(count: Int) = "$count دقيقة"
        override val fieldTreatmentTypes = "العلاج"
        override val fieldNotes = "ملاحظات"
        override val fieldPainScore = "مستوى الألم"
        override val painScoreNotRecorded = "غير مسجَّل"
        override val sessionToday = "اليوم"
        override val sessionYesterday = "أمس"
        override fun sessionDaysAgo(days: Int) = when (arabicPlural(days)) {
            PluralCategory.TWO -> "قبل يومين"
            PluralCategory.FEW -> "قبل $days أيام"
            else -> "قبل $days يومًا"
        }
        override val treatmentManualTherapy = "علاج يدوي"
        override val treatmentExercise = "تمارين"
        override val treatmentDryNeedling = "الإبر الجافة"
        override val treatmentUltrasound = "الموجات فوق الصوتية"
        override val progressTitle = "التقدّم"
        override val painScoreLabel = "مستوى الألم"
        override fun painScoreTrend(first: Int, latest: Int) = "من $first إلى $latest"
        override val notEnoughDataForProgress = "سجّل جلستين على الأقل بمستوى ألم لعرض المؤشر."
        override val emptyTitle = "لا توجد حالة مسجَّلة"
        override val emptyMessage = "افتح حالة لبدء سجل علاج طبيعي لهذا العضو."
        override val closedCaseMessage = "هذه الحالة مغلقة. افتح حالة جديدة إذا استؤنف العلاج."
        override val loadFailedTitle = "تعذّر تحميل الحالة"
        override fun caseOpened(condition: String) = "تم فتح حالة: $condition"
        override val caseClosed = "تم إغلاق الحالة."
        override val sessionLogged = "تم تسجيل الجلسة."
        override val errorConditionBlank = "اذكر ما الذي يتم علاجه."
        override val alreadyOpenMessage = "لهذا العضو حالة مفتوحة بالفعل."
        override val errorDuration = "المدة بين ٥ دقائق و٤ ساعات."
        override val errorPainScore = "مستوى الألم بين ٠ و١٠."
    }

    override val checkIn = object : AppStrings.CheckIn {
        override val title = "الحضور"
        override val subtitle = "ابحث عن العضو واسمح له بالدخول."
        override val searchPlaceholder = "ابحث بالاسم أو الرقم"
        override val searchPrompt = "ابدأ بكتابة اسم أو رقم عضوية."
        override val action = "تسجيل الحضور"
        override val recording = "جارٍ التسجيل…"

        override val totalToday = "دخلوا اليوم"
        override val deniedToday = "مُنعوا"
        override val peakHour = "أكثر ساعة"
        override fun hourLabel(hour: Int) =
            (hour.toString().padStart(2, '0') + ":00").asLtrIsolate()
        override val noPeakYet = "لا أحد بعد"

        override val logTitle = "حضور اليوم"
        override val logEmpty = "لم يحضر أحد اليوم."
        override val loadFailedTitle = "تعذّر تحميل سجل الحضور"
        override fun noMatches(query: String) = "لا يوجد عضو يطابق «$query»."

        override val granted = "دخل"
        override val outcomeExpired = "العضوية منتهية"
        override val outcomeSuspended = "موقوف"
        override val outcomePaused = "متوقف مؤقتًا"
        override val outcomeNoMembership = "لا توجد خطة بعد"

        override fun grantedNotice(name: String) = "دخل $name."
        override fun deniedNotice(name: String) = "تم منع $name."
    }
}

/** ISO order: index 0 is Monday. Egyptian usage. */
private val ARABIC_DAYS = listOf(
    "الاثنين",
    "الثلاثاء",
    "الأربعاء",
    "الخميس",
    "الجمعة",
    "السبت",
    "الأحد",
)

/**
 * The same names without the definite article, for a grid column header where the full form does
 * not fit. Not a truncation: cutting an Arabic word mid-ligature produces something unreadable.
 */
private val ARABIC_DAYS_SHORT = listOf(
    "اثنين",
    "ثلاثاء",
    "أربعاء",
    "خميس",
    "جمعة",
    "سبت",
    "أحد",
)
