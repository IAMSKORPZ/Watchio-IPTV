package com.iamskorpz.watchioiptv.feature.sports

import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.feature.tvguide.TvGuideRepository
import com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideWindow
import java.time.LocalDate
import java.time.ZoneId
import com.iamskorpz.watchioiptv.feature.sports.v2.ApiFootballCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.v2.BroadcastRepository
import com.iamskorpz.watchioiptv.feature.sports.v2.BroadcasterAliasCatalogue
import com.iamskorpz.watchioiptv.feature.sports.v2.CachedFixtureRepository
import com.iamskorpz.watchioiptv.feature.sports.v2.MatchChannelConfidence
import com.iamskorpz.watchioiptv.feature.sports.v2.ProviderChannelMatcherV2
import com.iamskorpz.watchioiptv.feature.sports.v2.ProviderChannelIndex
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsBroadcast
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsDataSource
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsFixtureSnapshot
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsFixtureState
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsSourceError
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsSourceIdentity
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsSourceResult
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsCapability
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsSettingsStore
import com.iamskorpz.watchioiptv.feature.sports.v2.enabledSources
import com.iamskorpz.watchioiptv.feature.sports.v2.manualMappings
import com.iamskorpz.watchioiptv.feature.sports.v2.EpgFixtureDiscovery
import com.iamskorpz.watchioiptv.feature.sports.v2.EpgFixtureIndex
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsChannelEvidenceSource
import com.iamskorpz.watchioiptv.feature.tvguide.TvGuideData
import java.util.concurrent.ConcurrentHashMap
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsCacheDiagnostics
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SportsRepository(
    private val scheduleSource: FootballScheduleSource,
    private val tvGuideRepository: TvGuideRepository,
    private val footballV2Repository: CachedFixtureRepository? = null,
    private val apiFootballV2Repository: CachedFixtureRepository? = null,
    private val apiFootballCredentialStore: ApiFootballCredentialStore? = null,
    private val broadcastRepository: BroadcastRepository? = null,
    private val matcherV2: ProviderChannelMatcherV2? = null,
    private val settingsStore: SportsSettingsStore? = null,
    private val epgDiscovery: EpgFixtureDiscovery = EpgFixtureDiscovery(),
    private val matchCache: SportsMatchCache = InMemorySportsMatchCache(),
    private val matchingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val discoveredCandidates = ConcurrentHashMap<String, List<SportsChannelCandidate>>()

    fun invalidateBroadcastDiscoveryCache() { discoveredCandidates.clear(); matchCache.clear() }

    fun invalidateProviderChannelCache(providerId: ProviderId? = null) {
        if (providerId == null) {
            invalidateBroadcastDiscoveryCache()
        } else {
            val prefix = "${providerId.value}:"
            discoveredCandidates.keys.removeIf { it.startsWith(prefix) }
            matchCache.invalidateProvider(providerId)
        }
    }
    suspend fun schedule(date: LocalDate): Result<SportsDateSchedule> = withContext(matchingDispatcher) {
        val apiConfigured = !apiFootballCredentialStore?.get().isNullOrBlank()
        val configuredOrder = settingsStore?.read()?.enabledSources(SportsCapability.FIXTURES).orEmpty()
        val configuredRepositories = configuredOrder.mapNotNull { source -> when (source) {
            SportsDataSource.ApiFootball -> apiFootballV2Repository?.takeIf { apiConfigured }
            SportsDataSource.FootballData -> footballV2Repository
            else -> null
        }}
        if (settingsStore != null && configuredRepositories.isEmpty()) return@withContext Result.failure(SportsScheduleException.MissingCredential)
        val repositories = configuredRepositories.ifEmpty { listOfNotNull(if (apiConfigured) apiFootballV2Repository else footballV2Repository, footballV2Repository).distinct() }
        for (v2 in repositories) {
            when (val result = v2.fixtures(date, date)) {
                is SportsSourceResult.Success -> return@withContext Result.success(result.data.toLegacySchedule(date))
                SportsSourceResult.NoData -> return@withContext Result.success(SportsDateSchedule(date, emptyList()))
                is SportsSourceResult.Failure -> if (result.error is SportsSourceError.RateLimited && repositories.lastOrNull() == v2) {
                    val retry = result.error.retryAt?.toEpochMilli() ?: System.currentTimeMillis() + 60_000L
                    return@withContext Result.failure(SportsScheduleException.RateLimited(retry))
                }
            }
        }
        scheduleSource.getFixtures(date).map { fixtures ->
        SportsDateSchedule(
            date,
            fixtures.groupBy { it.competitionId }.map { (id, rows) ->
                SportsCompetition(id, rows.first().competitionName, SportsCompetitionCatalog.displayOrder(id), rows.sortedBy { it.kickoffUtc })
            }.sortedWith(compareBy<SportsCompetition> { it.displayOrder }.thenBy { it.name }),
        )
    }
    }

    suspend fun broadcasts(
        fixtures: List<SportsFixture>,
        forceRefresh: Boolean = false,
        onFixture: suspend (String, SportsBroadcastState, List<SportsChannelCandidate>, List<SportsBroadcast>) -> Unit = { _, _, _, _ -> },
    ): SportsBroadcastDiscovery = withContext(matchingDispatcher) {
        val repository = broadcastRepository ?: return@withContext SportsBroadcastDiscovery(
            states = fixtures.associate { it.id to SportsBroadcastState.SERVICE_UNAVAILABLE },
        )
        val found = mutableMapOf<String, List<SportsBroadcast>>()
        val states = mutableMapOf<String, SportsBroadcastState>()
        val candidates = mutableMapOf<String, List<SportsChannelCandidate>>()
        val providerId = tvGuideRepository.selectedProviderId()
        val epgStartedAt = System.nanoTime()
        val guide = providerId?.let { loadGuide(it, fixtures) }
        val catalogue = providerId?.let { tvGuideRepository.liveCatalogueForProvider(it) }
        currentCoroutineContext().ensureActive()
        val indexChannels = mergeCatalogueWithGuide(catalogue?.channels.orEmpty(), guide?.channels.orEmpty())
        val providerIndex = if (guide != null) matcherV2?.buildIndex(indexChannels, guide.programmes, catalogue?.categoryNamesById().orEmpty()) else null
        val epgIndex = guide?.let { epgDiscovery.buildIndex(it.channels, it.programmes) }
        SportsCacheDiagnostics.epgIndexBuild((System.nanoTime() - epgStartedAt) / 1_000_000L)
        val now = System.currentTimeMillis()
        matchCache.cleanup(now)
        val epgFingerprint = guide?.epgFingerprint(indexChannels, catalogue?.categories.orEmpty()).orEmpty()
        val channelsById = indexChannels.associate { it.channelId to it.liveChannel }
        fixtures.forEach { fixture ->
            currentCoroutineContext().ensureActive()
            val cached = if (forceRefresh) null else providerId?.let { matchCache.get(it, fixture.id) }
                ?.takeIf { it.expiresAtEpochMs > now && it.epgFingerprint == epgFingerprint }
            if (cached != null) {
                val restored = cached.candidates.mapNotNull { it.restore(channelsById) }
                if (restored.size == cached.candidates.size) {
                    states[fixture.id] = cached.state
                    if (restored.isNotEmpty()) candidates[fixture.id] = restored
                    discoveredCandidates[cacheKey(ProviderId(cached.providerId), fixture)] = restored
                    onFixture(fixture.id, cached.state, restored, emptyList())
                    return@forEach
                }
            }
            val v2 = fixture.v2Fixture ?: fixture.toV2()
            val teamChannels = providerIndex?.let { matcherV2?.matchTeamChannels(v2, it) }.orEmpty()
            val fixtureChannels = providerIndex?.let { matcherV2?.matchFixtureChannels(v2, it) }.orEmpty()
            when (val result = repository.broadcasts(v2, forceRefresh)) {
                is SportsSourceResult.Success -> {
                    found[fixture.id] = result.data
                    val official = if (providerId != null && providerIndex != null) matchBroadcastsForProvider(providerId, fixture, result.data, providerIndex) else emptyList()
                    val matches = mergeCandidates(official, teamChannels, fixtureChannels).ifEmpty { epgIndex?.let { discoverFromEpg(fixture, it) }.orEmpty() }
                    if (matches.isNotEmpty()) {
                        candidates[fixture.id] = matches
                        providerId?.let { discoveredCandidates[cacheKey(it, fixture)] = matches }
                    }
                    states[fixture.id] = when {
                        official.isNotEmpty() || teamChannels.isNotEmpty() || fixtureChannels.isNotEmpty() -> SportsBroadcastState.CHANNEL_MATCH_AVAILABLE
                        matches.isNotEmpty() -> SportsBroadcastState.EPG_MATCH_AVAILABLE
                        else -> SportsBroadcastState.BROADCASTER_FOUND_NO_CHANNEL
                    }
                }
                else -> {
                    val matches = mergeCandidates(emptyList(), teamChannels, fixtureChannels).ifEmpty { epgIndex?.let { discoverFromEpg(fixture, it) }.orEmpty() }
                    if (matches.isNotEmpty()) {
                        candidates[fixture.id] = matches
                        providerId?.let { discoveredCandidates[cacheKey(it, fixture)] = matches }
                        states[fixture.id] = if (teamChannels.isNotEmpty() || fixtureChannels.isNotEmpty()) SportsBroadcastState.CHANNEL_MATCH_AVAILABLE else SportsBroadcastState.EPG_MATCH_AVAILABLE
                    } else states[fixture.id] = result.toBroadcastState()
                }
            }
            if (providerId != null) {
                val resultCandidates = candidates[fixture.id].orEmpty()
                val state = states.getValue(fixture.id)
                val ttl = matchTtlMs(fixture, state, now)
                matchCache.put(CachedSportsMatch(providerId.value, fixture.id, epgFingerprint, state, resultCandidates.map(SportsChannelCandidate::toCached), now, now + ttl))
            }
            onFixture(fixture.id, states.getValue(fixture.id), candidates[fixture.id].orEmpty(), found[fixture.id].orEmpty())
        }
        SportsBroadcastDiscovery(found, states, candidates)
    }

    suspend fun candidates(fixture: SportsFixture): Result<List<SportsChannelCandidate>> = runCatching {
        val providerId = tvGuideRepository.selectedProviderId() ?: error("Add a provider first.")
        candidatesForProvider(providerId, fixture)
    }

    suspend fun candidatesForProvider(providerId: ProviderId, fixture: SportsFixture): List<SportsChannelCandidate> = withContext(matchingDispatcher) {
        discoveredCandidates[cacheKey(providerId, fixture)]?.let { return@withContext it }
        val guide = loadGuide(providerId, listOf(fixture))
        val catalogue = tvGuideRepository.liveCatalogueForProvider(providerId)
        val broadcasts = broadcastRepository?.broadcasts(fixture.v2Fixture ?: fixture.toV2())
        val indexChannels = mergeCatalogueWithGuide(catalogue.channels, guide.channels)
        val providerIndex = matcherV2?.buildIndex(indexChannels, guide.programmes, catalogue.categoryNamesById())
        val epgIndex = epgDiscovery.buildIndex(guide.channels, guide.programmes)
        val official = if (broadcasts is SportsSourceResult.Success && providerIndex != null) matchBroadcastsForProvider(providerId, fixture, broadcasts.data, providerIndex) else emptyList()
        val teamChannels = providerIndex?.let { matcherV2?.matchTeamChannels(fixture.v2Fixture ?: fixture.toV2(), it) }.orEmpty()
        val fixtureChannels = providerIndex?.let { matcherV2?.matchFixtureChannels(fixture.v2Fixture ?: fixture.toV2(), it) }.orEmpty()
        mergeCandidates(official, teamChannels, fixtureChannels).ifEmpty { discoverFromEpg(fixture, epgIndex) }.also { discoveredCandidates[cacheKey(providerId, fixture)] = it }
    }

    private suspend fun matchBroadcastsForProvider(
        providerId: ProviderId,
        fixture: SportsFixture,
        broadcasts: List<SportsBroadcast>,
        index: ProviderChannelIndex,
    ): List<SportsChannelCandidate> {
        val v2Matcher = matcherV2
        if (v2Matcher != null) {
            val mappings = settingsStore?.read()?.manualMappings(providerId.value).orEmpty()
            return v2Matcher.match(fixture.v2Fixture ?: fixture.toV2(), broadcasts, index, mappings).map { row ->
                SportsChannelCandidate(
                    channel = row.channel,
                    score = row.score,
                    confidence = when (row.confidence) {
                        MatchChannelConfidence.VERIFIED -> SportsMatchConfidence.High
                        MatchChannelConfidence.STRONG -> SportsMatchConfidence.High
                        MatchChannelConfidence.POSSIBLE -> SportsMatchConfidence.Medium
                    },
                    matchedProgrammeTitle = row.epgEvidence.programmeTitle,
                    reasons = row.reasons,
                    v2Confidence = row.confidence,
                    broadcasterName = row.broadcasterName,
                    broadcasterCountry = row.broadcasterCountry,
                    isBackup = row.isBackup,
                    quality = row.quality,
                    evidenceSource = row.evidenceSource,
                )
            }
        }
        return emptyList()
    }

    private fun discoverFromEpg(fixture: SportsFixture, index: EpgFixtureIndex): List<SportsChannelCandidate> =
        epgDiscovery.discover(fixture.v2Fixture ?: fixture.toV2(), index).map { row ->
            SportsChannelCandidate(
                channel = row.channel.liveChannel,
                score = row.score,
                confidence = if (row.confidence == MatchChannelConfidence.VERIFIED) SportsMatchConfidence.High else SportsMatchConfidence.Medium,
                matchedProgrammeTitle = row.programme.title,
                reasons = row.reasons,
                v2Confidence = row.confidence,
                broadcasterName = null,
                isBackup = false,
                quality = null,
                evidenceSource = SportsChannelEvidenceSource.PROVIDER_EPG,
            )
        }

    private fun mergeCandidates(
        official: List<SportsChannelCandidate>,
        teamChannels: List<com.iamskorpz.watchioiptv.feature.sports.v2.MatchChannelCandidate>,
        fixtureChannels: List<com.iamskorpz.watchioiptv.feature.sports.v2.MatchChannelCandidate> = emptyList(),
    ): List<SportsChannelCandidate> = (official + (teamChannels + fixtureChannels).map { row ->
        SportsChannelCandidate(
            channel = row.channel,
            score = row.score,
            confidence = if (row.confidence == MatchChannelConfidence.VERIFIED) SportsMatchConfidence.High else SportsMatchConfidence.Medium,
            matchedProgrammeTitle = row.epgEvidence.programmeTitle,
            reasons = row.reasons,
            v2Confidence = row.confidence,
            broadcasterName = row.broadcasterName,
            broadcasterCountry = row.broadcasterCountry,
            isBackup = row.isBackup,
            quality = row.quality,
            evidenceSource = row.evidenceSource,
        )
    }).groupBy { it.channel.id }
        .map { (_, duplicates) -> duplicates.maxBy { it.score } }
        .sortedByDescending { it.score }

    private suspend fun loadGuide(providerId: ProviderId, fixtures: List<SportsFixture>): TvGuideData {
        val first = fixtures.minOfOrNull { it.kickoffUtc.toEpochMilli() } ?: System.currentTimeMillis()
        val last = fixtures.maxOfOrNull { it.kickoffUtc.toEpochMilli() } ?: first
        val day = java.time.Instant.ofEpochMilli(first).atZone(ZoneId.systemDefault()).toLocalDate()
        return tvGuideRepository.guideForProvider(
            providerId,
            WatchioGuideWindow(first - 45L * 60_000L, last + 3L * 60L * 60_000L, day, emptyList()),
            first,
            "all",
        )
    }

    private fun cacheKey(providerId: ProviderId, fixture: SportsFixture) = "${providerId.value}:${fixture.id}:${fixture.kickoffUtc.toEpochMilli()}"

    private fun TvGuideData.epgFingerprint(
        indexedChannels: List<com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideChannel>,
        providerCategories: List<com.iamskorpz.watchioiptv.data.live.LiveTvCategory>,
    ): String {
        var value = 65L // V6.5 direct provider-catalogue matcher model.
        providerCategories.forEach { value = value * 31 + it.id.hashCode(); value = value * 31 + it.name.hashCode() }
        indexedChannels.forEach {
            value = value * 31 + it.channelId.hashCode()
            value = value * 31 + it.displayName.hashCode()
            value = value * 31 + (it.category?.hashCode() ?: 0)
            value = value * 31 + (it.epgChannelId?.hashCode() ?: 0)
        }
        programmes.values.flatten().forEach { value = value * 31 + it.programmeId.hashCode(); value = value * 31 + it.startUtcMs; value = value * 31 + it.endUtcMs }
        return value.toString(16)
    }

    private fun matchTtlMs(fixture: SportsFixture, state: SportsBroadcastState, now: Long): Long = when {
        state == SportsBroadcastState.NO_BROADCASTER_DATA || state == SportsBroadcastState.FIXTURE_NOT_MATCHED -> 30L * 60_000L
        fixture.status == SportsFixtureStatus.Live -> 3L * 60_000L
        fixture.status == SportsFixtureStatus.Finished || fixture.status == SportsFixtureStatus.Cancelled -> 24L * 60L * 60_000L
        fixture.kickoffUtc.toEpochMilli() <= now + 2L * 60L * 60_000L -> 15L * 60_000L
        else -> 6L * 60L * 60_000L
    }

    suspend fun activeProviderChannels(): Pair<String?, List<com.iamskorpz.watchioiptv.data.live.LiveTvChannel>> {
        val providerId = tvGuideRepository.selectedProviderId() ?: return null to emptyList()
        val now = System.currentTimeMillis()
        val date = java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val guide = tvGuideRepository.guideForProvider(
            providerId,
            WatchioGuideWindow(now - 60_000L, now + 60L * 60_000L, date, emptyList()),
            now,
            "all",
        )
        return providerId.value to guide.channels.map { it.liveChannel }
    }

}

