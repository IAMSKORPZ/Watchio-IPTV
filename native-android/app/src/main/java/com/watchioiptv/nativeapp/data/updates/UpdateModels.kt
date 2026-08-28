package com.watchioiptv.nativeapp.data.updates

import kotlinx.serialization.Serializable

const val WATCHIO_DEV_UPDATE_MANIFEST_URL =
    "https://raw.githubusercontent.com/IAMSKORPZ/Watchio-IPTV/dev/native-android/update/update.json"

@Serializable
data class UpdateManifest(
    val schemaVersion: Int,
    val channel: String,
    val versionCode: Int,
    val versionName: String,
    val minimumSupportedVersionCode: Int,
    val mandatory: Boolean,
    val publishedAt: String,
    val releaseNotes: List<String>,
    val githubRelease: String,
    val apk: UpdateApk,
)

@Serializable
data class UpdateApk(
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
)

data class InstalledVersion(
    val versionCode: Long,
    val versionName: String,
)

data class UpdateCheckResult(
    val installed: InstalledVersion,
    val manifest: UpdateManifest,
    val status: UpdateAvailability,
)

enum class UpdateAvailability {
    UpToDate,
    DevelopmentBuildNewer,
    UpdateAvailable,
}

data class VerifiedUpdateFile(
    val manifest: UpdateManifest,
    val filePath: String,
)
