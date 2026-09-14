package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.data.xtream.EndpointAttemptFailure
import com.watchioiptv.nativeapp.data.xtream.WatchioEndpointConfigParser
import com.watchioiptv.nativeapp.data.xtream.WatchioEndpointConfigSource
import com.watchioiptv.nativeapp.data.xtream.WatchioEndpointManager
import com.watchioiptv.nativeapp.feature.home.managedServerLabel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WatchioEndpointManagerTest {
    @Test fun productionEndpointsNormalizeInPriorityOrder() {
        val parsed = WatchioEndpointConfigParser.parse(
            """{"version":1,"remoteConfigUrl":"https://iamskorpz.github.io/Watchio_Website/img/watchio_endpoints.json","endpoints":[{"id":"primary","url":"http://xololive.watch:8880","priority":0,"enabled":true},{"id":"backup","url":"http://mediatitans.live:8880","priority":1,"enabled":true}]}""",
        )
        assertEquals(listOf("http://xololive.watch:8880", "http://mediatitans.live:8880"), parsed.endpoints.map { it.url })
        assertEquals("https://iamskorpz.github.io/Watchio_Website/img/watchio_endpoints.json", parsed.remoteConfigUrl)
    }

    @Test fun httpProviderEndpointsAcceptedButRemoteConfigRemainsHttpsOnly() {
        assertEquals("http://xololive.watch:8880", WatchioEndpointConfigParser.normalize("HTTP://XOLOLIVE.WATCH:8880/"))
        expectParseFailure("""{"version":1,"remoteConfigUrl":"http://config.watchio.test/endpoints.json","endpoints":[]}""")
    }

    @Test fun unsupportedAndCredentialBearingEndpointsRejected() {
        assertNull(WatchioEndpointConfigParser.normalize("ftp://xololive.watch:8880"))
        assertNull(WatchioEndpointConfigParser.normalize("http://user:password@xololive.watch:8880"))
        assertNull(WatchioEndpointConfigParser.normalize("http://xololive.watch:8880?username=user&password=secret"))
    }

    @Test fun primarySuccessUsesPrimary() = runBlocking {
        val source = source(config("https://one.test", "https://two.test"))
        val result = WatchioEndpointManager(source).resolve { it.url }
        assertEquals("https://one.test", result.value)
    }

    @Test fun networkFailureUsesBackup() = runBlocking {
        val attempts = mutableListOf<String>()
        val result = WatchioEndpointManager(source(config("https://one.test", "https://two.test"))).resolve {
            attempts += it.url
            if (attempts.size == 1) throw EndpointAttemptFailure.Unavailable(IOException())
            it.url
        }
        assertEquals("https://two.test", result.value)
    }

    @Test fun serverFailureUsesBackup() = networkFailureUsesBackup()

    @Test fun invalidCredentialsDoNotRotate() = runBlocking {
        var attempts = 0
        expectFailure<EndpointAttemptFailure.Account> {
            WatchioEndpointManager(source(config("https://one.test", "https://two.test"))).resolve {
                attempts++; throw EndpointAttemptFailure.Account("Username or password is incorrect.")
            }
        }
        assertEquals(1, attempts)
    }

    @Test fun accountIssueDoesNotRotate() = invalidCredentialsDoNotRotate()

    @Test fun allUnavailableIsBounded() = runBlocking {
        var attempts = 0
        expectFailure<IllegalStateException> {
            WatchioEndpointManager(source(config("https://one.test", "https://two.test"))).resolve {
                attempts++; throw EndpointAttemptFailure.Unavailable(IOException())
            }
        }
        assertEquals(2, attempts)
    }

    @Test fun endpointsNormalizeAndDeduplicate() {
        val parsed = WatchioEndpointConfigParser.parse(config("HTTPS://ONE.TEST/", "https://one.test", "https://two.test/"))
        assertEquals(listOf("https://one.test", "https://two.test"), parsed.endpoints.map { it.url })
    }

    @Test fun lastWorkingPreferred() = runBlocking {
        val source = source(config("https://one.test", "https://two.test"), last = "https://two.test")
        assertEquals("https://two.test", WatchioEndpointManager(source).candidates().first().url)
    }

    @Test fun failedLastWorkingFallsBack() = runBlocking {
        val source = source(config("https://one.test", "https://two.test"), last = "https://two.test")
        val result = WatchioEndpointManager(source).resolve {
            if (it.url == "https://two.test") throw EndpointAttemptFailure.Unavailable(IOException())
            it.url
        }
        assertEquals("https://one.test", result.value)
    }

    @Test fun malformedRemoteRejected() = runBlocking {
        val source = FakeSource(remoteValue = "{}", cacheValue = null, bundledValue = config("https://one.test"))
        assertEquals("https://one.test", WatchioEndpointManager(source).candidates().single().url)
    }

    @Test fun invalidRemoteDoesNotReplaceCache() = runBlocking {
        val source = FakeSource("{}", config("https://cached.test"), config("https://bundled.test"))
        assertEquals("https://cached.test", WatchioEndpointManager(source).candidates().single().url)
        assertNull(source.savedCache)
    }

    @Test fun remoteUnavailableUsesCache() = runBlocking {
        val source = FakeSource(null, config("https://cached.test"), config("https://bundled.test"))
        assertEquals("https://cached.test", WatchioEndpointManager(source).candidates().single().url)
    }

    @Test fun remote404UsesCachedValidConfig() = runBlocking {
        val source = FakeSource(null, config("https://cached.test"), config("https://bundled.test"))
        assertEquals("https://cached.test", WatchioEndpointManager(source).candidates().single().url)
        assertNull(source.savedCache)
    }

    @Test fun noCacheUsesBundled() = runBlocking {
        assertEquals("https://bundled.test", WatchioEndpointManager(source(config("https://bundled.test"))).candidates().single().url)
    }

    @Test fun validRemoteReplacesBundledAndHonorsPriority() = runBlocking {
        val remote = objectConfig(endpoint("backup", "https://two.test", 20), endpoint("primary", "https://one.test", 10))
        val source = FakeSource(remote, null, config("https://bundled.test"))
        assertEquals(listOf("https://one.test", "https://two.test"), WatchioEndpointManager(source).candidates().map { it.url })
        assertEquals(remote, source.savedCache)
    }

    @Test fun disabledAndDuplicateRemoteEndpointsAreIgnored() = runBlocking {
        val remote = objectConfig(
            endpoint("disabled", "https://disabled.test", 0, false),
            endpoint("first", "HTTPS://ONE.TEST/", 1),
            endpoint("duplicate", "https://one.test", 2),
        )
        assertEquals(listOf("https://one.test"), WatchioEndpointManager(FakeSource(remote, null, config("https://bundled.test"))).candidates().map { it.url })
    }

    @Test fun remoteHttpEndpointIsAllowed() = runBlocking {
        val remote = objectConfig(endpoint("primary", "http://one.test:8880", 0))
        assertEquals("http://one.test:8880", WatchioEndpointManager(FakeSource(remote, null, config("https://bundled.test"))).candidates().single().url)
    }

    @Test fun refreshReplacesOldCandidatesAndDiscardsRemovedLastWorking() = runBlocking {
        val source = FakeSource(config("https://old.test"), null, config("https://bundled.test"), "https://old.test")
        val manager = WatchioEndpointManager(source)
        assertEquals("https://old.test", manager.candidates().single().url)
        source.remoteValue = config("https://new.test")
        assertEquals("https://new.test", manager.refreshConfig().single().url)
        assertNull(source.lastWorking())
    }

    @Test fun refreshRetainsConfiguredLastWorkingPreference() = runBlocking {
        val source = FakeSource(config("https://one.test", "https://two.test"), null, config("https://bundled.test"), "https://two.test")
        val manager = WatchioEndpointManager(source)
        manager.candidates()
        source.remoteValue = config("https://one.test", "https://two.test", "https://three.test")
        assertEquals("https://two.test", manager.refreshConfig().first().url)
    }

    @Test fun manualSwitchUsesCurrentRefreshedCandidates() = runBlocking {
        val source = FakeSource(config("https://one.test", "https://two.test"), null, config("https://bundled.test"))
        val manager = WatchioEndpointManager(source)
        assertEquals("https://two.test", manager.nextCandidate("https://one.test")?.url)
        source.remoteValue = config("https://three.test", "https://four.test", "https://five.test")
        manager.refreshConfig()
        assertEquals("https://four.test", manager.nextCandidate("https://three.test")?.url)
        assertEquals("https://three.test", manager.nextCandidate("https://five.test")?.url)
    }

    @Test fun stableEndpointLabelsNeverExposeUrls() {
        assertEquals("MediaTitans", managedServerLabel("MediaTitans"))
        assertEquals("AS8880", managedServerLabel("AS8880"))
        assertEquals("AW999", managedServerLabel("AW999"))
        assertEquals("Xolo", managedServerLabel("Xolo"))
        assertEquals("Server", managedServerLabel("http://private-host.test:8880"))
    }

    @Test fun automaticFailoverUpdatesAuthoritativeActiveEndpoint() = runBlocking {
        val manager = WatchioEndpointManager(source(objectConfig(
            endpoint("MediaTitans", "https://one.test", 1),
            endpoint("AS8880", "https://two.test", 2),
        )))
        val selected = manager.resolve {
            if (it.id == "MediaTitans") throw EndpointAttemptFailure.Unavailable(IOException())
            it.url
        }
        assertEquals("AS8880", selected.endpoint.id)
        assertEquals("AS8880", manager.activeEndpoint.value?.id)
    }

    @Test fun successfulManualSwitchUpdatesActiveEndpoint() = runBlocking {
        val manager = WatchioEndpointManager(source(objectConfig(
            endpoint("MediaTitans", "https://one.test", 1),
            endpoint("AS8880", "https://two.test", 2),
        )))
        manager.restoreActive("https://one.test")
        manager.switch("https://one.test") { it.url }
        assertEquals("AS8880", manager.activeEndpoint.value?.id)
    }

    @Test fun namedManualSwitchUsesRequestedEndpointAndPublishesSafeChoiceList() = runBlocking {
        val manager = WatchioEndpointManager(source(objectConfig(
            endpoint("MediaTitans", "https://one.test", 1),
            endpoint("AW999", "https://two.test", 2),
        )))
        manager.refreshConfig()
        val selected = manager.switchTo("AW999") { it.url }
        assertEquals("AW999", selected.endpoint.id)
        assertEquals(listOf("MediaTitans", "AW999"), manager.configuredEndpoints.value.map { it.id })
        assertEquals(true, manager.activeEndpointOnline.value)
    }

    @Test fun activeEndpointBecomesOfflineWhenEveryConnectionAttemptFails() = runBlocking {
        val manager = WatchioEndpointManager(source(objectConfig(
            endpoint("MediaTitans", "https://one.test", 1),
        )))
        manager.restoreActive("https://one.test")
        expectFailure<IllegalStateException> {
            manager.resolve("https://one.test") { throw EndpointAttemptFailure.Unavailable(IOException()) }
        }
        assertEquals(false, manager.activeEndpointOnline.value)
    }

    @Test fun failedManualSwitchPreservesActiveEndpoint() = runBlocking {
        val manager = WatchioEndpointManager(source(objectConfig(
            endpoint("MediaTitans", "https://one.test", 1),
            endpoint("AS8880", "https://two.test", 2),
        )))
        manager.restoreActive("https://one.test")
        expectFailure<EndpointAttemptFailure.Unavailable> {
            manager.switch("https://one.test") { throw EndpointAttemptFailure.Unavailable(IOException()) }
        }
        assertEquals("MediaTitans", manager.activeEndpoint.value?.id)
    }

    @Test fun unavailableRemoteAndEmptyBundledConfigHasNoProductionFallback() = runBlocking {
        val manager = WatchioEndpointManager(FakeSource(null, null, objectConfig()))
        assertTrue(manager.candidates().isEmpty())
    }

    @Test fun cachedEndpointIdentityRestoresActiveLabel() = runBlocking {
        val cached = objectConfig(endpoint("MediaTitans", "https://one.test", 1))
        val manager = WatchioEndpointManager(FakeSource(null, cached, objectConfig()))
        assertEquals("MediaTitans", manager.restoreActive("https://one.test")?.id)
    }

    @Test fun noEndpointsFailsSafely() = runBlocking {
        assertTrue(expectFailure<IllegalStateException> { WatchioEndpointManager(source(config())).resolve { it.url } }.message!!.contains("service configuration"))
    }

    @Test fun concurrentCallsAreSerialized() = runBlocking {
        var active = 0
        var peak = 0
        val manager = WatchioEndpointManager(source(config("https://one.test")))
        (1..10).map { async { manager.resolve { active++; peak = maxOf(peak, active); delay(5); active--; it.url } } }.awaitAll()
        assertEquals(1, peak)
    }

    private fun source(bundled: String, last: String? = null) = FakeSource(null, null, bundled, last)
    private fun config(vararg endpoints: String) = """{"version":1,"endpoints":[${endpoints.joinToString(",") { "\"$it\"" }}]}"""
    private fun endpoint(id: String, url: String, priority: Int, enabled: Boolean = true) =
        """{"id":"$id","url":"$url","priority":$priority,"enabled":$enabled}"""
    private fun objectConfig(vararg endpoints: String) = """{"version":1,"endpoints":[${endpoints.joinToString(",")}]}"""

    private fun expectParseFailure(raw: String) {
        try {
            WatchioEndpointConfigParser.parse(raw)
            throw AssertionError("Expected invalid endpoint configuration")
        } catch (_: IllegalArgumentException) {
            Unit
        }
    }

    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit): T {
        return try {
            block()
            throw AssertionError("Expected ${T::class.java.simpleName}")
        } catch (failure: Throwable) {
            if (failure !is T) throw failure
            failure
        }
    }

    private class FakeSource(
        var remoteValue: String?,
        private val cacheValue: String?,
        private val bundledValue: String,
        private var last: String? = null,
    ) : WatchioEndpointConfigSource {
        var savedCache: String? = null
        override suspend fun remote() = remoteValue
        override suspend fun cached() = cacheValue
        override suspend fun bundled() = bundledValue
        override suspend fun saveCache(raw: String) { savedCache = raw }
        override fun lastWorking() = last
        override fun saveLastWorking(url: String) { last = url }
        override fun clearLastWorking() { last = null }
    }
}
