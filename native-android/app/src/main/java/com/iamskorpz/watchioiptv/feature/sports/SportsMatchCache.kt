package com.iamskorpz.watchioiptv.feature.sports

import android.content.Context
import androidx.core.content.edit
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.feature.sports.v2.MatchChannelConfidence
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsChannelEvidenceSource
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CachedSportsMatch(
    val providerId: String,
    val fixtureKey: String,
    val epgFingerprint: String,
    val state: SportsBroadcastState,
    val candidates: List<CachedChannelMatch>,
    val fetchedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

data class CachedChannelMatch(
    val channelId: String,
    val score: Int,
    val confidence: SportsMatchConfidence,
    val matchedProgrammeTitle: String?,
    val reasons: List<String>,
    val v2Confidence: MatchChannelConfidence?,
    val broadcasterName: String?,
    val broadcasterCountry: String?,
    val isBackup: Boolean,
    val quality: String?,
    val evidenceSource: SportsChannelEvidenceSource,
)

interface SportsMatchCache {
    fun get(providerId: ProviderId, fixtureKey: String): CachedSportsMatch?
    fun put(value: CachedSportsMatch)
    fun clear()
    fun invalidateProvider(providerId: ProviderId) {
        clear()
    }
    fun cleanup(nowEpochMs: Long, maximumEntries: Int = 1_000)
}

class InMemorySportsMatchCache : SportsMatchCache {
    private val values = ConcurrentHashMap<String, CachedSportsMatch>()
    override fun get(providerId: ProviderId, fixtureKey: String) = values["${providerId.value}:$fixtureKey"]
    override fun put(value: CachedSportsMatch) { values["${value.providerId}:${value.fixtureKey}"] = value }
    override fun clear() = values.clear()
    override fun invalidateProvider(providerId: ProviderId) {
        val prefix = "${providerId.value}:"
        values.keys.removeIf { it.startsWith(prefix) }
    }
    override fun cleanup(nowEpochMs: Long, maximumEntries: Int) {
        values.entries.removeIf { it.value.expiresAtEpochMs < nowEpochMs }
        values.values.sortedBy { it.fetchedAtEpochMs }.take((values.size - maximumEntries).coerceAtLeast(0)).forEach { values.remove("${it.providerId}:${it.fixtureKey}") }
    }
}

class PersistentSportsMatchCache(context: Context) : SportsMatchCache {
    private val preferences = context.getSharedPreferences("sports_match_cache_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Synchronized override fun get(providerId: ProviderId, fixtureKey: String): CachedSportsMatch? = preferences.getString("${providerId.value}:$fixtureKey", null)?.let { runCatching { json.decodeFromString<MatchEntry>(it).toDomain() }.getOrNull() }
    @Synchronized override fun put(value: CachedSportsMatch) { preferences.edit { putString("${value.providerId}:${value.fixtureKey}", json.encodeToString(MatchEntry.from(value))) } }
    @Synchronized override fun clear() = preferences.edit { clear() }
    @Synchronized override fun invalidateProvider(providerId: ProviderId) {
        val prefix = "${providerId.value}:"
        val matchingKeys = preferences.all.keys.filter { it.startsWith(prefix) }
        if (matchingKeys.isNotEmpty()) {
            preferences.edit {
                matchingKeys.forEach { remove(it) }
            }
        }
    }
    @Synchronized override fun cleanup(nowEpochMs: Long, maximumEntries: Int) {
        val rows = preferences.all.mapNotNull { (key, raw) -> (raw as? String)?.let { runCatching { json.decodeFromString<MatchEntry>(it) }.getOrNull() }?.let { key to it } }
        preferences.edit {
            rows.filter { it.second.expiresAtEpochMs < nowEpochMs }.forEach { remove(it.first) }
            rows.sortedBy { it.second.fetchedAtEpochMs }.take((rows.size - maximumEntries).coerceAtLeast(0)).forEach { remove(it.first) }
        }
    }
}

@Serializable private data class MatchEntry(val providerId: String, val fixtureKey: String, val epgFingerprint: String, val state: String, val candidates: List<ChannelEntry>, val fetchedAtEpochMs: Long, val expiresAtEpochMs: Long) {
    fun toDomain() = CachedSportsMatch(providerId, fixtureKey, epgFingerprint, SportsBroadcastState.valueOf(state), candidates.map(ChannelEntry::toDomain), fetchedAtEpochMs, expiresAtEpochMs)
    companion object { fun from(value: CachedSportsMatch) = MatchEntry(value.providerId, value.fixtureKey, value.epgFingerprint, value.state.name, value.candidates.map(ChannelEntry::from), value.fetchedAtEpochMs, value.expiresAtEpochMs) }
}

@Serializable private data class ChannelEntry(val channelId: String, val score: Int, val confidence: String, val title: String?, val reasons: List<String>, val v2Confidence: String?, val broadcaster: String?, val country: String?, val backup: Boolean, val quality: String?, val evidence: String) {
    fun toDomain() = CachedChannelMatch(channelId, score, SportsMatchConfidence.valueOf(confidence), title, reasons, v2Confidence?.let(MatchChannelConfidence::valueOf), broadcaster, country, backup, quality, SportsChannelEvidenceSource.valueOf(evidence))
    companion object { fun from(value: CachedChannelMatch) = ChannelEntry(value.channelId, value.score, value.confidence.name, value.matchedProgrammeTitle, value.reasons, value.v2Confidence?.name, value.broadcasterName, value.broadcasterCountry, value.isBackup, value.quality, value.evidenceSource.name) }
}

internal fun SportsChannelCandidate.toCached() = CachedChannelMatch(channel.id, score, confidence, matchedProgrammeTitle, reasons, v2Confidence, broadcasterName, broadcasterCountry, isBackup, quality, evidenceSource)

internal fun CachedChannelMatch.restore(channels: Map<String, com.iamskorpz.watchioiptv.data.live.LiveTvChannel>): SportsChannelCandidate? = channels[channelId]?.let {
    SportsChannelCandidate(it, score, confidence, matchedProgrammeTitle, reasons, v2Confidence, broadcasterName, broadcasterCountry, isBackup, quality, evidenceSource)
}
