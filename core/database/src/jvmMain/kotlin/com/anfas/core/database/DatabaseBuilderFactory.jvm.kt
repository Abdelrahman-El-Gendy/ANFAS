package com.anfas.core.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import java.io.File

actual class DatabaseBuilderFactory {
    actual fun create(): RoomDatabase.Builder<AnfasDatabase> {
        val dir = File(System.getProperty("user.home"), ".anfas").apply { mkdirs() }
        return Room.databaseBuilder<AnfasDatabase>(
            name = File(dir, AnfasDatabase.FILE_NAME).absolutePath,
        )
    }
}
