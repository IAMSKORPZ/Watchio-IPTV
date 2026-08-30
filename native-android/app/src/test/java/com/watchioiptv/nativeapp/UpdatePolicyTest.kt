package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.data.updates.UpdateApk
import com.watchioiptv.nativeapp.data.updates.UpdateAvailability
import com.watchioiptv.nativeapp.data.updates.UpdateException
import com.watchioiptv.nativeapp.data.updates.UpdateManifest
import com.watchioiptv.nativeapp.data.updates.UpdatePolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class UpdatePolicyTest {
    @Test
    fun remoteNewerIsUpdateAvailable() {
        assertEquals(UpdateAvailability.UpdateAvailable, UpdatePolicy.compare(remoteVersionCode = 3, installedVersionCode = 2))
    }

    @Test
    fun sameVersionIsUpToDate() {
        assertEquals(UpdateAvailability.UpToDate, UpdatePolicy.compare(remoteVersionCode = 2, installedVersionCode = 2))
    }

    @Test
    fun remoteOlderIsUpToDateForDevelopmentBuild() {
        assertEquals(UpdateAvailability.DevelopmentBuildNewer, UpdatePolicy.compare(remoteVersionCode = 1, installedVersionCode = 2))
    }

    @Test
    fun validManifestPasses() {
        UpdatePolicy.validateManifest(manifest())
    }

    @Test(expected = UpdateException::class)
    fun malformedShaFails() {
        UpdatePolicy.validateManifest(manifest(apk = apk().copy(sha256 = "bad")))
    }

    @Test(expected = UpdateException::class)
    fun httpApkUrlFails() {
        UpdatePolicy.validateManifest(manifest(apk = apk().copy(downloadUrl = "http://example.invalid/app.apk")))
    }

    @Test(expected = UpdateException::class)
    fun channelMismatchFails() {
        UpdatePolicy.validateManifest(manifest(channel = "stable"))
    }

    @Test(expected = UpdateException::class)
    fun pathTraversalFilenameFails() {
        UpdatePolicy.validateManifest(manifest(apk = apk().copy(fileName = "../app.apk")))
    }

    @Test
    fun shaMatchAndMismatchAreExact() {
        val expected = sha256("watchio".toByteArray())
        assertEquals(expected, sha256("watchio".toByteArray()))
        assertEquals(false, expected.equals(sha256("broken".toByteArray()), ignoreCase = true))
    }

    private fun manifest(
        channel: String = "dev",
        apk: UpdateApk = apk(),
    ) = UpdateManifest(
        schemaVersion = 1,
        channel = channel,
        versionCode = 2,
        versionName = "0.1.0-dev.1-debug",
        minimumSupportedVersionCode = 1,
        mandatory = false,
        publishedAt = "2026-08-28T16:36:29+01:00",
        releaseNotes = listOf("Native Android Watchio development build."),
        githubRelease = "https://github.com/IAMSKORPZ/Watchio-IPTV/releases/tag/v0.1.0-dev.1",
        apk = apk,
    )

    private fun apk() = UpdateApk(
        fileName = "watchio-dev-0.1.0-dev.1-debug.apk",
        downloadUrl = "https://github.com/IAMSKORPZ/Watchio-IPTV/releases/download/v0.1.0-dev.1/watchio-dev-0.1.0-dev.1-debug.apk",
        sha256 = "7453742ce6d876bdf153bfc699938ffa9aef6ba0efa3ab96acfc9bbe46d33b6a",
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
