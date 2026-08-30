package com.watchioiptv.nativeapp.data.updates

object UpdatePolicy {
    fun validateManifest(manifest: UpdateManifest, expectedChannel: String = "dev") {
        if (manifest.schemaVersion != 1) throw UpdateException("Unsupported update manifest.")
        if (manifest.channel != expectedChannel) throw UpdateException("Update channel mismatch.")
        if (manifest.versionCode <= 0) throw UpdateException("Invalid update version.")
        if (manifest.versionName.isBlank()) throw UpdateException("Invalid update version.")
        if (!manifest.apk.downloadUrl.startsWith("https://")) throw UpdateException("Update download URL is invalid.")
        if (!Regex("^[a-fA-F0-9]{64}$").matches(manifest.apk.sha256)) throw UpdateException("Update checksum is invalid.")
        sanitizeFileName(manifest.apk.fileName)
    }

    fun compare(remoteVersionCode: Int, installedVersionCode: Long): UpdateAvailability = when {
        remoteVersionCode > installedVersionCode -> UpdateAvailability.UpdateAvailable
        remoteVersionCode < installedVersionCode -> UpdateAvailability.DevelopmentBuildNewer
        else -> UpdateAvailability.UpToDate
    }

    fun sanitizeFileName(name: String): String {
        if (name.isBlank() || name.contains("..") || name.contains('/') || name.contains('\\') || Regex("^[A-Za-z]:").containsMatchIn(name)) {
            throw UpdateException("Update filename is invalid.")
        }
        if (!name.endsWith(".apk", ignoreCase = true)) throw UpdateException("Update file is not an APK.")
        return name
    }
}
