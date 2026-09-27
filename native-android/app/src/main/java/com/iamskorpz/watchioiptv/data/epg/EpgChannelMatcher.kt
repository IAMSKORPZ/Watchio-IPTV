package com.iamskorpz.watchioiptv.data.epg

import com.iamskorpz.watchioiptv.core.database.EpgChannelEntity
import com.iamskorpz.watchioiptv.core.database.LiveStreamEntity
import com.iamskorpz.watchioiptv.core.database.M3uItemEntity
import com.iamskorpz.watchioiptv.core.util.TextNormalizer

class EpgChannelMatcher {
    fun matchXtream(stream: LiveStreamEntity, channels: List<EpgChannelEntity>): String? =
        match(stream.epgChannelId, stream.name, channels)

    fun matchM3u(item: M3uItemEntity, channels: List<EpgChannelEntity>): String? =
        match(item.tvgId, item.name, channels)

    fun match(primaryId: String?, displayName: String, channels: List<EpgChannelEntity>): String? {
        val id = primaryId?.trim()?.takeIf { it.isNotBlank() }
        if (id != null) {
            channels.firstOrNull { it.epgChannelId == id }?.let { return it.epgChannelId }
            channels.firstOrNull { it.epgChannelId.equals(id, ignoreCase = true) }?.let { return it.epgChannelId }
        }
        channels.firstOrNull { it.displayName == displayName }?.let { return it.epgChannelId }
        channels.firstOrNull { it.displayName.equals(displayName, ignoreCase = true) }?.let { return it.epgChannelId }
        val normalized = TextNormalizer.normalizeForSearch(displayName)
        channels.firstOrNull { it.normalizedName == normalized }?.let { return it.epgChannelId }
        val compact = compact(displayName)
        if (compact.isNotBlank()) {
            val nameMatches = channels.filter { compact(it.displayName) == compact }
            nameMatches.singleOrNull()?.let { return it.epgChannelId }
            val idMatches = channels.filter { compactId(it.epgChannelId) == compact }
            idMatches.singleOrNull()?.let { return it.epgChannelId }
        }
        return null
    }

    fun compact(value: String): String =
        TextNormalizer.normalizeForSearch(value)
            .replace(Regex("\\b(uk|us|ca|fr|de|es|it|tr|ar|ie|au|nz|pl|ex|al|nl|be|pt|gr)\\b"), "")
            .replace(Regex("\\b(fhd|uhd|hd|sd|vip|vm|4k|backup|raw|1080p|720p|50fps|h265|hevc|reloc)\\b"), "")
            .replace(Regex("[^a-z0-9]"), "")

    fun compactId(value: String): String =
        value.lowercase()
            .replace(Regex("\\.(uk|us|ca|fr|de|es|it|tr|ar|ie|au|nz|pl|ex|al|nl|be|pt|gr)$"), "")
            .replace(Regex("[^a-z0-9]"), "")
}

class EpgMatchIndex(
    channels: List<EpgChannelEntity>,
    private val matcher: EpgChannelMatcher = EpgChannelMatcher(),
) {
    private val byExactId = channels.associateBy { it.epgChannelId }
    private val byLowerId = channels.associateBy { it.epgChannelId.lowercase() }
    private val byExactName = channels.associateBy { it.displayName }
    private val byLowerName = channels.associateBy { it.displayName.lowercase() }
    private val byNormalizedName = channels.associateBy { it.normalizedName }
    private val byCompactName = channels
        .groupBy { matcher.compact(it.displayName) }
        .mapValues { (_, rows) -> rows.singleOrNull()?.epgChannelId }
    private val byCompactId = channels
        .groupBy { matcher.compactId(it.epgChannelId) }
        .mapValues { (_, rows) -> rows.singleOrNull()?.epgChannelId }

    fun match(primaryId: String?, displayName: String): String? {
        val id = primaryId?.trim()?.takeIf { it.isNotBlank() }
        if (id != null) {
            byExactId[id]?.let { return it.epgChannelId }
            byLowerId[id.lowercase()]?.let { return it.epgChannelId }
        }
        byExactName[displayName]?.let { return it.epgChannelId }
        byLowerName[displayName.lowercase()]?.let { return it.epgChannelId }
        byNormalizedName[TextNormalizer.normalizeForSearch(displayName)]?.let { return it.epgChannelId }
        val compact = matcher.compact(displayName)
        if (compact.isNotBlank()) {
            byCompactName[compact]?.let { return it }
            byCompactId[compact]?.let { return it }
        }
        return null
    }
}
