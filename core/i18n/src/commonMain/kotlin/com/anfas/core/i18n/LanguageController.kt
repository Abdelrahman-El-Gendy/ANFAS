package com.anfas.core.i18n

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds and persists the chosen language.
 *
 * Initial value is the saved choice, falling back to the device language — so an Egyptian phone
 * set to Arabic opens in Arabic without anyone touching a setting.
 *
 * Known limitation, documented so nobody "fixes" it later: system-supplied UI (the soft keyboard,
 * the share sheet, permission dialogs, the camera app) follows the *device* language, not this
 * toggle. Aligning those means AppCompatDelegate.setApplicationLocales on Android and is
 * impossible in-session on iOS. Out of scope by design.
 */
class LanguageController(private val settings: Settings, deviceLanguageTag: String?) {
    private val _language = MutableStateFlow(
        AppLanguage.fromTag(settings.getStringOrNull(KEY) ?: deviceLanguageTag),
    )

    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun select(language: AppLanguage) {
        settings.putString(KEY, language.tag)
        _language.value = language
    }

    /** For the EN / ع toggle, which flips between exactly two languages. */
    fun toggle() = select(if (_language.value == AppLanguage.AR) AppLanguage.EN else AppLanguage.AR)

    private companion object {
        const val KEY = "app.language"
    }
}
