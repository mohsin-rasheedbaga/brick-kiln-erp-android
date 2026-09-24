package com.brickkiln.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.brickkiln.erp.data.local.entity.WorkerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(workers: List<WorkerEntity>)

    @Query("SELECT * FROM workers WHERE status = 'active' ORDER BY fullName")
    fun observeAll(): Flow<List<WorkerEntity>>

    @Query("SELECT * FROM workers WHERE id = :id")
    suspend fun get(id: String): WorkerEntity?

    @Query("SELECT * FROM workers WHERE workerCode = :code")
    suspend fun findByCode(code: String): WorkerEntity?

    @Query("""
        SELECT * FROM workers
        WHERE status = 'active'
          AND (fullName LIKE '%' || :q || '%' OR workerCode LIKE '%' || :q || '%')
        ORDER BY fullName LIMIT 50
    """)
    suspend fun search(q: String): List<WorkerEntity>

    @Query("DELETE FROM workers")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM workers")
    suspend fun count(): Int
}
