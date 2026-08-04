package com.anfas.core.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual class DatabaseBuilderFactory {
    @OptIn(ExperimentalForeignApi::class)
    actual fun create(): RoomDatabase.Builder<AnfasDatabase> =
        Room.databaseBuilder<AnfasDatabase>(
            name = "${documentDirectory()}/${AnfasDatabase.FILE_NAME}",
        )

    @OptIn(ExperimentalForeignApi::class)
    private fun documentDirectory(): String {
        val url: NSURL? = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        return requireNotNull(url?.path) { "Could not resolve NSDocumentDirectory" }
    }
}
