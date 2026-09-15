package com.iamskorpz.watchioiptv

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.iamskorpz.watchioiptv.feature.sports.FootballDataApi
import com.iamskorpz.watchioiptv.feature.sports.FootballDataValidationResult
import com.iamskorpz.watchioiptv.feature.sports.RemoteFootballDataCredentialValidator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class FootballDataCredentialValidatorTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    @Test fun successfulRequestIsValid() = assertStatus(200, FootballDataValidationResult.Valid)
    @Test fun unauthorizedRequestIsInvalid() = assertStatus(401, FootballDataValidationResult.Invalid)
    @Test fun forbiddenRequestIsInvalid() = assertStatus(403, FootballDataValidationResult.Invalid)
    @Test fun rateLimitedRequestIsNotInvalid() = assertStatus(429, FootballDataValidationResult.RateLimited)
    @Test fun serverFailureCannotBeVerified() = assertStatus(503, FootballDataValidationResult.NetworkError)

    private fun assertStatus(code: Int, expected: FootballDataValidationResult) = runTest {
        server.enqueue(MockResponse().setResponseCode(code))
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FootballDataApi::class.java)
        val actual = RemoteFootballDataCredentialValidator(api).validate("test-token")
        assertEquals(expected, actual)
        val request = server.takeRequest()
        assertEquals("/v4/competitions/PL", request.path)
        assertEquals("test-token", request.getHeader("X-Auth-Token"))
    }
}
