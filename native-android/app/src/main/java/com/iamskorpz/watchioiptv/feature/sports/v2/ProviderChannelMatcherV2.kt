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
    val evidenceSource: SportsChannelEvidenceSource = SportsChannelEvidenceSource.OFFICIAL_BROADCASTER,
)

@Serializable data class BroadcasterAliasFile(val schemaVersion: Int, val broadcasters: List<BroadcasterAliasEntry>)
@Serializable data class BroadcasterAliasEntry(val canonical: String, val serviceType: String, val aliases: List<String>)

data class CanonicalBroadcaster(
    val name: String,
    val serviceType: BroadcastServiceType,
    val aliases: Set<String>,
)

class BroadcasterAliasCatalogue private constructor(val entries: List<CanonicalBroadcaster>) {
    private val entriesByAlias = buildMap<String, CanonicalBroadcaster> {
        this@BroadcasterAliasCatalogue.entries.forEach { entry ->
            entry.aliases.forEach { alias -> if (alias !in this) put(alias, entry) }
        }
    }

    fun find(value: String): CanonicalBroadcaster? {
        return entriesByAlias[normalizePhrase(value)]
    }

    companion object {
        private val strictJson = Json { ignoreUnknownKeys = false }

        fun parse(raw: String): BroadcasterAliasCatalogue {
            val file = strictJson.decodeFromString<BroadcasterAliasFile>(raw)
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

class IndexedProviderChannel internal constructor(
    val channel: WatchioGuideChannel,
    val normalized: NormalizedProviderChannel,
    val programmes: List<WatchioGuideProgramme>,
    val categoryName: String?,
)

class ProviderChannelIndex internal constructor(
    val providerId: String?,
    internal val channels: List<IndexedProviderChannel>,
    internal val channelsById: Map<String, IndexedProviderChannel>,
    private val channelsByToken: Map<String, List<IndexedProviderChannel>>,
    internal val eplChannels: List<IndexedProviderChannel>,
    internal val footballEventChannels: List<IndexedProviderChannel>,
) {
    internal fun candidatesFor(broadcaster: CanonicalBroadcaster): List<IndexedProviderChannel> {
        return broadcaster.aliases.asSequence()
            .mapNotNull { alias -> alias.splitToSequence(' ')
                .filter { it.length >= 2 }
                .mapNotNull(channelsByToken::get)
                .minByOrNull(List<IndexedProviderChannel>::size)
            }
            .flatten()
            .distinctBy { it.channel.channelId }
            .toList()
    }
}

object ProviderChannelNormalizer {
    private val qualityTokens = setOf("hd", "fhd", "uhd", "4k", "hevc", "h265", "sd")
    private val operationalTokens = setOf("backup", "bkp", "vip", "raw")
    private val countryPrefixes = setOf("uk", "gb", "eng", "england", "us", "usa", "ca", "canada", "au", "australia", "ie", "ireland", "nz")

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
    fun buildIndex(
        channels: List<WatchioGuideChannel>,
        programmes: Map<String, List<WatchioGuideProgramme>>,
        categoryNames: Map<String, String> = emptyMap(),
    ): ProviderChannelIndex {
        val provider = channels.firstOrNull()?.providerId
        val indexed = channels.asSequence()
            .filter { provider != null && it.providerId == provider }
            .map { channel ->
                IndexedProviderChannel(
                    channel = channel,
                    normalized = ProviderChannelNormalizer.normalize(channel.displayName),
                    programmes = programmes[channel.channelId].orEmpty(),
                    categoryName = channel.category?.let(categoryNames::get),
                )
            }
            .toList()
        return ProviderChannelIndex(
            providerId = provider?.value,
            channels = indexed,
            channelsById = indexed.associateBy { it.channel.channelId },
            channelsByToken = indexed.flatMap { row -> row.normalized.tokens.map { it to row } }.groupBy({ it.first }, { it.second }),
            eplChannels = indexed.filter { isEplCategory(it.categoryName) },
            footballEventChannels = indexed.filter { isFootballEventCategory(it.categoryName) },
        )
    }

    fun matchTeamChannels(
        fixture: SportsFixture,
        index: ProviderChannelIndex,
    ): List<MatchChannelCandidate> {
        if (!fixture.isPremierLeagueFixture()) return emptyList()
        val eplChannels = index.eplChannels
        if (eplChannels.isEmpty()) return emptyList()
        val teams = listOf(fixture.homeTeam.displayName, fixture.awayTeam.displayName)
        return teams.flatMap { team ->
            val aliases = teamChannelAliases(team)
            eplChannels.mapNotNull { indexed ->
                if (indexed.normalized.isExcludedTeamVariant()) return@mapNotNull null
                val alias = aliases.firstOrNull { matchesTeamChannelName(indexed.normalized.value, it) } ?: return@mapNotNull null
                val epg = epgVerifier.verify(fixture, indexed.programmes)
                if (epg.conflictingFixture) return@mapNotNull null
                val verified = epg.teamsMatched && epg.kickoffMatched
                MatchChannelCandidate(
                    channel = indexed.channel.liveChannel,
                    broadcasterName = team,
                    broadcasterCountry = "GB",
                    serviceType = BroadcastServiceType.LINEAR_TV,
                    score = if (verified) 100 else 50,
                    confidence = if (verified) MatchChannelConfidence.VERIFIED else MatchChannelConfidence.POSSIBLE,
                    epgEvidence = epg,
                    reasons = buildList {
                        add("EPL team channel: $alias")
                        if (!verified) add("Team channel - unverified")
                        addAll(epg.reasons)
                    },
                    isBackup = indexed.normalized.isBackup,
                    quality = indexed.normalized.quality,
                    evidenceSource = SportsChannelEvidenceSource.TEAM_CHANNEL,
                )
            }
        }.groupBy { it.channel.id }
            .map { (_, duplicates) -> duplicates.maxWith(candidateComparator) }
            .sortedWith(candidateComparator.reversed())
    }

    fun matchFixtureChannels(fixture: SportsFixture, index: ProviderChannelIndex): List<MatchChannelCandidate> {
        val homeAliases = fixtureEventAliases(fixture.homeTeam.displayName)
        val awayAliases = fixtureEventAliases(fixture.awayTeam.displayName)
        return index.footballEventChannels.mapNotNull { indexed ->
            if (!fixtureSeparatorRegex.containsMatchIn(indexed.channel.displayName)) return@mapNotNull null
            val value = indexed.normalized.value
            val home = homeAliases.firstOrNull { containsPhrase(value, it) } ?: return@mapNotNull null
            val away = awayAliases.firstOrNull { containsPhrase(value, it) } ?: return@mapNotNull null
            if (home == away || indexed.normalized.isExcludedTeamVariant()) return@mapNotNull null
            val epg = epgVerifier.verify(fixture, indexed.programmes)
            if (epg.conflictingFixture) return@mapNotNull null
            val verified = epg.teamsMatched && epg.kickoffMatched
            MatchChannelCandidate(
                channel = indexed.channel.liveChannel,
                broadcasterName = "${fixture.homeTeam.displayName} v ${fixture.awayTeam.displayName}",
                broadcasterCountry = null,
                serviceType = BroadcastServiceType.LINEAR_TV,
                score = if (verified) 100 else 65,
                confidence = if (verified) MatchChannelConfidence.VERIFIED else MatchChannelConfidence.POSSIBLE,
                epgEvidence = epg,
                reasons = buildList {
                    add("Both fixture teams in provider channel")
                    if (!verified) add("Match-specific channel - unverified")
                    addAll(epg.reasons)
                },
                isBackup = indexed.normalized.isBackup,
                quality = indexed.normalized.quality,
                evidenceSource = SportsChannelEvidenceSource.MATCH_SPECIFIC_CHANNEL,
            )
        }.groupBy { it.channel.id }
            .map { (_, duplicates) -> duplicates.maxWith(candidateComparator) }
            .sortedWith(candidateComparator.reversed())
    }

    fun match(
        fixture: SportsFixture,
        broadcasts: List<SportsBroadcast>,
        channels: List<WatchioGuideChannel>,
        programmes: Map<String, List<WatchioGuideProgramme>>,
        manualMappings: List<ManualChannelMapping> = emptyList(),
    ): List<MatchChannelCandidate> = match(
        fixture = fixture,
        broadcasts = broadcasts,
        index = buildIndex(channels, programmes),
        manualMappings = manualMappings,
    )

    fun match(
        fixture: SportsFixture,
        broadcasts: List<SportsBroadcast>,
        index: ProviderChannelIndex,
        manualMappings: List<ManualChannelMapping> = emptyList(),
    ): List<MatchChannelCandidate> {
        val preferredBroadcasts = broadcasts.sortedBy { if (isUkRegion(it.countryOrRegion)) 0 else 1 }
        val automatic = preferredBroadcasts.flatMap { broadcast ->
            val canonical = catalogue.find(broadcast.displayName) ?: return@flatMap emptyList()
            if (canonical.serviceType != BroadcastServiceType.LINEAR_TV) return@flatMap emptyList()
            index.candidatesFor(canonical).mapNotNull { channel -> candidate(fixture, broadcast, canonical, channel) }
        }
        val manual = preferredBroadcasts.flatMap { broadcast ->
            manualMappings.asSequence()
                .filter { normalizePhrase(it.broadcaster) == normalizePhrase(broadcast.displayName) }
                .mapNotNull { mapping -> index.channelsById[mapping.channelId]?.let { indexed ->
                    val channel = indexed.channel
                    val epg = epgVerifier.verify(fixture, indexed.programmes)
                    if (epg.conflictingFixture) null else MatchChannelCandidate(
                        channel = channel.liveChannel,
                        broadcasterName = broadcast.displayName,
                        broadcasterCountry = broadcast.countryOrRegion,
                        serviceType = BroadcastServiceType.LINEAR_TV,
                        score = (90 + epg.score).coerceAtMost(100),
                        confidence = if (epg.teamsMatched && epg.kickoffMatched) MatchChannelConfidence.VERIFIED else MatchChannelConfidence.STRONG,
                        epgEvidence = epg,
                        reasons = listOf("Saved manual mapping") + epg.reasons,
                        isBackup = false,
                        quality = mapping.preferredQuality ?: indexed.normalized.quality,
                    )
                }}.toList()
        }
        return (manual + automatic)
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
        indexed: IndexedProviderChannel,
    ): MatchChannelCandidate? {
        val channel = indexed.channel
        val normalized = indexed.normalized
        val matchedAlias = canonical.aliases.firstOrNull { alias ->
            normalized.value == alias || containsPhrase(normalized.value, alias)
        } ?: canonical.aliases.firstOrNull { alias -> conservativeAliasMatch(normalized.value, alias) } ?: return null
        if (numberConflict(normalizePhrase(canonical.name), normalized.value)) return null
        val exact = normalized.value == normalizePhrase(canonical.name)
        val epg = epgVerifier.verify(fixture, indexed.programmes)
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

private val combiningMarksRegex = Regex("\\p{M}+")
private val nonAlphaNumericRegex = Regex("[^a-z0-9]+")
private val repeatedWhitespaceRegex = Regex("\\s+")
private val clubSuffixRegex = Regex("\\b(fc|afc)\\b")
private val versusRegex = Regex("\\b(v|vs|versus)\\b")
private val fixtureSeparatorRegex = Regex("(?i)\\s(?:v|vs\\.?|versus|-)\\s")

internal fun normalizePhrase(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(combiningMarksRegex, "")
    .lowercase()
    .replace("&", " and ")
    .replace("+", " plus ")
    .replace(nonAlphaNumericRegex, " ")
    .trim().replace(repeatedWhitespaceRegex, " ")

internal fun teamAliases(value: String): Set<String> {
    val base = normalizePhrase(value).replace(clubSuffixRegex, " ").trim().replace(repeatedWhitespaceRegex, " ")
    return setOf(base) + when (base) {
        "manchester united" -> setOf("man utd", "man united")
        "manchester city" -> setOf("man city")
        "tottenham hotspur" -> setOf("tottenham", "spurs")
        "wolverhampton wanderers" -> setOf("wolves")
        "paris saint germain" -> setOf("psg")
        "internazionale", "inter milan" -> setOf("internazionale", "inter milan", "inter")
        "sk slavia praha", "slavia praha" -> setOf("sk slavia praha", "slavia praha", "slavia prague")
        "sabah fk" -> setOf("sabah fk", "sabah")
        else -> emptySet()
    }
}

internal fun isEplCategory(value: String?): Boolean {
    val normalized = normalizePhrase(value.orEmpty())
    val tokens = normalized.split(' ').filter(String::isNotBlank).toSet()
    if (tokens.any { it in excludedCategoryTokens }) return false
    return "epl" in tokens || containsPhrase(normalized, "premier league")
}

internal fun isFootballEventCategory(value: String?): Boolean {
    val normalized = normalizePhrase(value.orEmpty())
    val tokens = normalized.split(' ').filter(String::isNotBlank).toSet()
    if (tokens.any { it in excludedCategoryTokens }) return false
    return tokens.any { it in footballCategoryTokens } || footballCategoryPhrases.any { containsPhrase(normalized, it) }
}

internal fun teamChannelAliases(value: String): Set<String> {
    val base = normalizePhrase(value).replace(clubSuffixRegex, " ").trim().replace(repeatedWhitespaceRegex, " ")
    return (setOf(base, "$base fc") + when (base) {
        "manchester united" -> setOf("man united", "man utd", "man u", "mufc")
        "manchester city" -> setOf("man city", "mcfc")
        "liverpool" -> setOf("lfc")
        "arsenal" -> setOf("arsenal fc")
        "chelsea" -> setOf("chelsea fc")
        "tottenham hotspur" -> setOf("tottenham", "spurs")
        "newcastle united" -> setOf("newcastle", "nufc")
        "nottingham forest" -> setOf("nottm forest", "nffc")
        "aston villa" -> setOf("villa")
        "west ham united" -> setOf("west ham")
        "brighton and hove albion" -> setOf("brighton")
        "wolverhampton wanderers" -> setOf("wolves")
        "crystal palace" -> setOf("palace")
        "afc bournemouth", "bournemouth" -> setOf("afc bournemouth", "bournemouth")
        else -> emptySet()
    }).filter { it.length >= 3 }.toSet()
}

private fun SportsFixture.isPremierLeagueFixture(): Boolean {
    val code = competition.code?.uppercase()
    val name = normalizePhrase(competition.name)
    return code == "PL" || name == "premier league" || name == "english premier league" || name == "epl"
}

private val excludedTeamVariantTokens = setOf(
    "women", "womens", "ladies", "u18", "u19", "u20", "u21", "u23", "youth", "academy", "reserve", "reserves", "highlights", "tv",
)

private val excludedCategoryTokens = setOf("replay", "highlights", "women", "womens", "youth", "academy", "reserve", "reserves")
private val footballCategoryTokens = setOf("epl", "spfl", "uefa", "football", "soccer")
private val footballCategoryPhrases = setOf("premier league", "fa cup", "la liga", "serie a", "ligue 1", "champions league")
private val teamChannelSuffixTokens = setOf("epl", "english", "premier", "league", "hub", "channel", "channels")

private fun NormalizedProviderChannel.isExcludedTeamVariant(): Boolean = tokens.any { it in excludedTeamVariantTokens }

private fun matchesTeamChannelName(channel: String, alias: String): Boolean {
    if (channel == alias) return true
    if (!channel.startsWith("$alias ")) return false
    val suffix = channel.removePrefix("$alias ").split(' ').filter(String::isNotBlank)
    return suffix.isNotEmpty() && suffix.all { it in teamChannelSuffixTokens }
}

private fun fixtureEventAliases(value: String): Set<String> = teamChannelAliases(value)
    .filter { alias -> alias.length >= 5 && (alias.contains(' ') || alias !in ambiguousSingleTeamAliases) }
    .toSet()

private val ambiguousSingleTeamAliases = setOf("city", "united", "villa", "palace", "spurs", "wolves")

private fun competitionAliases(value: SportsCompetition): Set<String> = setOfNotNull(normalizePhrase(value.name), value.code?.let(::normalizePhrase)) + when (value.code?.uppercase()) {
    "PL" -> setOf("premier league", "epl", "english premier league")
    "CL" -> setOf("champions league", "uefa champions league", "ucl")
    "ELC" -> setOf("championship", "efl championship")
    else -> emptySet()
}

private fun containsPhrase(text: String, phrase: String) = text == phrase || " $text ".contains(" $phrase ")
private fun hasVersus(text: String) = versusRegex.containsMatchIn(text)
private fun isUkRegion(value: String?) = value == "GB" || value?.startsWith("GB-") == true
private fun numberConflict(canonical: String, channel: String): Boolean {
    val expected = canonical.split(' ').firstOrNull { it.length == 1 && it[0] in '1'..'9' } ?: return false
    val actual = channel.split(' ').firstOrNull { it.length == 1 && it[0] in '1'..'9' } ?: return false
    return expected != actual
}
private fun qualityRank(value: String?) = when (value) { "UHD", "4K" -> 4; "FHD" -> 3; "HD" -> 2; "SD" -> 1; else -> 0 }

private fun conservativeAliasMatch(channel: String, alias: String): Boolean {
    if (alias.length < 10 || channel.length < 10) return false
    val channelWords = channel.split(' ')
    val aliasWords = alias.split(' ')
    val channelNumbers = channelWords.filter { word -> word.isNotEmpty() && word.all(Char::isDigit) }.toSet()
    val aliasNumbers = aliasWords.filter { word -> word.isNotEmpty() && word.all(Char::isDigit) }.toSet()
    if (channelNumbers != aliasNumbers) return false
    if (channelWords.size != aliasWords.size) return false
    val differences = channelWords.zip(aliasWords).sumOf { (left, right) -> boundedEditDistance(left, right, 1) }
    return differences <= 1
}

private fun boundedEditDistance(left: String, right: String, limit: Int): Int {
    if (kotlin.math.abs(left.length - right.length) > limit) return limit + 1
    var previous = IntArray(right.length + 1) { it }
    for (i in left.indices) {
        val current = IntArray(right.length + 1); current[0] = i + 1
        for (j in right.indices) current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (left[i] == right[j]) 0 else 1)
        if (current.minOrNull()!! > limit) return limit + 1
        previous = current
    }
    return previous[right.length]
}
