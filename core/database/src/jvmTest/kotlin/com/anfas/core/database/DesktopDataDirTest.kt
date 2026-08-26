package com.anfas.core.database

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The path resolution is pure and branchy, and the migration touches user data — both are worth
 * testing here rather than discovering on someone's machine that the gym's member database went
 * somewhere unexpected, or worse, was half-moved.
 */
class DesktopDataDirTest {

    @Test
    fun `macOS uses Application Support, not Library Logs`() {
        val dir = resolveDesktopDataDir(osName = "Mac OS X", userHome = tempHome())
        assertTrue(dir.path.endsWith("Library/Application Support/ANFAS"), dir.path)
    }

    @Test
    fun `windows uses LOCALAPPDATA when set, and falls back when not`() {
        val home = tempHome()
        val withVar = resolveDesktopDataDir(
            osName = "Windows 11",
            userHome = home,
            localAppData = File(home, "AppData/Local").path,
        )
        assertTrue(withVar.path.contains("AppData"), withVar.path)
        assertTrue(withVar.path.endsWith("ANFAS"), withVar.path)

        val fallback =
            resolveDesktopDataDir(osName = "Windows 11", userHome = home, localAppData = null)
        assertTrue(fallback.path.contains("AppData"), fallback.path)
    }

    @Test
    fun `linux honours XDG_DATA_HOME and falls back to local share`() {
        val home = tempHome()
        val withXdg = resolveDesktopDataDir(
            osName = "Linux",
            userHome = home,
            xdgDataHome = File(home, ".custom").path,
        )
        assertTrue(withXdg.path.endsWith(".custom/anfas"), withXdg.path)

        val fallback = resolveDesktopDataDir(osName = "Linux", userHome = home, xdgDataHome = null)
        assertTrue(fallback.path.endsWith(".local/share/anfas"), fallback.path)
    }

    @Test
    fun `no OS branch resolves to the old dot-anfas directory`() {
        val home = tempHome()
        listOf("Mac OS X", "Windows 11", "Linux").forEach { os ->
            val dir = resolveDesktopDataDir(osName = os, userHome = home)
            assertFalse(
                dir.canonicalFile == File(home, ".anfas").canonicalFile,
                "$os still resolves to the legacy ~/.anfas",
            )
        }
    }

    // --- migration -------------------------------------------------------------------------

    @Test
    fun `a legacy database is adopted together with its wal and shm`() {
        val legacy = tempDir("legacy")
        val target = tempDir("target")
        seedDatabase(legacy, main = "main", wal = "wal", shm = "shm")

        assertTrue(adoptLegacyDatabase(from = legacy, to = target))

        assertEquals("main", File(target, AnfasDatabase.FILE_NAME).readText())
        assertEquals("wal", File(target, AnfasDatabase.FILE_NAME + "-wal").readText())
        assertEquals("shm", File(target, AnfasDatabase.FILE_NAME + "-shm").readText())
        assertFalse(File(legacy, AnfasDatabase.FILE_NAME).exists(), "legacy .db was left behind")
    }

    /** The important one: never clobber data that is by definition newer. */
    @Test
    fun `an existing database is never overwritten`() {
        val legacy = tempDir("legacy")
        val target = tempDir("target")
        seedDatabase(legacy, main = "old")
        File(target, AnfasDatabase.FILE_NAME).writeText("current")

        assertFalse(adoptLegacyDatabase(from = legacy, to = target))

        assertEquals("current", File(target, AnfasDatabase.FILE_NAME).readText())
        assertEquals("old", File(legacy, AnfasDatabase.FILE_NAME).readText(), "legacy was moved")
    }

    @Test
    fun `nothing to adopt is not a failure`() {
        assertFalse(adoptLegacyDatabase(from = tempDir("empty"), to = tempDir("target")))
    }

    @Test
    fun `adopting into the same directory is a no-op`() {
        val dir = tempDir("same")
        seedDatabase(dir, main = "main")
        assertFalse(adoptLegacyDatabase(from = dir, to = dir))
        assertEquals("main", File(dir, AnfasDatabase.FILE_NAME).readText())
    }

    /** A missing legacy directory must not throw — startup has to survive it. */
    @Test
    fun `a nonexistent source directory is handled quietly`() {
        val missing = File(tempDir("gone"), "not-there")
        assertFalse(adoptLegacyDatabase(from = missing, to = tempDir("target")))
    }

    private fun seedDatabase(dir: File, main: String, wal: String? = null, shm: String? = null) {
        File(dir, AnfasDatabase.FILE_NAME).writeText(main)
        wal?.let { File(dir, AnfasDatabase.FILE_NAME + "-wal").writeText(it) }
        shm?.let { File(dir, AnfasDatabase.FILE_NAME + "-shm").writeText(it) }
    }

    private fun tempDir(name: String): File =
        Files.createTempDirectory("anfas-$name").toFile().also { it.deleteOnExit() }

    /** A real temp path, because [resolveDesktopDataDir] calls `mkdirs()`. */
    private fun tempHome(): String = tempDir("home").path
}
