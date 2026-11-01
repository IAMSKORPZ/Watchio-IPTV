package com.iamskorpz.watchioiptv.data.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.iamskorpz.watchioiptv.R
import com.iamskorpz.watchioiptv.WatchioNativeApplication

@UnstableApi
class WatchioDownloadService : DownloadService(FOREGROUND_NOTIFICATION_ID) {
    private lateinit var notificationHelper: DownloadNotificationHelper

    override fun onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Offline downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        notificationHelper = DownloadNotificationHelper(this, CHANNEL_ID)
        super.onCreate()
    }

    override fun getDownloadManager(): DownloadManager =
        (application as WatchioNativeApplication).container.offlineMovieDownloadRepository.downloadManager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int) =
        notificationHelper.buildProgressNotification(
            this,
            R.mipmap.ic_launcher,
            null,
            "Downloading offline media",
            downloads,
            notMetRequirements,
        )

    companion object {
        private const val CHANNEL_ID = "watchio_offline_downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 2712

        fun add(context: Context, request: DownloadRequest) {
            sendAddDownload(context, WatchioDownloadService::class.java, request, true)
        }

        fun remove(context: Context, downloadId: String) {
            sendRemoveDownload(context, WatchioDownloadService::class.java, downloadId, true)
        }
    }
}
