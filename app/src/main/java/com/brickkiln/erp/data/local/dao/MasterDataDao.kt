package com.brickkiln.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.brickkiln.erp.data.local.entity.BrickCategoryEntity
import com.brickkiln.erp.data.local.entity.KilnEntity
import com.brickkiln.erp.data.local.entity.WorkTypeEntity

@Dao
interface MasterDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkTypes(items: List<WorkTypeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKilns(items: List<KilnEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBrickCategories(items: List<BrickCategoryEntity>)

    @Query("SELECT * FROM work_types WHERE isActive = 1 ORDER BY name")
    suspend fun getWorkTypes(): List<WorkTypeEntity>

    @Query("SELECT * FROM kilns ORDER BY name")
    suspend fun getKilns(): List<KilnEntity>

    @Query("SELECT * FROM brick_categories ORDER BY name")
    suspend fun getBrickCategories(): List<BrickCategoryEntity>

    @Query("DELETE FROM work_types")
    suspend fun clearWorkTypes()

    @Query("DELETE FROM kilns")
    suspend fun clearKilns()

    @Query("DELETE FROM brick_categories")
    suspend fun clearBrickCategories()
}
