package com.anfas.core.common

import org.koin.core.module.Module

/**
 * Key-value storage, registered once for the whole app.
 *
 * It lives here rather than in `:core:i18n` — which owned it first, for the language choice —
 * because `:core:data` needs the same store for the current session. Two modules each registering
 * `single<Settings>` is a Koin duplicate-definition error, and giving them separate qualified
 * instances would mean two wrappers over the same backing store for no reason. `:core:common` is
 * where app infrastructure already lives, next to [AppDispatchers] and logging.
 *
 * Explicit per-platform rather than multiplatform-settings-no-arg: that artifact obtains its
 * Android Context by registering a hidden ContentProvider, and this keeps the `androidContext()`
 * requirement visible in the graph.
 */
expect fun platformSettingsModule(): Module
