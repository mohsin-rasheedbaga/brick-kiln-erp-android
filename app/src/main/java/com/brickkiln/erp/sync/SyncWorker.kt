package com.brickkiln.erp.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.brickkiln.erp.BrickKilnApp
import com.brickkiln.erp.data.repository.ProductionRepository
import com.brickkiln.erp.util.NetworkMonitor
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BrickKilnApp
        val repo = ProductionRepository(app.settingsRepository.let {
            com.brickkiln.erp.data.remote.ApiClient(it)
        }, app.database)

        // Wait until network is available
        if (!NetworkMonitor.isNetworkAvailable(applicationContext)) {
            return Result.retry()
        }

        val pending = app.database.productionEntryDao().getPending()
        if (pending.isEmpty()) {
            // Nothing to sync — also retry any previously FAILED entries
            val failed = app.database.productionEntryDao().getFailed()
            if (failed.isEmpty()) return Result.success()
            for (entry in failed) {
                repo.syncOne(entry)
            }
            return Result.success()
        }

        var allSucceeded = true
        for (entry in pending) {
            val ok = repo.syncOne(entry)
            if (!ok) allSucceeded = false
        }
        return if (allSucceeded) Result.success() else Result.retry()
    }
}

object SyncScheduler {
    private const val WORK_NAME = "brick-kiln-sync"

    /**
     * Schedule a periodic sync that runs every 15 minutes when network is available.
     * Also runs immediately when the app starts and when network is restored.
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(BrickKilnApp.instance)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }

    /**
     * Trigger an immediate sync (e.g., when the user manually taps "Sync now").
     */
    fun syncNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(BrickKilnApp.instance).enqueue(request)
    }
}
