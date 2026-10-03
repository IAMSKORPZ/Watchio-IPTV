package com.iamskorpz.watchioiptv.feature.sports.v2

import com.iamskorpz.watchioiptv.core.security.SecretStore

interface ApiFootballCredentialStore {
    suspend fun get(): String?
    suspend fun save(value: String)
    suspend fun remove()
}

class SecureApiFootballCredentialStore(
    private val secretStore: SecretStore,
) : ApiFootballCredentialStore {
    override suspend fun get(): String? = secretStore.getSecret(KEY)

    override suspend fun save(value: String) {
        val normalized = value.trim()
        if (normalized.isBlank()) remove() else secretStore.putSecret(KEY, normalized)
    }

    override suspend fun remove() = secretStore.removeSecret(KEY)

    private companion object {
        const val KEY = "sports.api_football.api_key"
    }
}
