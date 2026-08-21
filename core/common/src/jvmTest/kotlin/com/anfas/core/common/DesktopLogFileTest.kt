package com.anfas.core.common

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The path resolution is pure and branchy, so it is worth testing per OS rather than discovering
 * on a user's machine that the log went somewhere unexpected.
 */
class DesktopLogFileTest {

    @Test
    fun `macOS uses the standard Library Logs directory`() {
        val f = desktopLogFile(osName = "Mac OS X", userHome = "/Users/x")
        assertTrue(f.path.startsWith("/Users/x/Library/Logs/ANFAS"), f.path)
    }

    @Test
    fun `windows uses LOCALAPPDATA when it is set`() {
        val f = desktopLogFile(
            osName = "Windows 11",
            userHome = "C:\\Users\\x",
            localAppData = "C:\\Users\\x\\AppData\\Local",
        )
        assertTrue(f.path.contains("AppData"), f.path)
        assertTrue(f.path.contains("ANFAS"), f.path)
    }

    @Test
    fun `windows falls back when LOCALAPPDATA is absent`() {
        val f = desktopLogFile(osName = "Windows 11", userHome = "/home/x", localAppData = null)
        assertTrue(f.path.contains("AppData"), f.path)
    }

    @Test
    fun `linux honours XDG_STATE_HOME and falls back to local state`() {
        val withXdg = desktopLogFile(
            osName = "Linux",
            userHome = "/home/x",
            xdgStateHome = "/home/x/.custom",
        )
        assertTrue(withXdg.path.startsWith("/home/x/.custom/anfas"), withXdg.path)

        val fallback = desktopLogFile(osName = "Linux", userHome = "/home/x", xdgStateHome = null)
        assertTrue(fallback.path.startsWith("/home/x/.local/state/anfas"), fallback.path)
    }

    @Test
    fun `the file is always named anfas log`() {
        assertTrue(desktopLogFile(osName = "Linux", userHome = "/home/x").name == "anfas.log")
    }
}
