package com.anfas.core.i18n

import java.util.Locale

internal actual fun deviceLanguageTag(): String? = Locale.getDefault().language
