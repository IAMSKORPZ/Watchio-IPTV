package com.watchioiptv.nativeapp.data.epg

import com.watchioiptv.nativeapp.core.database.EpgChannelEntity
import com.watchioiptv.nativeapp.core.database.LiveStreamEntity
import com.watchioiptv.nativeapp.core.database.M3uItemEntity
import com.watchioiptv.nativeapp.core.util.TextNormalizer

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
        val normalized = TextNormalizer.normalizeForSearch(displayName)
        channels.firstOrNull { it.normalizedName == normalized }?.let { return it.epgChannelId }
        val compact = compact(displayName)
        val matches = channels.filter { compact(it.displayName) == compact }
        return matches.singleOrNull()?.epgChannelId
    }

    fun compact(value: String): String =
        TextNormalizer.normalizeForSearch(value)
            .replace(Regex("\\b(uk|us|ca|fr|de|es|it|tr|ar)\\b"), "")
            .replace(Regex("\\b(fhd|uhd|hd|sd|vip|vm|4k|backup|raw)\\b"), "")
            .replace(Regex("[^a-z0-9]"), "")
}
