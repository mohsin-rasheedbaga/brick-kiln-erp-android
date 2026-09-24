package com.brickkiln.erp.data.repository

import com.brickkiln.erp.data.local.AppDatabase
import com.brickkiln.erp.data.local.entity.ProductionEntryEntity
import com.brickkiln.erp.data.remote.ApiClient
import com.brickkiln.erp.data.remote.SubmitProductionResponse
import com.brickkiln.erp.data.remote.SyncStatusResponse
import java.util.UUID

class ProductionRepository(
    private val apiClient: ApiClient,
    private val database: AppDatabase,
) {

    /**
     * Create a production entry LOCALLY (offline-first).
     * The entry is saved to Room with status=PENDING.
     * The SyncWorker will pick it up and POST to the server when network is available.
     *
     * @return the local client UUID (used as the idempotency key on sync).
     */
    suspend fun createLocalEntry(
        stage: String,
        workerId: String,
        quantity: Double,
        ratePer1000: Double,
        workTypeId: String?,
        batchId: String? = null,
        kilnId: String? = null,
        categoryId: String? = null,
        date: String,
        notes: String? = null,
    ): String {
        val session = database.userSessionDao().get()
            ?: throw IllegalStateException("Not logged in.")

        val clientUuid = UUID.randomUUID().toString()
        val labour = (quantity / 1000.0) * ratePer1000

        val entry = ProductionEntryEntity(
            clientUuid = clientUuid,
            stage = stage,
            workerId = workerId,
            workTypeId = workTypeId,
            quantity = quantity,
            ratePer1000 = ratePer1000,
            labourAmount = labour,
            batchId = batchId,
            kilnId = kilnId,
            categoryId = categoryId,
            date = date,
            notes = notes,
            enteredBy = session.userId,
            createdAt = System.currentTimeMillis(),
            status = "PENDING",
        )
        database.productionEntryDao().insert(entry)
        return clientUuid
    }

    /**
     * Upload a single pending entry to the server.
     * Returns true on success (entry marked SYNCED), false on failure (entry marked FAILED).
     */
    suspend fun syncOne(entry: ProductionEntryEntity): Boolean {
        val session = database.userSessionDao().get() ?: return false
        val result = apiClient.callRpc<SubmitProductionResponse>(
            channel = "mobile:submit-production",
            args = mapOf(
                "token" to session.token,
                "stage" to entry.stage,
                "workerId" to entry.workerId,
                "quantity" to entry.quantity,
                "ratePer1000" to entry.ratePer1000,
                "workTypeId" to entry.workTypeId,
                "batchId" to entry.batchId,
                "kilnId" to entry.kilnId,
                "categoryId" to entry.categoryId,
                "date" to entry.date,
                "notes" to entry.notes,
                "clientUuid" to entry.clientUuid,
            ),
        )
        return if (result.isSuccess) {
            database.productionEntryDao().updateStatus(
                id = entry.clientUuid,
                status = "SYNCED",
                error = null,
                syncedAt = System.currentTimeMillis(),
            )
            true
        } else {
            database.productionEntryDao().updateStatus(
                id = entry.clientUuid,
                status = "FAILED",
                error = result.exceptionOrNull()?.message,
                syncedAt = null,
            )
            false
        }
    }

    /**
     * Get the count of pending (unsynced) entries.
     */
    suspend fun pendingCount(): Int {
        val session = database.userSessionDao().get() ?: return 0
        val today = todayDate()
        val stats = database.productionEntryDao().getStats(session.userId, today)
        return stats.pendingCount
    }

    /**
     * Fetch sync status from the server (used by the sync status screen).
     */
    suspend fun fetchSyncStatus(): Result<SyncStatusResponse> {
        val session = database.userSessionDao().get()
            ?: return Result.failure(Exception("Not logged in."))
        return apiClient.callRpc(
            channel = "mobile:sync-status",
            args = mapOf("token" to session.token),
        )
    }

    private fun todayDate(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }
}
