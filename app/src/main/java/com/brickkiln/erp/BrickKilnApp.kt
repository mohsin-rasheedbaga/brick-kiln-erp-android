package com.brickkiln.erp

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.brickkiln.erp.data.local.AppDatabase
import com.brickkiln.erp.data.remote.ApiClient
import com.brickkiln.erp.data.repository.SettingsRepository
import com.brickkiln.erp.sync.SyncScheduler

class BrickKilnApp : Application(), Configuration.Provider {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Configure WorkManager
        WorkManager.initialize(this, workManagerConfiguration)

        // Schedule periodic sync
        SyncScheduler.schedulePeriodicSync()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    companion object {
        lateinit var instance: BrickKilnApp
            private set
    }
}
