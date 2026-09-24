package com.brickkiln.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.brickkiln.erp.data.local.entity.ProductionEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductionEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ProductionEntryEntity)

    @Update
    suspend fun update(entry: ProductionEntryEntity)

    @Query("SELECT * FROM production_entries WHERE enteredBy = :userId ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(userId: String, limit: Int = 50): Flow<List<ProductionEntryEntity>>

    @Query("SELECT * FROM production_entries WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<ProductionEntryEntity>

    @Query("SELECT * FROM production_entries WHERE status = 'FAILED' ORDER BY createdAt ASC")
    suspend fun getFailed(): List<ProductionEntryEntity>

    @Query("UPDATE production_entries SET status = :status, syncError = :error, syncedAt = :syncedAt WHERE clientUuid = :id")
    suspend fun updateStatus(id: String, status: String, error: String?, syncedAt: Long?)

    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN date = :today THEN quantity ELSE 0 END), 0) AS todayQty,
            COALESCE(SUM(CASE WHEN date = :today THEN labourAmount ELSE 0 END), 0) AS todayLabour,
            COUNT(CASE WHEN status = 'PENDING' THEN 1 END) AS pendingCount,
            COUNT(CASE WHEN status = 'SYNCED' THEN 1 END) AS syncedCount,
            COUNT(CASE WHEN status = 'FAILED' THEN 1 END) AS failedCount,
            COUNT(*) AS totalCount
        FROM production_entries
        WHERE enteredBy = :userId
    """)
    suspend fun getStats(userId: String, today: String): EntryStats

    data class EntryStats(
        val todayQty: Double,
        val todayLabour: Double,
        val pendingCount: Int,
        val syncedCount: Int,
        val failedCount: Int,
        val totalCount: Int,
    )
}
