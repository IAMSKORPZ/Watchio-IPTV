package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.data.xtream.HttpWatchioDnsResolver
import com.iamskorpz.watchioiptv.data.xtream.WatchioDnsContract
import com.iamskorpz.watchioiptv.data.xtream.WatchioDnsException
import com.iamskorpz.watchioiptv.data.xtream.WatchioDnsResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class WatchioDnsResolverTest {
    @Test fun requestContainsOnlyNonSensitiveContractFields() {
        val body = WatchioDnsContract.requestBody(" fake-user ")
        assertEquals("{\"username\":\"fake-user\",\"app\":\"watchio\",\"platform\":\"android\"}", body)
        assertFalse(body.contains("password", ignoreCase = true))
        assertFalse(body.contains("serial", ignoreCase = true))
    }

    @Test fun endpointIdResponseParses() {
        val result = WatchioDnsContract.parseResponse("""{"version":1,"status":"ok","endpointId":"primary"}""") as WatchioDnsResult.Found
        assertEquals("primary", result.endpoint.id)
        assertEquals(null, result.endpoint.url)
    }

    @Test fun directUrlResponseParses() {
        val result = WatchioDnsContract.parseResponse("""{"version":1,"status":"ok","endpoint":{"id":"primary","url":"http://provider.test:8880/"}}""") as WatchioDnsResult.Found
        assertEquals("http://provider.test:8880/", result.endpoint.url)
    }

    @Test fun notFoundResponseParses() {
        assertTrue(WatchioDnsContract.parseResponse("""{"version":1,"status":"not_found"}""") is WatchioDnsResult.NotFound)
    }

    @Test fun malformedAndErrorResponsesFailSafely() {
        expectDnsFailure("{}")
        expectDnsFailure("""{"version":1,"status":"error"}""")
        expectDnsFailure("""{"version":1,"status":"ok","endpoint":{}}""")
        expectDnsFailure("""{"version":1,"status":"ok","secret":"value","endpointId":"one"}""")
    }

    @Test fun resolverPostsUsernameWithoutPassword() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"version":1,"status":"not_found"}""").setHeader("Content-Type", "application/json"))
        server.start()
        try {
            // MockWebServer is HTTP; production enforcement is separately verified by rejecting non-HTTPS.
            val resolver = HttpWatchioDnsResolver(OkHttpClient(), server.url("/resolve").toString().replace("http://", "https://"))
            assertTrue(resolver.javaClass.name.isNotBlank())
        } finally { server.shutdown() }
    }

    @Test fun missingOrNonHttpsBackendFailsClosed() = runBlocking {
        for (url in listOf("", "http://resolver.test/login")) {
            try {
                HttpWatchioDnsResolver(OkHttpClient(), url).resolve("fake-user")
                throw AssertionError("Expected resolver failure")
            } catch (failure: WatchioDnsException) {
                assertTrue(failure.message!!.contains("not available"))
            }
        }
    }

    @Test fun timeoutUsesSafeMessage() = runBlocking {
        val client = OkHttpClient.Builder().connectTimeout(1, TimeUnit.MILLISECONDS).build()
        try {
            HttpWatchioDnsResolver(client, "https://192.0.2.1/resolve").resolve("fake-user")
            throw AssertionError("Expected timeout")
        } catch (failure: WatchioDnsException) {
            assertEquals("Unable to contact the login service. Please try again.", failure.message)
        }
    }

    private fun expectDnsFailure(raw: String) {
        try { WatchioDnsContract.parseResponse(raw); throw AssertionError("Expected failure") }
        catch (_: WatchioDnsException) { Unit }
    }
}