private fun TvGuideData.categoryNamesById(): Map<String, String> = buildMap {
    categories.forEach { category ->
        put(category.id, category.name)
        category.sourceCategoryId?.let { put(it, category.name) }
    }
}

private fun com.iamskorpz.watchioiptv.feature.tvguide.TvGuideRepository.ProviderLiveCatalogue.categoryNamesById(): Map<String, String> = buildMap {
    categories.forEach { category ->
        put(category.id, category.name)
        category.sourceCategoryId?.let { put(it, category.name) }
    }
}

private fun mergeCatalogueWithGuide(
    catalogue: List<com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideChannel>,
    guide: List<com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideChannel>,
): List<com.iamskorpz.watchioiptv.feature.tvguide.WatchioGuideChannel> {
    val guideById = guide.associateBy { it.channelId }
    return catalogue.map { channel -> guideById[channel.channelId] ?: channel }
}

internal fun SportsSourceResult<List<SportsBroadcast>>.toBroadcastState(hasChannelMatch: Boolean = false): SportsBroadcastState = when (this) {
    is SportsSourceResult.Success -> if (hasChannelMatch) SportsBroadcastState.CHANNEL_MATCH_AVAILABLE else SportsBroadcastState.BROADCASTER_FOUND_NO_CHANNEL
    SportsSourceResult.NoData -> SportsBroadcastState.NO_BROADCASTER_DATA
    is SportsSourceResult.Failure -> when (error) {
        SportsSourceError.NoBroadcasterData -> SportsBroadcastState.NO_BROADCASTER_DATA
        SportsSourceError.FixtureNotMatched -> SportsBroadcastState.FIXTURE_NOT_MATCHED
        SportsSourceError.UnsupportedTier -> SportsBroadcastState.UNSUPPORTED_SUBSCRIPTION
        SportsSourceError.Unauthorized -> SportsBroadcastState.AUTHENTICATION_FAILED
        SportsSourceError.NetworkFailure -> SportsBroadcastState.NETWORK_FAILURE
        else -> SportsBroadcastState.SERVICE_UNAVAILABLE
    }
}

