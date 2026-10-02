package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.data.updates.UpdateApk
import com.iamskorpz.watchioiptv.data.updates.UpdateArtifactValidator
import com.iamskorpz.watchioiptv.data.updates.UpdateAvailability
import com.iamskorpz.watchioiptv.data.updates.UpdateException
import com.iamskorpz.watchioiptv.data.updates.UpdateManifest
import com.iamskorpz.watchioiptv.data.updates.UpdatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UpdateChannelAndArtifactTest {
    @Test fun publicCode14SeesStableCode15() = assertEquals(UpdateAvailability.UpdateAvailable, UpdatePolicy.compare(15, 14))
    @Test fun publicCode15IsCurrentWithStableCode15() = assertEquals(UpdateAvailability.UpToDate, UpdatePolicy.compare(15, 15))
    @Test fun publicCode16DoesNotDowngradeToStableCode15() = assertEquals(UpdateAvailability.DevelopmentBuildNewer, UpdatePolicy.compare(15, 16))

    @Test fun publicAcceptsStable() = UpdatePolicy.validateManifest(manifest("stable"), "stable")
    @Test fun developmentAcceptsDev() = UpdatePolicy.validateManifest(manifest("dev"), "dev")
    @Test(expected = UpdateException::class) fun publicRejectsDev() = UpdatePolicy.validateManifest(manifest("dev"), "stable")
    @Test(expected = UpdateException::class) fun developmentRejectsStable() = UpdatePolicy.validateManifest(manifest("stable"), "dev")
    @Test(expected = UpdateException::class) fun unknownManifestChannelFails() = UpdatePolicy.validateManifest(manifest("preview"), "stable")
    @Test(expected = UpdateException::class) fun missingManifestChannelFails() = UpdatePolicy.validateManifest(manifest(""), "stable")
    @Test(expected = UpdateException::class) fun unknownExpectedChannelFails() = UpdatePolicy.validateManifest(manifest("preview"), "preview")

    @Test
    fun cachedCorrectHashWrongPackageIsRejected() {
        val file = artifact()
        try {
            UpdateArtifactValidator.validateCached(file, sha256(file), "com.iamskorpz.watchioiptv") { "wrong.package" }
        } catch (expected: UpdateException) {
            assertFalse(file.exists())
            return
        }
        throw AssertionError("Wrong-package cached APK was accepted")
    }

    @Test
    fun freshWrongPackageIsRejected() {
        val file = artifact()
        try {
            UpdateArtifactValidator.validateDownloaded(file, sha256(file), "com.iamskorpz.watchioiptv") { "wrong.package" }
        } catch (expected: UpdateException) {
            assertFalse(file.exists())
            return
        }
        throw AssertionError("Wrong-package downloaded APK was accepted")
    }

    @Test
    fun shaMismatchIsRejected() {
        val file = artifact()
        try {
            UpdateArtifactValidator.validateDownloaded(file, "0".repeat(64), "com.iamskorpz.watchioiptv") { "com.iamskorpz.watchioiptv" }
        } catch (expected: UpdateException) {
            assertFalse(file.exists())
            return
        }
        throw AssertionError("SHA-mismatched APK was accepted")
    }

    private fun artifact(): File = File.createTempFile("watchio-update-", ".apk").apply { writeText("test artifact") }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    private fun manifest(channel: String) = UpdateManifest(
        schemaVersion = 1,
        channel = channel,
        versionCode = 15,
        versionName = "0.1.2",
        minimumSupportedVersionCode = 1,
        mandatory = false,
        publishedAt = "2026-09-16T00:00:00Z",
        releaseNotes = listOf("Update test"),
        githubRelease = "https://github.com/IAMSKORPZ/Watchio-IPTV/releases/tag/v0.1.2",
        apk = UpdateApk(
            fileName = "Watchio-IPTV.apk",
            downloadUrl = "https://github.com/IAMSKORPZ/Watchio-IPTV/releases/latest/download/Watchio-IPTV.apk",
            sha256 = "a".repeat(64),
        ),
    )
}
