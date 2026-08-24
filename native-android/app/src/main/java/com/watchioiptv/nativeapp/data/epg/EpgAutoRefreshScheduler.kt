package com.watchioiptv.nativeapp.data.epg

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class EpgAutoRefreshScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun apply(enabled: Boolean, interval: EpgRefreshInterval) {
        if (!enabled) {
            workManager.cancelUniqueWork(UniqueWorkName)
            return
        }
        val request = PeriodicWorkRequestBuilder<EpgAutoRefreshWorker>(interval.days, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(Tag)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UniqueWorkName,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    companion object {
        const val UniqueWorkName = "watchio_epg_auto_refresh"
        const val Tag = "watchio_epg"
    }
}