private fun SportsFixtureSnapshot.toLegacySchedule(date: LocalDate): SportsDateSchedule {
    val rows = fixtures.filter { it.kickoff.atZone(ZoneId.systemDefault()).toLocalDate() == date }.map { it.toLegacy() }
    return SportsDateSchedule(date, rows.groupBy { it.competitionId }.map { (id, fixtures) ->
        SportsCompetition(id, fixtures.first().competitionName, SportsCompetitionCatalog.displayOrder(id), fixtures.sortedBy { it.kickoffUtc })
    }.sortedWith(compareBy<SportsCompetition> { it.displayOrder }.thenBy { it.name }))
}

private fun com.iamskorpz.watchioiptv.feature.sports.v2.SportsFixture.toLegacy() = SportsFixture(
    id = identity.stableKey,
    competitionId = competition.code ?: competition.identity.sourceId,
    competitionName = competition.name,
    kickoffUtc = kickoff,
    homeTeam = homeTeam.displayName,
    awayTeam = awayTeam.displayName,
    status = when (state) {
        SportsFixtureState.Live, SportsFixtureState.Halftime -> SportsFixtureStatus.Live
        SportsFixtureState.Finished -> SportsFixtureStatus.Finished
        SportsFixtureState.Postponed, SportsFixtureState.Suspended -> SportsFixtureStatus.Postponed
        SportsFixtureState.Cancelled -> SportsFixtureStatus.Cancelled
        else -> SportsFixtureStatus.Scheduled
    },
    homeScore = score?.current?.home ?: score?.fulltime?.home,
    awayScore = score?.current?.away ?: score?.fulltime?.away,
    homeLogoUrl = homeTeam.logoUrl,
    awayLogoUrl = awayTeam.logoUrl,
    minute = minute,
    v2Fixture = this,
)

