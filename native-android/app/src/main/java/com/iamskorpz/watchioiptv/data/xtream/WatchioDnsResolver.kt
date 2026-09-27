package com.iamskorpz.watchioiptv.data.xtream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class WatchioDnsEndpoint(val id: String?, val url: String?)

sealed interface WatchioDnsResult {
    data class Found(val endpoint: WatchioDnsEndpoint) : WatchioDnsResult
    data object NotFound : WatchioDnsResult
}

interface WatchioDnsResolver {
    suspend fun resolve(username: String): WatchioDnsResult
}

class WatchioDnsException(message: String, cause: Throwable? = null) : Exception(message, cause)

object WatchioDnsContract {
    private val json = Json { ignoreUnknownKeys = false }

    fun requestBody(username: String): String = buildJsonObject {
        put("username", username.trim())
        put("app", "watchio")
        put("platform", "android")
    }.toString()

    fun parseResponse(raw: String): WatchioDnsResult {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: throw WatchioDnsException("The login service returned an invalid response.")
        requireOnly(root, setOf("version", "status", "endpoint", "endpointId"))
        val version = root.string("version")?.toIntOrNull()
        if (version != 1) throw WatchioDnsException("The login service returned an unsupported response.")
        return when (root.string("status")) {
            "not_found" -> WatchioDnsResult.NotFound
            "error" -> throw WatchioDnsException("The login service is temporarily unavailable.")
            "ok" -> {
                val nested = root["endpoint"] as? JsonObject
                nested?.let { requireOnly(it, setOf("id", "url")) }
                val id = nested?.string("id") ?: root.string("endpointId")
                val url = nested?.string("url")
                if (id.isNullOrBlank() && url.isNullOrBlank()) {
                    throw WatchioDnsException("No provider is available for this account.")
                }
                WatchioDnsResult.Found(WatchioDnsEndpoint(id?.trim(), url?.trim()))
            }
            else -> throw WatchioDnsException("The login service returned an invalid response.")
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.content
    private fun requireOnly(value: JsonObject, allowed: Set<String>) {
        if (!value.keys.all { it in allowed }) throw WatchioDnsException("The login service returned an invalid response.")
    }
}

class HttpWatchioDnsResolver(
    private val client: OkHttpClient,
    private val resolverUrl: String?,
) : WatchioDnsResolver {
    override suspend fun resolve(username: String): WatchioDnsResult = withContext(Dispatchers.IO) {
        val url = resolverUrl?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) }
            ?: throw WatchioDnsException("DNS Login is not available yet. Please use Xtream login.")
        val request = Request.Builder()
            .url(url)
            .post(WatchioDnsContract.requestBody(username).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw WatchioDnsException("Unable to contact the login service. Please try again.")
                WatchioDnsContract.parseResponse(response.body?.string().orEmpty())
            }
        } catch (failure: WatchioDnsException) {
            throw failure
        } catch (failure: IOException) {
            throw WatchioDnsException("Unable to contact the login service. Please try again.", failure)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

// A real HTTPS POST backend must be supplied before production DNS Login can resolve accounts.
const val WATCHIO_DNS_RESOLVER_URL: String = ""
