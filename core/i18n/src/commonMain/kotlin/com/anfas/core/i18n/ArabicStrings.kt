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
        override fun idPrefix(number: String) = "رقم العضوية: $number"
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
    }

    /** Egyptian Arabic month names, matching what IntakeValidator parses off the sheets. */
    internal val MONTHS_AR = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )
}
