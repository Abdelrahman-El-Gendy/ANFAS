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
 * A purchasable plan. Prices live in the database rather than in code because they change,
 * and a price change must not require shipping an app build.
 */
@Entity(tableName = "subscription_plans")
data class SubscriptionPlanEntity(
    @PrimaryKey val id: String,
    val tier: String,
    @ColumnInfo(name = "price_minor_units") val priceMinorUnits: Long,
    val currency: String,
    val perks: String,
    @ColumnInfo(name = "savings_percent") val savingsPercent: Int?,
)

/**
 * A term a member has actually bought. Rows are append-only history, not a mutable "current
 * subscription" — the current one is simply the latest by [endsOnEpochDay], which is what lets
 * "renew when the current term ends" work and keeps an audit trail of what was charged.
 *
 * [paidMinorUnits] records the quoted total, so a later price change never rewrites history.
 */
@Entity(
    tableName = "subscriptions",
    indices = [Index(value = ["member_id", "ends_on_epoch_day"])],
)
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    val tier: String,
    @ColumnInfo(name = "starts_on_epoch_day") val startsOnEpochDay: Long,
    @ColumnInfo(name = "ends_on_epoch_day") val endsOnEpochDay: Long,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "paid_minor_units") val paidMinorUnits: Long,
    val currency: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscription_plans ORDER BY price_minor_units ASC")
    fun observePlans(): Flow<List<SubscriptionPlanEntity>>

    @Upsert
    suspend fun upsertPlans(plans: List<SubscriptionPlanEntity>)

    /** The member's latest term — the one "Current plan ends …" refers to. */
    @Query(
        """
        SELECT * FROM subscriptions
        WHERE member_id = :memberId
        ORDER BY ends_on_epoch_day DESC
        LIMIT 1
        """,
    )
    fun observeCurrent(memberId: String): Flow<SubscriptionEntity?>

    /**
     * The latest term for every member that has one, newest end date first.
     *
     * Grouped in SQL rather than by reading all terms and filtering in Kotlin: a gym with years
     * of renewal history has many rows per member, and the dashboard only ever wants the current
     * one. The correlated MAX picks it per member.
     *
     * The subquery alias is `latest`, not `inner` — `INNER` is a SQL keyword, and Room's query
     * verifier reads it as the start of an INNER JOIN.
     */
    @Query(
        """
        SELECT s.* FROM subscriptions s
        WHERE s.ends_on_epoch_day = (
            SELECT MAX(latest.ends_on_epoch_day) FROM subscriptions latest
            WHERE latest.member_id = s.member_id
        )
        ORDER BY s.ends_on_epoch_day ASC
        """,
    )
    fun observeAllCurrent(): Flow<List<SubscriptionEntity>>

    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity)
}