private fun SportsFixture.toV2(): com.iamskorpz.watchioiptv.feature.sports.v2.SportsFixture {
    val source = SportsDataSource.FootballData
    return com.iamskorpz.watchioiptv.feature.sports.v2.SportsFixture(
        identity = SportsSourceIdentity(source, id),
        competition = com.iamskorpz.watchioiptv.feature.sports.v2.SportsCompetition(SportsSourceIdentity(source, competitionId), name = competitionName, code = competitionId),
        homeTeam = com.iamskorpz.watchioiptv.feature.sports.v2.SportsTeam(SportsSourceIdentity(source, "$id-home"), displayName = homeTeam, logoUrl = homeLogoUrl),
        awayTeam = com.iamskorpz.watchioiptv.feature.sports.v2.SportsTeam(SportsSourceIdentity(source, "$id-away"), displayName = awayTeam, logoUrl = awayLogoUrl),
        kickoff = kickoffUtc,
        state = when (status) {
            SportsFixtureStatus.Live -> SportsFixtureState.Live
            SportsFixtureStatus.Finished -> SportsFixtureState.Finished
            SportsFixtureStatus.Postponed -> SportsFixtureState.Postponed
            SportsFixtureStatus.Cancelled -> SportsFixtureState.Cancelled
            SportsFixtureStatus.Scheduled -> SportsFixtureState.Scheduled
        },
        minute = minute,
        fetchedAt = java.time.Instant.now(),
    )
}
