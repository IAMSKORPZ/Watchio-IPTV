package com.iamskorpz.watchioiptv.data.epg

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.iamskorpz.watchioiptv.WatchioNativeApplication
import java.io.IOException

class EpgAutoRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? WatchioNativeApplication ?: return Result.failure()
        val summary = try {
            app.container.epgRefreshCoordinator.refreshAllEnabledProviders()
        } catch (_: IOException) {
            return Result.retry()
        } catch (_: Throwable) {
            return Result.failure()
        }
        return when {
            summary.refreshedProviders > 0 -> Result.success()
            summary.failedProviders > 0 -> Result.retry()
            else -> Result.success()
        }
    }
}
