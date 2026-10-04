package com.iamskorpz.watchioiptv.feature.sports.v2

import com.iamskorpz.watchioiptv.data.live.LiveTvChannel
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideChannel
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideProgramme
import java.text.Normalizer
import java.time.Duration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class BroadcastServiceType { LINEAR_TV, STREAMING, RADIO, UNKNOWN }
enum class MatchChannelConfidence { VERIFIED, STRONG, POSSIBLE }

data class EpgVerificationEvidence(
    val score: Int,
    val programmeTitle: String? = null,
    val teamsMatched: Boolean = false,
    val kickoffMatched: Boolean = false,
    val competitionMatched: Boolean = false,
    val conflictingFixture: Boolean = false,
    val reasons: List<String> = emptyList(),
)

data class MatchChannelCandidate(
    val channel: LiveTvChannel,
    val broadcasterName: String,
    val broadcasterCountry: String?,
    val serviceType: BroadcastServiceType,
    val score: Int,
    val confidence: MatchChannelConfidence,
    val epgEvidence: EpgVerificationEvidence,
    val reasons: List<String>,
    val isBackup: Boolean,
    val quality: String?,
)

@Serializable data class BroadcasterAliasFile(val schemaVersion: Int, val broadcasters: List<BroadcasterAliasEntry>)
@Serializable data class BroadcasterAliasEntry(val canonical: String, val serviceType: String, val aliases: List<String>)

data class CanonicalBroadcaster(
    val name: String,
    val serviceType: BroadcastServiceType,
    val aliases: Set<String>,
)

class BroadcasterAliasCatalogue private constructor(val entries: List<CanonicalBroadcaster>) {
    fun find(value: String): CanonicalBroadcaster? {
        val normalized = normalizePhrase(value)
        return entries.firstOrNull { normalized == normalizePhrase(it.name) || normalized in it.aliases }
    }

    companion object {
        fun parse(raw: String): BroadcasterAliasCatalogue {
            val file = Json { ignoreUnknownKeys = false }.decodeFromString<BroadcasterAliasFile>(raw)
            require(file.schemaVersion == 1 && file.broadcasters.isNotEmpty())
            val entries = file.broadcasters.map { row ->
                val canonical = row.canonical.trim().also { require(it.isNotBlank()) }
                val type = BroadcastServiceType.valueOf(row.serviceType)
                val aliases = (row.aliases + canonical).map(::normalizePhrase).filter(String::isNotBlank).toSet()
                require(aliases.isNotEmpty())
                CanonicalBroadcaster(canonical, type, aliases)
            }
            require(entries.map { normalizePhrase(it.name) }.distinct().size == entries.size)
            return BroadcasterAliasCatalogue(entries)
        }
    }
}

data class NormalizedProviderChannel(
    val value: String,
    val tokens: Set<String>,
    val quality: String?,
    val isBackup: Boolean,
)

object ProviderChannelNormalizer {
    private val qualityTokens = setOf("hd", "fhd", "uhd", "4k", "hevc", "h265", "sd")
    private val operationalTokens = setOf("backup", "bkp", "vip", "raw")
    private val countryPrefixes = setOf("uk", "gb", "eng", "england")

    fun normalize(value: String): NormalizedProviderChannel {
        val words = normalizePhrase(value).split(' ').filter(String::isNotBlank).toMutableList()
        while (words.firstOrNull() in countryPrefixes) words.removeAt(0)
        val quality = words.lastOrNull { it in qualityTokens }?.uppercase()
        val backup = words.any { it in operationalTokens }
        val meaningful = words.filterNot { it in qualityTokens || it in operationalTokens }
        return NormalizedProviderChannel(meaningful.joinToString(" "), meaningful.toSet(), quality, backup)
    }
}

class EpgVerifierV2 {
    fun verify(fixture: SportsFixture, programmes: List<WatchioGuideProgramme>): EpgVerificationEvidence {
        val relevant = programmes.filter { programme ->
            programme.endUtcMs > fixture.kickoff.minus(Duration.ofMinutes(45)).toEpochMilli() &&
                programme.startUtcMs < fixture.kickoff.plus(Duration.ofHours(3)).toEpochMilli()
        }
        return relevant.map { verifyProgramme(fixture, it) }
            .maxByOrNull { it.score } ?: EpgVerificationEvidence(0, reasons = listOf("No EPG programme"))
    }

    private fun verifyProgramme(fixture: SportsFixture, programme: WatchioGuideProgramme): EpgVerificationEvidence {
        val text = normalizePhrase(programme.title)
        val home = teamAliases(fixture.homeTeam.displayName).any { containsPhrase(text, it) }
        val away = teamAliases(fixture.awayTeam.displayName).any { containsPhrase(text, it) }
        val competition = competitionAliases(fixture.competition).any { containsPhrase(text, it) }
        val spansKickoff = programme.startUtcMs <= fixture.kickoff.toEpochMilli() && programme.endUtcMs > fixture.kickoff.toEpochMilli()
        val startsNear = programme.startUtcMs in fixture.kickoff.minus(Duration.ofMinutes(45)).toEpochMilli()..fixture.kickoff.plus(Duration.ofMinutes(15)).toEpochMilli()
        val conflicting = (home xor away) && hasVersus(text)
        if (conflicting) return EpgVerificationEvidence(-50, programme.title, conflictingFixture = true, reasons = listOf("EPG describes another fixture"))
        val score = (if (home && away) 30 else 0) + (if (spansKickoff || startsNear) 15 else 0) + (if (competition) 5 else 0)
        return EpgVerificationEvidence(score, programme.title, home && away, spansKickoff || startsNear, competition, reasons = buildList {
            if (home && away) add("Both teams match EPG")
            if (spansKickoff || startsNear) add("EPG time matches kickoff")
            if (competition) add("Competition matches EPG")
        })
    }
}

