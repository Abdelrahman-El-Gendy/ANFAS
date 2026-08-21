package com.anfas.core.i18n

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.Locale

internal actual fun platformI18nModule(): Module = module {
    single<com.russhwolf.settings.Settings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("anfas", Context.MODE_PRIVATE),
        )
    }
}

internal actual fun deviceLanguageTag(): String? = Locale.getDefault().language
