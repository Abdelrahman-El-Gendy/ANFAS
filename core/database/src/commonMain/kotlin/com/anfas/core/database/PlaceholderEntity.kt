package com.anfas.core.database

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query

/**
 * ONE placeholder entity, present only to prove the KSP wiring generates code on every
 * target. The real schema is a separate task — do not model domain tables here.
 */
@Entity(tableName = "placeholder")
data class PlaceholderEntity(
    @PrimaryKey val id: Long,
    val label: String,
)

@Dao
interface PlaceholderDao {
    @Insert
    suspend fun insert(entity: PlaceholderEntity)

    @Query("SELECT * FROM placeholder")
    suspend fun getAll(): List<PlaceholderEntity>
}
