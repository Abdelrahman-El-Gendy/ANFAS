package com.anfas.core.common

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformSettingsModule(): Module = module {
    single<Settings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("anfas", Context.MODE_PRIVATE),
        )
    }
}
