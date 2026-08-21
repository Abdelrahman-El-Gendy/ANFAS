package com.anfas.core.database.migrations

import androidx.room3.DeleteTable
import androidx.room3.migration.AutoMigrationSpec

/**
 * v4 -> v5: drops the `placeholder` table.
 *
 * That table existed only to prove the KSP wiring generated code on every target back when
 * :core:database had no real entities. It then shipped through three migrations unnoticed.
 *
 * Dropping a table is expressible as an auto-migration via @DeleteTable, so no hand-written SQL
 * is needed — but the spec has to be declared, because Room cannot infer that a table which
 * vanished from the entity list was meant to be dropped rather than renamed.
 */
@DeleteTable(tableName = "placeholder")
class DropPlaceholderTable : AutoMigrationSpec
