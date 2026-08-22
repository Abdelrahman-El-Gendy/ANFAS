package com.anfas.core.data

import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.SessionStore
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the session in platform key-value storage: SharedPreferences, NSUserDefaults or JVM
 * Preferences, the same backing store the language choice already uses.
 *
 * **No secret is stored here.** The value is a staff id and a role list — enough to restore who
 * was signed in, and useless on its own, because signing in requires the password hash that lives
 * in the database. That is deliberate: `Settings` is not encrypted on any of these platforms, and
 * a stored token would be readable on a rooted device.
 *
 * Reactive over a StateFlow rather than `Settings`' own listeners: those are only available on
 * some platforms, and a signed-out app has to reach the login screen on every one of them.
 */
internal class SettingsSessionStore(private val settings: Settings) : SessionStore {

    private val state = MutableStateFlow(read())

    override fun observe(): Flow<Session?> = state.asStateFlow()

    override suspend fun current(): Session? = state.value

    override suspend fun save(session: Session) {
        settings.putString(KEY_USER_ID, session.userId)
        settings.putString(KEY_ROLES, session.roles.joinToString(",") { it.name })
        state.value = session
    }

    override suspend fun clear() {
        settings.remove(KEY_USER_ID)
        settings.remove(KEY_ROLES)
        state.value = null
    }

    private fun read(): Session? {
        val userId = settings.getStringOrNull(KEY_USER_ID) ?: return null
        val roles = settings.getStringOrNull(KEY_ROLES)
            .orEmpty()
            .split(',')
            .mapNotNull { name -> Role.entries.firstOrNull { it.name == name.trim() } }
            .toSet()
        // A session with no resolvable role can do nothing, so treat it as absent rather than
        // restoring a signed-in user who is denied everywhere. This also self-heals if a Role
        // enum entry is ever renamed.
        return if (roles.isEmpty()) null else Session(userId = userId, roles = roles)
    }

    private companion object {
        const val KEY_USER_ID = "session.user_id"
        const val KEY_ROLES = "session.roles"
    }
}
