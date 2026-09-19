package com.iamskorpz.watchioiptv.feature.settings

import com.iamskorpz.watchioiptv.data.updates.UpdateApk
import com.iamskorpz.watchioiptv.data.updates.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OptionalUpdateSessionDeferralTest {
    @Test
    fun repeatedLaterAndBackCyclesNeverCrossSessionBoundary() {
        repeat(3) {
            val runningSession = OptionalUpdateSessionDeferral()
            assertNull(runningSession.deferredUpdateCode.value)

            // LATER and optional Back both invoke this same operation.
            runningSession.defer(optionalManifest)
            assertEquals(optionalManifest.versionCode, runningSession.deferredUpdateCode.value)

            val newProcessSession = OptionalUpdateSessionDeferral()
            assertNull(newProcessSession.deferredUpdateCode.value)
        }
    }

    @Test
    fun mandatoryUpdateCannotInheritOptionalDeferral() {
        val runningSession = OptionalUpdateSessionDeferral()

        runningSession.defer(mandatoryManifest)

        assertNull(runningSession.deferredUpdateCode.value)
    }

    private val optionalManifest = manifest(mandatory = false)
    private val mandatoryManifest = manifest(mandatory = true)

    private fun manifest(mandatory: Boolean) = UpdateManifest(
        schemaVersion = 1,
        channel = "dev",
        versionCode = if (mandatory) 990002 else 990001,
        versionName = if (mandatory) "99.0.0-uitest-mandatory" else "99.0.0-uitest-optional",
        minimumSupportedVersionCode = 1,
        mandatory = mandatory,
        publishedAt = "2026-09-19T12:00:00Z",
        releaseNotes = listOf("UITEST startup notification fixture."),
        githubRelease = "https://example.com/release",
        apk = UpdateApk("Watchio-IPTV.apk", "https://example.com/Watchio-IPTV.apk", "a".repeat(64)),
    )
}
