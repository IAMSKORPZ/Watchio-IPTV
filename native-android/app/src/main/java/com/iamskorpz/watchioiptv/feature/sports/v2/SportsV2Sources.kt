package com.iamskorpz.watchioiptv.feature.sports.v2

import java.time.Instant
import java.time.LocalDate

enum class SportsSourceCapability { DateRange, FixtureById, LiveFixtures, Broadcasts }

sealed interface SportsSourceError {
    data class RateLimited(val retryAt: Instant?) : SportsSourceError
    data object Unauthorized : SportsSourceError
    data object Unavailable : SportsSourceError
    data object NetworkFailure : SportsSourceError
    data object ParseFailure : SportsSourceError
    data object Unsupported : SportsSourceError
}

sealed interface SportsSourceResult<out T> {
    data class Success<T>(val data: T, val fetchedAt: Instant) : SportsSourceResult<T>
    data object NoData : SportsSourceResult<Nothing>
    data class Failure(val error: SportsSourceError) : SportsSourceResult<Nothing>
}

interface FixtureSource {
    val source: SportsDataSource
    val capabilities: Set<SportsSourceCapability>

    suspend fun fixtures(from: LocalDate, toInclusive: LocalDate): SportsSourceResult<List<SportsFixture>>
    suspend fun fixture(sourceFixtureId: String): SportsSourceResult<SportsFixture> =
        SportsSourceResult.Failure(SportsSourceError.Unsupported)
    suspend fun liveFixtures(): SportsSourceResult<List<SportsFixture>> =
        SportsSourceResult.Failure(SportsSourceError.Unsupported)
}

interface BroadcastSource {
    val source: SportsDataSource
    val capabilities: Set<SportsSourceCapability>

    suspend fun broadcasts(fixture: SportsFixture): SportsSourceResult<List<SportsBroadcast>> =
        SportsSourceResult.Failure(SportsSourceError.Unsupported)
}
