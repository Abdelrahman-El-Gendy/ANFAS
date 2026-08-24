package com.anfas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class MaintenanceLogTest {

    @Test
    fun `no entries means no last service`() {
        assertNull(MaintenanceLog.lastServiceOn(emptyList()))
    }

    @Test
    fun `a report with no technician is not a service`() {
        val entries = listOf(reportAt("2026-05-01T09:00:00Z"))
        assertNull(MaintenanceLog.lastServiceOn(entries))
    }

    @Test
    fun `the most recent serviced entry wins`() {
        val entries = listOf(
            servicedAt("2025-11-14T10:00:00Z"),
            servicedAt("2026-05-03T10:00:00Z"),
        )
        assertEquals(
            Instant.parse("2026-05-03T10:00:00Z"),
            MaintenanceLog.lastServiceOn(entries),
        )
    }

    /**
     * The bug this pins: a plain issue report logged after the last real service must not push
     * "Last service" forward. Only [MaintenanceLogEntry.technician] counts.
     */
    @Test
    fun `a later report does not override an earlier service`() {
        val entries = listOf(
            servicedAt("2026-05-03T10:00:00Z"),
            reportAt("2026-08-24T09:30:00Z"),
        )
        assertEquals(
            Instant.parse("2026-05-03T10:00:00Z"),
            MaintenanceLog.lastServiceOn(entries),
        )
    }

    private fun servicedAt(instant: String) = entry(instant, technician = "Omar")

    private fun reportAt(instant: String) = entry(instant, technician = null)

    private fun entry(instant: String, technician: String?) = MaintenanceLogEntry(
        id = MaintenanceLogId("log-1"),
        equipmentId = EquipmentId("eq-1"),
        occurredAt = Instant.parse(instant),
        summary = "Summary",
        details = "Details",
        reportedByStaffName = if (technician == null) "Alex" else null,
        technician = technician,
        cost = null,
        partsUsed = null,
    )
}
