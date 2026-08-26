package com.anfas.core.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import java.io.File

actual class DatabaseBuilderFactory {
    actual fun create(): RoomDatabase.Builder<AnfasDatabase> {
        val dir = resolveDesktopDataDir()
        val legacy = legacyDesktopDataDir()
        // Best-effort: an install that predates the per-OS location keeps its data.
        adoptLegacyDatabase(from = legacy, to = dir)
        return Room.databaseBuilder<AnfasDatabase>(
            name = File(dir, AnfasDatabase.FILE_NAME).absolutePath,
        )
    }
}

/**
 * OS-idiomatic data location, mirroring `AppLog.desktopLogFile` one module over — same injectable
 * parameters for the same reason, so the branching is unit-testable rather than discovered on a
 * user's machine.
 *
 * This used to be `~/.anfas` on **every** OS, which is wrong on two of the three: macOS has a
 * standard Application Support directory that Migration Assistant and backup tools understand, and
 * a dot-directory in a Windows profile can end up synced to OneDrive — which for this app means a
 * SQLite file with `-wal` companions being copied out from under an open connection.
 *
 * Note this is *data*, so macOS gets `Application Support` (not `Library/Logs`, which is where the
 * log goes) and Linux gets `XDG_DATA_HOME` (not `XDG_STATE_HOME`).
 */
internal fun resolveDesktopDataDir(
    osName: String = System.getProperty("os.name").orEmpty(),
    userHome: String = System.getProperty("user.home").orEmpty(),
    localAppData: String? = System.getenv("LOCALAPPDATA"),
    xdgDataHome: String? = System.getenv("XDG_DATA_HOME"),
): File {
    val os = osName.lowercase()
    val dir = when {
        os.contains("mac") || os.contains("darwin") ->
            File(userHome, "Library/Application Support/ANFAS")

        os.contains("win") ->
            File(localAppData ?: File(userHome, "AppData/Local").path, "ANFAS")

        else ->
            File(xdgDataHome ?: File(userHome, ".local/share").path, "anfas")
    }
    dir.mkdirs()
    return dir
}

/** Where installs before the per-OS move kept their database. */
internal fun legacyDesktopDataDir(userHome: String = System.getProperty("user.home").orEmpty()) =
    File(userHome, ".anfas")

/**
 * Moves a pre-existing database to the new location, conservatively.
 *
 * Deliberately timid, because the failure modes are all worse than doing nothing:
 *  - **Never overwrites.** If [to] already holds a database, the legacy one is left untouched
 *    rather than clobbering data that is by definition newer.
 *  - **Moves the `-wal` and `-shm` companions as a set.** A `.db` separated from its write-ahead
 *    log is a database missing its most recent commits.
 *  - **Never throws.** On any failure the legacy files are left exactly where they were, and the
 *    caller carries on — a permissions problem must not be a failure to start.
 *
 * Returns true only when a database was actually adopted.
 */
internal fun adoptLegacyDatabase(from: File, to: File): Boolean = runCatching {
    if (from.canonicalFile == to.canonicalFile) return false

    val target = File(to, AnfasDatabase.FILE_NAME)
    if (target.exists()) return false

    val source = File(from, AnfasDatabase.FILE_NAME)
    if (!source.isFile) return false

    to.mkdirs()
    // The main file last: if the move is interrupted, the legacy directory still has a `.db`
    // and is still the one that looks authoritative on the next start.
    val companions = listOf("-wal", "-shm").mapNotNull { suffix ->
        File(from, AnfasDatabase.FILE_NAME + suffix).takeIf { it.isFile }
    }
    companions.forEach { companion ->
        companion.renameTo(File(to, companion.name))
    }
    source.renameTo(target)
}.getOrDefault(false)