class ProviderChannelMatcherV2(
    private val catalogue: BroadcasterAliasCatalogue,
    private val epgVerifier: EpgVerifierV2 = EpgVerifierV2(),
) {
    fun match(
        fixture: SportsFixture,
        broadcasts: List<SportsBroadcast>,
        channels: List<WatchioGuideChannel>,
        programmes: Map<String, List<WatchioGuideProgramme>>,
    ): List<MatchChannelCandidate> {
        val provider = channels.firstOrNull()?.providerId
        val isolatedChannels = channels.filter { provider != null && it.providerId == provider }
        val preferredBroadcasts = broadcasts.sortedBy { if (isUkRegion(it.countryOrRegion)) 0 else 1 }
        val candidates = preferredBroadcasts.flatMap { broadcast ->
            val canonical = catalogue.find(broadcast.displayName) ?: return@flatMap emptyList()
            if (canonical.serviceType != BroadcastServiceType.LINEAR_TV) return@flatMap emptyList()
            isolatedChannels.mapNotNull { channel -> candidate(fixture, broadcast, canonical, channel, programmes[channel.channelId].orEmpty()) }
        }
        return candidates
            .groupBy { it.channel.id to normalizePhrase(it.broadcasterName) }
            .map { (_, duplicates) -> duplicates.maxWith(candidateComparator) }
            .groupBy { normalizePhrase(it.broadcasterName) }
            .flatMap { (_, variants) -> variants.sortedWith(candidateComparator.reversed()).take(2) }
            .sortedWith(candidateComparator.reversed())
    }

    private fun candidate(
        fixture: SportsFixture,
        broadcast: SportsBroadcast,
        canonical: CanonicalBroadcaster,
        channel: WatchioGuideChannel,
        programmes: List<WatchioGuideProgramme>,
    ): MatchChannelCandidate? {
        val normalized = ProviderChannelNormalizer.normalize(channel.displayName)
        val matchedAlias = canonical.aliases.firstOrNull { alias ->
            normalized.value == alias || containsPhrase(normalized.value, alias)
        } ?: return null
        if (numberConflict(normalizePhrase(canonical.name), normalized.value)) return null
        val exact = normalized.value == normalizePhrase(canonical.name)
        val epg = epgVerifier.verify(fixture, programmes)
        if (epg.conflictingFixture) return null
        var score = if (exact) 60 else 55
        if (isUkRegion(broadcast.countryOrRegion)) score += 5
        if (normalized.isBackup) score -= 5
        score += epg.score
        score = score.coerceIn(0, 100)
        val confidence = when (score) {
            in 95..100 -> MatchChannelConfidence.VERIFIED
            in 80..94 -> MatchChannelConfidence.STRONG
            in 60..79 -> MatchChannelConfidence.POSSIBLE
            else -> return null
        }
        return MatchChannelCandidate(channel.liveChannel, canonical.name, broadcast.countryOrRegion, canonical.serviceType, score, confidence, epg,
            buildList {
                add(if (exact) "Exact broadcaster" else "Broadcaster alias: $matchedAlias")
                if (isUkRegion(broadcast.countryOrRegion)) add("UK broadcaster")
                addAll(epg.reasons)
                if (normalized.isBackup) add("Backup variant")
            }, normalized.isBackup, normalized.quality)
    }

    private val candidateComparator = compareBy<MatchChannelCandidate> { it.score }
        .thenBy { !it.isBackup }
        .thenBy { qualityRank(it.quality) }
        .thenByDescending { it.channel.serverOrder }
}

internal fun normalizePhrase(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace("&", " and ")
    .replace("+", " plus ")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim().replace(Regex("\\s+"), " ")

internal fun teamAliases(value: String): Set<String> {
    val base = normalizePhrase(value).replace(Regex("\\b(fc|afc)\\b"), " ").trim().replace(Regex("\\s+"), " ")
    return setOf(base) + when (base) {
        "manchester united" -> setOf("man utd", "man united")
        "manchester city" -> setOf("man city")
        "tottenham hotspur" -> setOf("tottenham", "spurs")
        "wolverhampton wanderers" -> setOf("wolves")
        "paris saint germain" -> setOf("psg")
        "internazionale", "inter milan" -> setOf("internazionale", "inter milan", "inter")
        else -> emptySet()
    }
}

private fun competitionAliases(value: SportsCompetition): Set<String> = setOfNotNull(normalizePhrase(value.name), value.code?.let(::normalizePhrase)) + when (value.code?.uppercase()) {
    "PL" -> setOf("premier league", "epl", "english premier league")
    "CL" -> setOf("champions league", "uefa champions league", "ucl")
    "ELC" -> setOf("championship", "efl championship")
    else -> emptySet()
}

private fun containsPhrase(text: String, phrase: String) = Regex("(^| )${Regex.escape(phrase)}( |$)").containsMatchIn(text)
private fun hasVersus(text: String) = Regex("\\b(v|vs|versus)\\b").containsMatchIn(text)
private fun isUkRegion(value: String?) = value == "GB" || value?.startsWith("GB-") == true
private fun numberConflict(canonical: String, channel: String): Boolean {
    val expected = Regex("\\b([1-9])\\b").find(canonical)?.groupValues?.get(1) ?: return false
    val actual = Regex("\\b([1-9])\\b").find(channel)?.groupValues?.get(1) ?: return false
    return expected != actual
}
private fun qualityRank(value: String?) = when (value) { "UHD", "4K" -> 4; "FHD" -> 3; "HD" -> 2; "SD" -> 1; else -> 0 }
