package com.brickkiln.erp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A production entry saved locally on the mobile device.
 *
 * Status flow:
 *   - PENDING: created offline, waiting for network
 *   - SYNCED: successfully uploaded to the desktop ERP server
 *   - FAILED: server rejected (e.g., worker not found) — needs user action
 *
 * The `clientUuid` is generated once on creation and used as the
 * idempotency key when POSTing to the server. If the same entry is
 * uploaded twice (e.g., network glitch + retry), the server ignores
 * the duplicate.
 */
@Entity(tableName = "production_entries")
data class ProductionEntryEntity(
    @PrimaryKey val clientUuid: String,
    val stage: String,                  // raw_brick_making | raw_brick_transport | baked_brick_unloading
    val workerId: String,
    val workTypeId: String?,
    val quantity: Double,
    val ratePer1000: Double,
    val labourAmount: Double,
    val batchId: String?,
    val kilnId: String?,
    val categoryId: String?,           // for baked_brick_unloading
    val date: String,                  // YYYY-MM-DD
    val notes: String?,
    val enteredBy: String,             // user ID
    val createdAt: Long,              // local creation timestamp
    val status: String = "PENDING",   // PENDING | SYNCED | FAILED
    val syncError: String? = null,
    val syncedAt: Long? = null,
)
