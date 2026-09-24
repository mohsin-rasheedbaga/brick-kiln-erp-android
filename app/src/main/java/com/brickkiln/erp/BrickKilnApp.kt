package com.brickkiln.erp

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.brickkiln.erp.data.local.AppDatabase
import com.brickkiln.erp.data.repository.SettingsRepository
import com.brickkiln.erp.sync.SyncScheduler

/**
 * Application class.
 *
 * Implements Configuration.Provider for WorkManager so that periodic sync
 * can run in the background. We do NOT call WorkManager.initialize() manually
 * because we implement Configuration.Provider — WorkManager auto-initializes
 * itself lazily on first use, using our configuration.
 *
 * Calling WorkManager.initialize() when implementing Configuration.Provider
 * throws IllegalStateException ("WorkManager is already initialized"), which
 * was causing the app to crash on launch.
 */
class BrickKilnApp : Application(), Configuration.Provider {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i("BrickKilnApp", "Application started")

        // Schedule periodic sync — safe to call here; WorkManager will pick
        // up our Configuration via the Configuration.Provider interface.
        try {
            SyncScheduler.schedulePeriodicSync()
        } catch (e: Exception) {
            Log.e("BrickKilnApp", "Failed to schedule periodic sync", e)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    companion object {
        lateinit var instance: BrickKilnApp
            private set
    }
}

