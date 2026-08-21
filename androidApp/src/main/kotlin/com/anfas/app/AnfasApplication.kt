package com.anfas.app

import android.app.Application
import android.content.pm.ApplicationInfo
import com.anfas.app.di.initKoin
import com.anfas.core.common.configureLogging
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class AnfasApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Gated on the debuggable flag rather than BuildConfig.DEBUG: AGP 9 has buildConfig off
        // by default and enabling it for one boolean is not worth it.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        configureLogging(verbose = debuggable)
        installCrashHandler()

        initKoin {
            androidLogger()
            androidContext(this@AnfasApplication)
        }
    }
}
