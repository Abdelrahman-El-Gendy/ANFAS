package com.anfas.core.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase

actual class DatabaseBuilderFactory(private val context: Context) {
    actual fun create(): RoomDatabase.Builder<AnfasDatabase> {
        val dbFile = context.getDatabasePath(AnfasDatabase.FILE_NAME)
        return Room.databaseBuilder<AnfasDatabase>(
            context = context.applicationContext,
            name = dbFile.absolutePath,
        )
    }
}
