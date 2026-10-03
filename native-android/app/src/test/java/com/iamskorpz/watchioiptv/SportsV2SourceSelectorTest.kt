package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.sports.v2.*
import java.time.LocalDate
import org.junit.Assert.assertSame
import org.junit.Test

class SportsV2SourceSelectorTest {
    @Test fun footballDataRemainsDefaultAndApiFootballRequiresExplicitSelection() {
        val footballData = StubSource(SportsDataSource.FootballData)
        val apiFootball = StubSource(SportsDataSource.ApiFootball)
        assertSame(footballData, SportsV2SourceSelector(footballData, apiFootball).selected())
        assertSame(apiFootball, SportsV2SourceSelector(footballData, apiFootball, SportsV2SourceSelection.ApiFootball).selected())
    }

    private class StubSource(override val source: SportsDataSource) : FixtureSource {
        override val capabilities = emptySet<SportsSourceCapability>()
        override suspend fun fixtures(from: LocalDate, toInclusive: LocalDate) = SportsSourceResult.NoData
    }
}
