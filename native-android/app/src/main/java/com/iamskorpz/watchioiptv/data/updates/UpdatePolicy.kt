package com.iamskorpz.watchioiptv.data.updates

import java.net.URI

object UpdatePolicy {
    fun validateManifest(manifest: UpdateManifest, expectedChannel: String) {
        if (manifest.schemaVersion != 1) throw UpdateException("Unsupported update manifest.")
        if (expectedChannel !in setOf("stable", "dev", "uitest")) throw UpdateException("Update channel is invalid.")
        if (manifest.channel !in setOf("stable", "dev", "uitest")) throw UpdateException("Update channel is invalid.")
        if (manifest.channel != expectedChannel) throw UpdateException("Update channel mismatch.")
        if (manifest.versionCode <= 0) throw UpdateException("Invalid update version.")
        if (manifest.versionName.isBlank()) throw UpdateException("Invalid update version.")
        if (expectedChannel != "uitest") {
            validateTrustedGitHubUrl(manifest.githubRelease, listOf("/IAMSKORPZ/Watchio-IPTV/releases/tag/"))
            validateTrustedGitHubUrl(
                manifest.apk.downloadUrl,
                listOf(
                    "/IAMSKORPZ/Watchio-IPTV/releases/download/",
                    "/IAMSKORPZ/Watchio-IPTV/releases/latest/download/",
                ),
            )
        }
        if (!Regex("^[a-fA-F0-9]{64}$").matches(manifest.apk.sha256)) throw UpdateException("Update checksum is invalid.")
        sanitizeFileName(manifest.apk.fileName)
        if (!URI(manifest.apk.downloadUrl).path.endsWith("/${manifest.apk.fileName}")) {
            throw UpdateException("Update filename does not match download URL.")
        }
        if (manifest.channel == "stable" && manifest.apk.fileName != "Watchio-IPTV.apk") {
            throw UpdateException("Stable update filename is invalid.")
        }
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

    private fun validateTrustedGitHubUrl(value: String, requiredPathPrefixes: List<String>) {
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw UpdateException("Update URL is invalid.")
        val trusted = uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.userInfo == null &&
            uri.port == -1 &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            requiredPathPrefixes.any(uri.path::startsWith)
        if (!trusted) throw UpdateException("Update URL is not trusted.")
    }
}
