package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.sports.v2.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SportsV2SourceContractTest {
    @Test fun unsupportedCapabilitiesReturnTypedResult() = runTest {
        val source = object : FixtureSource {
            override val source = SportsDataSource("future-vendor")
            override val capabilities = emptySet<SportsSourceCapability>()
            override suspend fun fixtures(from: java.time.LocalDate, toInclusive: java.time.LocalDate) = SportsSourceResult.NoData
        }
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unsupported), source.fixture("1"))
        assertEquals(SportsSourceResult.Failure(SportsSourceError.Unsupported), source.liveFixtures())
    }
}
