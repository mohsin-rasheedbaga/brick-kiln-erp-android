package com.brickkiln.erp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.brickkiln.erp.data.local.dao.MasterDataDao
import com.brickkiln.erp.data.local.dao.ProductionEntryDao
import com.brickkiln.erp.data.local.dao.UserSessionDao
import com.brickkiln.erp.data.local.dao.WorkerDao
import com.brickkiln.erp.data.local.entity.BrickCategoryEntity
import com.brickkiln.erp.data.local.entity.KilnEntity
import com.brickkiln.erp.data.local.entity.ProductionEntryEntity
import com.brickkiln.erp.data.local.entity.UserSessionEntity
import com.brickkiln.erp.data.local.entity.WorkTypeEntity
import com.brickkiln.erp.data.local.entity.WorkerEntity

@Database(
    entities = [
        UserSessionEntity::class,
        WorkerEntity::class,
        WorkTypeEntity::class,
        KilnEntity::class,
        BrickCategoryEntity::class,
        ProductionEntryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userSessionDao(): UserSessionDao
    abstract fun workerDao(): WorkerDao
    abstract fun masterDataDao(): MasterDataDao
    abstract fun productionEntryDao(): ProductionEntryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "brick-kiln-erp.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
