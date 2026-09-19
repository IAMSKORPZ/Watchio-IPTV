package com.iamskorpz.watchioiptv.uitest

import android.content.Context
import com.iamskorpz.watchioiptv.data.updates.InstalledVersion
import com.iamskorpz.watchioiptv.data.updates.UpdateApk
import com.iamskorpz.watchioiptv.data.updates.UpdateManifest
import java.util.UUID

enum class StartupFixtureState {
    NONE,
    OPTIONAL_UPDATE,
    MANDATORY_UPDATE,
    ANNOUNCEMENT,
    OPTIONAL_UPDATE_AND_ANNOUNCEMENT,
    MANDATORY_UPDATE_AND_ANNOUNCEMENT,
}

class StartupNotificationFixture(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val generation: String = preferences.getString(KEY_ANNOUNCEMENT_GENERATION, null)
        ?: UUID.randomUUID().toString().also { generated ->
            check(preferences.edit().putString(KEY_ANNOUNCEMENT_GENERATION, generated).commit()) {
                "Unable to persist UITEST announcement generation."
            }
        }

    fun state(): StartupFixtureState = runCatching {
        StartupFixtureState.valueOf(preferences.getString(KEY_STATE, null).orEmpty())
    }.getOrDefault(StartupFixtureState.NONE)

    fun select(state: StartupFixtureState) {
        check(preferences.edit().putString(KEY_STATE, state.name).commit()) {
            "Unable to persist UITEST startup fixture state."
        }
        check(this.state() == state) {
            "UITEST startup fixture state failed read-after-write verification."
        }
    }

    fun nextAnnouncementId(): Int {
        val expected = revision() + 1
        check(preferences.edit().putInt(KEY_ANNOUNCEMENT_REVISION, expected).commit()) {
            "Unable to persist UITEST announcement revision."
        }
        check(revision() == expected) {
            "UITEST announcement revision failed read-after-write verification."
        }
        return expected
    }

    fun reset(): Int {
        val nextGeneration = UUID.randomUUID().toString()
        val nextRevision = 1
        check(
            preferences.edit()
                .clear()
                .putString(KEY_ANNOUNCEMENT_GENERATION, nextGeneration)
                .putInt(KEY_ANNOUNCEMENT_REVISION, nextRevision)
                .commit(),
        ) {
            "Unable to reset UITEST startup fixture state."
        }
        check(
            state() == StartupFixtureState.NONE &&
                revision() == nextRevision &&
                preferences.getString(KEY_ANNOUNCEMENT_GENERATION, null) == nextGeneration,
        ) {
            "UITEST startup fixture reset failed read-after-write verification."
        }
        return nextRevision
    }

    fun updateManifest(installed: InstalledVersion): UpdateManifest = manifestFor(state(), installed)

    fun announcementFeed(): String = feedFor(state(), generation(), revision())

    fun announcementId(): String = announcementIdFor(generation(), revision())

    private fun revision(): Int = preferences.getInt(KEY_ANNOUNCEMENT_REVISION, 1).coerceAtLeast(1)

    private fun generation(): String = preferences.getString(KEY_ANNOUNCEMENT_GENERATION, null) ?: generation

    companion object {
        const val PREFERENCES = "watchio_uitest_startup_fixture"
        const val KEY_STATE = "state"
        const val KEY_ANNOUNCEMENT_REVISION = "announcement_revision"
        const val KEY_ANNOUNCEMENT_GENERATION = "announcement_generation"
        const val OPTIONAL_VERSION_CODE = 990001
        const val OPTIONAL_VERSION_NAME = "99.0.0-uitest-optional"
        const val MANDATORY_VERSION_CODE = 990002
        const val MANDATORY_VERSION_NAME = "99.0.0-uitest-mandatory"
        const val ANNOUNCEMENT_ID_PREFIX = "uitest-physical-announcement-"
    }
}

internal fun manifestFor(selected: StartupFixtureState, installed: InstalledVersion): UpdateManifest {
        val mandatory = selected == StartupFixtureState.MANDATORY_UPDATE ||
            selected == StartupFixtureState.MANDATORY_UPDATE_AND_ANNOUNCEMENT
        val optional = selected == StartupFixtureState.OPTIONAL_UPDATE ||
            selected == StartupFixtureState.OPTIONAL_UPDATE_AND_ANNOUNCEMENT
        val versionCode = when {
            mandatory -> StartupNotificationFixture.MANDATORY_VERSION_CODE
            optional -> StartupNotificationFixture.OPTIONAL_VERSION_CODE
            else -> installed.versionCode.toInt()
        }
        val versionName = when {
            mandatory -> StartupNotificationFixture.MANDATORY_VERSION_NAME
            optional -> StartupNotificationFixture.OPTIONAL_VERSION_NAME
            else -> installed.versionName
        }
    return UpdateManifest(
            schemaVersion = 1,
            channel = "uitest",
            versionCode = versionCode,
            versionName = versionName,
            minimumSupportedVersionCode = 1,
            mandatory = mandatory,
            publishedAt = "2026-09-18T12:00:00Z",
            releaseNotes = listOf("UITEST startup notification fixture."),
            githubRelease = "https://example.invalid/watchio-uitest",
            apk = UpdateApk(
                fileName = "watchio-uitest-fixture.apk",
                downloadUrl = "https://example.invalid/watchio-uitest-fixture.apk",
                sha256 = "0".repeat(64),
            ),
    )
}

internal fun announcementIdFor(generation: String, revision: Int): String =
    "${StartupNotificationFixture.ANNOUNCEMENT_ID_PREFIX}$generation-$revision"

internal fun feedFor(selected: StartupFixtureState, generation: String, revision: Int): String {
        val enabled = selected == StartupFixtureState.ANNOUNCEMENT ||
            selected == StartupFixtureState.OPTIONAL_UPDATE_AND_ANNOUNCEMENT ||
            selected == StartupFixtureState.MANDATORY_UPDATE_AND_ANNOUNCEMENT
        val announcements = if (enabled) {
            """{"id":"${announcementIdFor(generation, revision)}","title":"Watchio Test Announcement","body":"This is a Watchio UITEST announcement used for physical acceptance.","publishedAt":"2026-09-18T12:00:00Z","type":"GENERAL","priority":"IMPORTANT","dismissible":true,"enabled":true,"action":null}"""
        } else ""
    return """{"version":1,"announcements":[$announcements]}"""
}

internal fun fixtureArtifactDownloadsEnabled(): Boolean = false
