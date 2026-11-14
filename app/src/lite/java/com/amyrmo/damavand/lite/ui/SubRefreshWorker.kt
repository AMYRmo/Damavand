package com.amyrmo.damavand.lite.ui

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amyrmo.damavand.lite.ProfileRepository

/** Background subscription refresh (WorkManager, 12 h, Wi-Fi-only). */
class SubRefreshWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            ProfileRepository(applicationContext).refreshAll()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
