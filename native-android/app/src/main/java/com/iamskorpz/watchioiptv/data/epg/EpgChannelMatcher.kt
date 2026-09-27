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
