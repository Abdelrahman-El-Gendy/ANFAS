package com.anfas.core.model

import kotlin.jvm.JvmInline

/**
 * Typed identifiers. One value class per aggregate root, so a MemberId can never be passed
 * where some other id is expected. Domain types themselves live in their own files
 * ([Member], ...) — keep this file to ids only.
 */
@JvmInline
value class MemberId(val value: String)

@JvmInline
value class ReminderId(val value: String)

@JvmInline
value class PlanId(val value: String)

@JvmInline
value class SubscriptionId(val value: String)

@JvmInline
value class IntakeBatchId(val value: String)

@JvmInline
value class IntakeRowId(val value: String)

@JvmInline
value class CheckInId(val value: String)
