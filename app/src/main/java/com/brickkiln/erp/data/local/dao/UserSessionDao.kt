package com.brickkiln.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.brickkiln.erp.data.local.entity.UserSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: UserSessionEntity)

    @Query("SELECT * FROM user_session WHERE id = 'current'")
    fun observe(): Flow<UserSessionEntity?>

    @Query("SELECT * FROM user_session WHERE id = 'current'")
    suspend fun get(): UserSessionEntity?

    @Query("DELETE FROM user_session WHERE id = 'current'")
    suspend fun clear()
}
