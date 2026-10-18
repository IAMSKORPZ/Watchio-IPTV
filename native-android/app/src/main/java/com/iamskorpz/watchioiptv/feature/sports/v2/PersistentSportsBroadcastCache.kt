package com.iamskorpz.watchioiptv.feature.sports.v2

import android.content.Context
import androidx.core.content.edit
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PersistentSportsBroadcastCache(context: Context) : SportsBroadcastCache {
    private val preferences = context.getSharedPreferences("sports_broadcast_cache_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    override fun get(source: SportsDataSource, fixtureIdentity: SportsSourceIdentity): CachedBroadcasts? =
        preferences.getString(key(source, fixtureIdentity), null)?.let { raw ->
            runCatching { json.decodeFromString<CacheEntry>(raw).toDomain() }.getOrNull()
        }

    @Synchronized
    override fun put(source: SportsDataSource, fixtureIdentity: SportsSourceIdentity, value: CachedBroadcasts) {
        preferences.edit { putString(key(source, fixtureIdentity), json.encodeToString(CacheEntry.from(value))) }
    }

    @Synchronized
    override fun clear() = preferences.edit { clear() }

    @Synchronized
    override fun cleanup(now: Instant, maximumEntries: Int) {
        val parsed = preferences.all.mapNotNull { (key, value) ->
            val entry = (value as? String)?.let { runCatching { json.decodeFromString<CacheEntry>(it) }.getOrNull() }
            if (entry == null || entry.expiresAtEpochMs < now.minusSeconds(24 * 60 * 60).toEpochMilli()) {
                preferences.edit { remove(key) }; null
            } else key to entry
        }
        if (parsed.size > maximumEntries) {
            preferences.edit {
                parsed.sortedBy { it.second.fetchedAtEpochMs }.take(parsed.size - maximumEntries).forEach { remove(it.first) }
            }
        }
    }

    private fun key(source: SportsDataSource, fixture: SportsSourceIdentity) = "${source.value}:${fixture.stableKey}"
}

@Serializable
private data class CacheEntry(
    val broadcasts: List<BroadcastEntry>,
    val fetchedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val negative: Boolean,
) {
    fun toDomain() = CachedBroadcasts(broadcasts.map(BroadcastEntry::toDomain), Instant.ofEpochMilli(fetchedAtEpochMs), Instant.ofEpochMilli(expiresAtEpochMs), negative)
    companion object {
        fun from(value: CachedBroadcasts) = CacheEntry(value.broadcasts.map(BroadcastEntry::from), value.fetchedAt.toEpochMilli(), value.expiresAt.toEpochMilli(), value.negative)
    }
}

@Serializable
private data class BroadcastEntry(
    val source: String,
    val sourceId: String,
    val canonicalId: String?,
    val displayName: String,
    val countryOrRegion: String?,
    val logoUrl: String?,
    val fetchedAtEpochMs: Long,
    val evidence: List<EvidenceEntry>,
) {
    fun toDomain() = SportsBroadcast(SportsSourceIdentity(SportsDataSource(source), sourceId), canonicalId, displayName, countryOrRegion, logoUrl, Instant.ofEpochMilli(fetchedAtEpochMs), evidence.map(EvidenceEntry::toDomain))
    companion object { fun from(value: SportsBroadcast) = BroadcastEntry(value.identity.source.value, value.identity.sourceId, value.canonicalId, value.displayName, value.countryOrRegion, value.logoUrl, value.fetchedAt.toEpochMilli(), value.evidence.map(EvidenceEntry::from)) }
}

@Serializable
private data class EvidenceEntry(val source: String, val sourceId: String, val fixtureId: String, val confidence: String, val region: String?, val fetchedAtEpochMs: Long) {
    fun toDomain() = SportsBroadcastEvidence(SportsSourceIdentity(SportsDataSource(source), sourceId), fixtureId, BroadcastReconciliationConfidence.valueOf(confidence), region, Instant.ofEpochMilli(fetchedAtEpochMs))
    companion object { fun from(value: SportsBroadcastEvidence) = EvidenceEntry(value.sourceIdentity.source.value, value.sourceIdentity.sourceId, value.sourceFixtureId, value.confidence.name, value.originalCountryOrRegion, value.fetchedAt.toEpochMilli()) }
}
