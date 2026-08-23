package com.anfas.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A staff login, stored locally. Flat and primitive like every other entity here — roles are a
 * comma-separated list of enum names rather than a relation, because the set is tiny, fixed and
 * always read whole.
 *
 * [passwordSalt] and [passwordHash] are raw bytes; the plaintext password is never stored,
 * transmitted or logged. [passwordIterations] and [passwordAlgorithm] travel with the hash so the
 * cost can be raised later without locking existing accounts out.
 *
 * The unique index on [username] is what makes "username taken" a database guarantee rather than
 * only a validation rule — two devices could otherwise both create "fahd" before any sync.
 */
@Entity(
    tableName = "staff",
    indices = [Index(value = ["username"], unique = true)],
)
data class StaffEntity(
    @PrimaryKey val id: String,
    /** Already normalised (trimmed, lowercased) by the mapper. */
    val username: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    /** Comma-separated `Role` names. */
    val roles: String,
    @ColumnInfo(name = "password_algorithm") val passwordAlgorithm: String,
    @ColumnInfo(name = "password_iterations") val passwordIterations: Int,
    @ColumnInfo(name = "password_salt") val passwordSalt: ByteArray,
    @ColumnInfo(name = "password_hash") val passwordHash: ByteArray,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
) {
    // ByteArray uses identity equality, so the generated data-class equals would report two
    // identical rows as different. Room does not need this, but tests compare entities.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StaffEntity) return false
        return id == other.id &&
            username == other.username &&
            displayName == other.displayName &&
            roles == other.roles &&
            passwordAlgorithm == other.passwordAlgorithm &&
            passwordIterations == other.passwordIterations &&
            passwordSalt.contentEquals(other.passwordSalt) &&
            passwordHash.contentEquals(other.passwordHash) &&
            createdAtEpochMs == other.createdAtEpochMs &&
            isEnabled == other.isEnabled
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + roles.hashCode()
        result = 31 * result + passwordAlgorithm.hashCode()
        result = 31 * result + passwordIterations
        result = 31 * result + passwordSalt.contentHashCode()
        result = 31 * result + passwordHash.contentHashCode()
        result = 31 * result + createdAtEpochMs.hashCode()
        result = 31 * result + isEnabled.hashCode()
        return result
    }
}

@Dao
interface StaffDao {
    /**
     * Looked up by normalised username. Returns the row even when disabled, so the caller can
     * tell "wrong password" from "account switched off" — a distinction staff need and an
     * attacker does not learn, because sign-in reports both as invalid credentials.
     */
    @Query("SELECT * FROM staff WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): StaffEntity?

    @Query("SELECT * FROM staff WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): StaffEntity?

    /**
     * Observed rather than read once, so a rename or a role change by the Owner reaches the
     * signed-in person's own chrome without them signing out and back in.
     */
    @Query("SELECT * FROM staff WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<StaffEntity?>

    /** Drives the first-run decision: no rows means offer setup, not a login nobody can pass. */
    @Query("SELECT COUNT(*) FROM staff")
    suspend fun count(): Int

    @Query("SELECT username FROM staff")
    suspend fun allUsernames(): List<String>

    @Query("SELECT * FROM staff ORDER BY display_name COLLATE NOCASE")
    fun observeAll(): Flow<List<StaffEntity>>

    @Upsert
    suspend fun upsert(staff: StaffEntity)

    @Query("DELETE FROM staff WHERE id = :id")
    suspend fun delete(id: String)
}
