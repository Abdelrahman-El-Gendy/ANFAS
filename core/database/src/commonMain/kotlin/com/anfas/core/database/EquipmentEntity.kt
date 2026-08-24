package com.anfas.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A physical asset on the gym floor. No foreign key anywhere in this table — it names no staff,
 * only a status and a zone.
 */
@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "asset_tag") val assetTag: String,
    /** `EquipmentStatus` name. */
    val status: String,
    /** `EquipmentZone` name. */
    val zone: String,
    val manufacturer: String?,
    @ColumnInfo(name = "serial_number") val serialNumber: String?,
    @ColumnInfo(name = "purchased_on_epoch_day") val purchasedOnEpochDay: Long?,
    @ColumnInfo(name = "warranty_until_epoch_day") val warrantyUntilEpochDay: Long?,
)

/**
 * One logged event against a piece of equipment. CASCADEs from its equipment, the same
 * reasoning as `therapy_sessions` from `therapy_cases`: a maintenance history with no equipment
 * left to attach to is not a record worth keeping.
 */
@Entity(
    tableName = "maintenance_log",
    foreignKeys = [
        ForeignKey(
            entity = EquipmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["equipment_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["equipment_id"])],
)
data class MaintenanceLogEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "equipment_id") val equipmentId: String,
    @ColumnInfo(name = "occurred_at_epoch_ms") val occurredAtEpochMs: Long,
    val summary: String,
    val details: String,
    @ColumnInfo(name = "reported_by_staff_name") val reportedByStaffName: String?,
    val technician: String?,
    @ColumnInfo(name = "cost_minor_units") val costMinorUnits: Long?,
    @ColumnInfo(name = "parts_used") val partsUsed: String?,
)

@Dao
interface EquipmentDao {

    @Query("SELECT * FROM equipment ORDER BY name")
    fun observeAll(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<EquipmentEntity?>

    @Query("SELECT * FROM equipment WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): EquipmentEntity?

    @Query("SELECT * FROM equipment WHERE asset_tag = :assetTag LIMIT 1")
    suspend fun findByAssetTag(assetTag: String): EquipmentEntity?

    @Upsert
    suspend fun upsert(equipment: EquipmentEntity)

    @Query(
        "SELECT * FROM maintenance_log WHERE equipment_id = :equipmentId " +
            "ORDER BY occurred_at_epoch_ms DESC",
    )
    fun observeLog(equipmentId: String): Flow<List<MaintenanceLogEntryEntity>>

    @Upsert
    suspend fun insertLogEntry(entry: MaintenanceLogEntryEntity)
}
