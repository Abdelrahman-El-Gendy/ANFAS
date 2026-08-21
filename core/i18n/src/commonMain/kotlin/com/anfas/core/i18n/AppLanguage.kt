package com.anfas.core.i18n

/**
 * The languages the app ships in.
 *
 * [isRtl] belongs on the language rather than being derived at the call site, because layout
 * direction and string choice must never disagree — they come from one value.
 */
enum class AppLanguage(val tag: String, val isRtl: Boolean, val endonym: String) {
    EN("en", isRtl = false, endonym = "EN"),
    AR("ar", isRtl = true, endonym = "ع"),
    ;

    companion object {
        /** Matches a platform locale tag like "ar-EG" or "en_US"; falls back to English. */
        fun fromTag(tag: String?): AppLanguage {
            val prefix = tag?.take(2)?.lowercase()
            return entries.firstOrNull { it.tag == prefix } ?: EN
        }
    }
}
