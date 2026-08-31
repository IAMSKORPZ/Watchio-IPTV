package com.watchioiptv.nativeapp.data.announcements

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.watchioiptv.nativeapp.domain.model.Announcement
import com.watchioiptv.nativeapp.domain.model.AnnouncementAction
import com.watchioiptv.nativeapp.domain.model.AnnouncementItem
import com.watchioiptv.nativeapp.domain.model.AnnouncementPriority
import com.watchioiptv.nativeapp.domain.model.AnnouncementScreen
import com.watchioiptv.nativeapp.domain.model.AnnouncementSnapshot
import com.watchioiptv.nativeapp.domain.model.AnnouncementType
import com.watchioiptv.nativeapp.data.updates.UpdateAvailability
import com.watchioiptv.nativeapp.data.updates.UpdateCheckResult
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class AnnouncementFeedParser(
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) {
    fun parse(raw: String): List<Announcement> {
        val root = json.parseToJsonElement(raw).jsonObject
        require((root["version"]?.jsonPrimitive?.intOrNull ?: 0) >= 1) { "Unsupported announcement feed" }
        val entries = root["announcements"]?.jsonArray ?: error("Missing announcements")
        return entries.mapNotNull { element ->
            runCatching { parseAnnouncement(element.jsonObject) }.getOrNull()
        }.distinctBy(Announcement::id)
    }

    private fun parseAnnouncement(value: JsonObject): Announcement {
        fun required(name: String) = value[name]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().also {
            require(it.isNotEmpty()) { "Missing $name" }
        }
        val type = AnnouncementType.valueOf(required("type").uppercase())
        val priority = AnnouncementPriority.valueOf(required("priority").uppercase())
        return Announcement(
            id = required("id"),
            title = required("title"),
            body = required("body"),
            publishedAt = required("publishedAt"),
            type = type,
            priority = priority,
            action = value["action"]?.let { runCatching { parseAction(it.jsonObject) }.getOrNull() },
            dismissible = runCatching { value["dismissible"]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: true,
            expiresAt = runCatching {
                value["expiresAt"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            }.getOrNull(),
        )
    }

    private fun parseAction(value: JsonObject): AnnouncementAction? {
        val type = value["type"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: return null
        val label = value["label"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        return when (type) {
            "NONE" -> null
            "OPEN_URL" -> {
                val url = value["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                require(url.startsWith("https://") || url.startsWith("http://"))
                AnnouncementAction.OpenUrl(url, label ?: "OPEN")
            }
            "OPEN_SCREEN" -> {
                val target = value["target"]?.jsonPrimitive?.contentOrNull.orEmpty().uppercase()
                AnnouncementAction.OpenScreen(AnnouncementScreen.valueOf(target), label ?: "VIEW")
            }
            "OPEN_UPDATER" -> AnnouncementAction.OpenUpdater(label ?: "UPDATE NOW")
            else -> null
        }
    }
}

fun interface AnnouncementRemoteDataSource {
    suspend fun fetch(): String
}

class GitHubAnnouncementRemoteDataSource(
    private val okHttpClient: OkHttpClient,
    private val feedUrl: String = FEED_URL,
) : AnnouncementRemoteDataSource {
    override suspend fun fetch(): String = withContext(Dispatchers.IO) {
        okHttpClient.newCall(Request.Builder().url(feedUrl).get().build()).execute().use { response ->
            check(response.isSuccessful) { "Announcement feed unavailable" }
            response.body?.string()?.takeIf(String::isNotBlank) ?: error("Announcement feed empty")
        }
    }

    companion object {
        const val FEED_URL = "https://raw.githubusercontent.com/IAMSKORPZ/Watchio-IPTV/dev/announcements/announcements.json"
    }
}

class StaticAnnouncementRemoteDataSource(private val raw: String) : AnnouncementRemoteDataSource {
    override suspend fun fetch(): String = raw
}

interface AnnouncementLocalStore {
    val cachedFeed: Flow<String?>
    val seenIds: Flow<Set<String>>
    val dismissedIds: Flow<Set<String>>
    suspend fun saveFeed(raw: String)
    suspend fun markSeen(id: String)
    suspend fun dismiss(id: String)
}

class DataStoreAnnouncementLocalStore(
    private val dataStore: DataStore<Preferences>,
) : AnnouncementLocalStore {
    private val safeData = dataStore.data.catch { emit(emptyPreferences()) }
    override val cachedFeed = safeData.map { it[CachedFeed] }
    override val seenIds = safeData.map { it[SeenIds].orEmpty() }
    override val dismissedIds = safeData.map { it[DismissedIds].orEmpty() }

    override suspend fun saveFeed(raw: String) { dataStore.edit { it[CachedFeed] = raw } }
    override suspend fun markSeen(id: String) { dataStore.edit { it[SeenIds] = it[SeenIds].orEmpty() + id } }
    override suspend fun dismiss(id: String) {
        dataStore.edit {
            it[SeenIds] = it[SeenIds].orEmpty() + id
            it[DismissedIds] = it[DismissedIds].orEmpty() + id
        }
    }

    private companion object {
        val CachedFeed = stringPreferencesKey("announcement_cached_feed")
        val SeenIds = stringSetPreferencesKey("announcement_seen_ids")
        val DismissedIds = stringSetPreferencesKey("announcement_dismissed_ids")
    }
}

class AnnouncementRepository(
    private val remote: AnnouncementRemoteDataSource,
    private val local: AnnouncementLocalStore,
    private val updateChecker: (suspend () -> UpdateCheckResult?)? = null,
    private val parser: AnnouncementFeedParser = AnnouncementFeedParser(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val refreshMutex = Mutex()
    private val latestUpdate = MutableStateFlow<UpdateCheckResult?>(null)

    val snapshot: Flow<AnnouncementSnapshot> = combine(
        local.cachedFeed,
        local.seenIds,
        local.dismissedIds,
        latestUpdate,
    ) { raw, seen, dismissed, updateResult ->
        val parsed = raw?.let { runCatching { parser.parse(it) }.getOrNull() }
        val generatedUpdate = generateUpdateAnnouncement(updateResult)
        val remoteList = parsed.orEmpty()
        val allAnnouncements = if (generatedUpdate != null) {
            listOf(generatedUpdate) + remoteList.filterNot {
                it.id == generatedUpdate.id ||
                    (it.type == AnnouncementType.UPDATE && it.title.contains(updateResult?.manifest?.versionName.orEmpty(), ignoreCase = true))
            }
        } else {
            remoteList
        }
        val announcements = allAnnouncements
            .filterNot(::isExpired)
            .sortedByDescending { runCatching { Instant.parse(it.publishedAt) }.getOrNull() ?: Instant.MIN }
        AnnouncementSnapshot(
            items = announcements.map { AnnouncementItem(it, it.id in seen, it.id in dismissed) },
            hasCachedFeed = parsed != null || generatedUpdate != null,
        )
    }

    suspend fun refresh(): Result<Unit> = refreshMutex.withLock {
        val feedResult = runCatching {
            val raw = remote.fetch()
            parser.parse(raw)
            local.saveFeed(raw)
        }
        val checker = updateChecker
        if (checker != null) {
            val update = runCatching { checker() }.getOrNull()
            latestUpdate.value = update
        }
        feedResult
    }

    suspend fun markRead(id: String) = local.markSeen(id)
    suspend fun dismiss(id: String) = local.dismiss(id)

    private fun generateUpdateAnnouncement(result: UpdateCheckResult?): Announcement? {
        if (result == null || result.status != UpdateAvailability.UpdateAvailable) return null
        val manifest = result.manifest
        val bodyText = if (manifest.releaseNotes.isNotEmpty()) {
            manifest.releaseNotes.joinToString("\n• ", prefix = "• ")
        } else {
            "A newer development build of Watchio is available."
        }
        return Announcement(
            id = "update-${manifest.versionName}-${manifest.versionCode}",
            title = "Watchio ${manifest.versionName} is available",
            body = bodyText,
            publishedAt = manifest.publishedAt.takeIf(String::isNotBlank) ?: "2026-08-31T00:00:00Z",
            type = AnnouncementType.UPDATE,
            priority = if (manifest.mandatory) AnnouncementPriority.CRITICAL else AnnouncementPriority.IMPORTANT,
            action = AnnouncementAction.OpenUpdater(label = "UPDATE NOW"),
            dismissible = !manifest.mandatory,
        )
    }

    private fun isExpired(announcement: Announcement): Boolean {
        val expiry = announcement.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
        return !expiry.isAfter(clock.instant())
    }
}
