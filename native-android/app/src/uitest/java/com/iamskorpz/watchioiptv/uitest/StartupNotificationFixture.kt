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
    MULTIPLE_NOTIFICATIONS,
    MIXED_NOTIFICATIONS,
    TEN_UNREAD_NOTIFICATIONS,
    LONG_NOTIFICATION,
    SAFE_ACTION_NOTIFICATION,
    UNSAFE_ACTION_NOTIFICATION,
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
        val editor = preferences.edit().putString(KEY_STATE, state.name)
        if (state.isInboxFixture() && !preferences.contains(KEY_INBOX_GENERATION)) {
            editor.putString(KEY_INBOX_GENERATION, UUID.randomUUID().toString())
        }
        check(editor.commit()) {
            "Unable to persist UITEST startup fixture state."
        }
        check(this.state() == state) {
            "UITEST startup fixture state failed read-after-write verification."
        }
    }

    fun selectFresh(state: StartupFixtureState) {
        val editor = preferences.edit().putString(KEY_STATE, state.name)
        if (state.isInboxFixture()) {
            editor.putString(KEY_INBOX_GENERATION, UUID.randomUUID().toString())
        } else if (state.hasStartupAnnouncement()) {
            editor
                .putString(KEY_ANNOUNCEMENT_GENERATION, UUID.randomUUID().toString())
                .putInt(KEY_ANNOUNCEMENT_REVISION, 1)
        }
        check(editor.commit()) {
            "Unable to persist fresh UITEST startup fixture generation."
        }
        check(this.state() == state) {
            "Fresh UITEST startup fixture generation failed read-after-write verification."
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

    fun announcementFeed(): String {
        val selected = state()
        val selectedGeneration = if (selected.isInboxFixture()) inboxGeneration() else generation()
        return feedFor(selected, selectedGeneration, revision())
    }

    fun announcementId(): String = announcementIdFor(generation(), revision())

    fun inboxAnnouncementIds(): List<String> = inboxAnnouncementIds(state(), inboxGeneration())

    private fun revision(): Int = preferences.getInt(KEY_ANNOUNCEMENT_REVISION, 1).coerceAtLeast(1)

    private fun generation(): String = preferences.getString(KEY_ANNOUNCEMENT_GENERATION, null) ?: generation
    private fun inboxGeneration(): String = preferences.getString(KEY_INBOX_GENERATION, "default") ?: "default"

    companion object {
        const val PREFERENCES = "watchio_uitest_startup_fixture"
        const val KEY_STATE = "state"
        const val KEY_ANNOUNCEMENT_REVISION = "announcement_revision"
        const val KEY_ANNOUNCEMENT_GENERATION = "announcement_generation"
        const val KEY_INBOX_GENERATION = "inbox_generation"
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
            channel = "dev",
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
        } else inboxEntries(selected, generation).joinToString(",")
    return """{"version":1,"announcements":[$announcements]}"""
}

internal fun StartupFixtureState.isInboxFixture(): Boolean = this in setOf(
    StartupFixtureState.MULTIPLE_NOTIFICATIONS,
    StartupFixtureState.MIXED_NOTIFICATIONS,
    StartupFixtureState.TEN_UNREAD_NOTIFICATIONS,
    StartupFixtureState.LONG_NOTIFICATION,
    StartupFixtureState.SAFE_ACTION_NOTIFICATION,
    StartupFixtureState.UNSAFE_ACTION_NOTIFICATION,
)

internal fun StartupFixtureState.hasStartupAnnouncement(): Boolean = this in setOf(
    StartupFixtureState.ANNOUNCEMENT,
    StartupFixtureState.OPTIONAL_UPDATE_AND_ANNOUNCEMENT,
    StartupFixtureState.MANDATORY_UPDATE_AND_ANNOUNCEMENT,
)

internal fun inboxAnnouncementIds(state: StartupFixtureState, generation: String): List<String> =
    inboxEntries(state, generation).map { entry -> Regex("\\\"id\\\":\\\"([^\\\"]+)").find(entry)!!.groupValues[1] }

private fun inboxEntries(state: StartupFixtureState, generation: String): List<String> {
    val count = when (state) {
        StartupFixtureState.MULTIPLE_NOTIFICATIONS, StartupFixtureState.MIXED_NOTIFICATIONS -> 3
        StartupFixtureState.TEN_UNREAD_NOTIFICATIONS -> 10
        StartupFixtureState.LONG_NOTIFICATION,
        StartupFixtureState.SAFE_ACTION_NOTIFICATION,
        StartupFixtureState.UNSAFE_ACTION_NOTIFICATION -> 1
        else -> 0
    }
    return (1..count).map { index ->
        val id = "uitest-inbox-${state.name.lowercase()}-$generation-$index"
        val title = if (state == StartupFixtureState.LONG_NOTIFICATION) {
            "Long Watchio notification for phone and TV scrolling acceptance"
        } else {
            "Test notification $index"
        }
        val body = if (state == StartupFixtureState.LONG_NOTIFICATION) {
            (1..18).joinToString(" ") { paragraph ->
                "Section $paragraph explains deterministic Watchio notification layout, scrolling, focus, controls, and Back navigation."
            }
        } else {
            "Deterministic UITEST notification $index for Inbox acceptance."
        }
        val action = when (state) {
            StartupFixtureState.SAFE_ACTION_NOTIFICATION ->
                ""","action":{"type":"OPEN_URL","label":"OPEN SAFE LINK","url":"https://example.com/watchio-uitest"}"""
            StartupFixtureState.UNSAFE_ACTION_NOTIFICATION ->
                ""","action":{"type":"OPEN_URL","label":"UNSAFE LINK","url":"http://example.com/watchio-uitest"}"""
            else -> ""
        }
        val day = 20 - index
        """{"id":"$id","title":"$title","body":"$body","publishedAt":"2026-09-${day.toString().padStart(2, '0')}T12:00:00Z","type":"GENERAL","priority":"NORMAL","dismissible":true,"enabled":true$action}"""
    }
}

internal fun fixtureArtifactDownloadsEnabled(): Boolean = false
