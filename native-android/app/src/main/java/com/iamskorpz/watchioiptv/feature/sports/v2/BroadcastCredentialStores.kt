package com.iamskorpz.watchioiptv.feature.sports.v2

import com.iamskorpz.watchioiptv.core.security.SecretStore

class SoccersApiCredentials(val apiKey: String)

interface SoccersApiCredentialStore {
    suspend fun get(): SoccersApiCredentials?
    suspend fun save(apiKey: String)
    suspend fun remove()
}

class SecureSoccersApiCredentialStore(private val secretStore: SecretStore) : SoccersApiCredentialStore {
    override suspend fun get(): SoccersApiCredentials? {
        val apiKey = secretStore.getSecret(API_KEY)?.trim()
            .orEmpty().ifBlank { secretStore.getSecret(LEGACY_TOKEN_KEY)?.trim().orEmpty() }
        return apiKey.takeIf(String::isNotBlank)?.let(::SoccersApiCredentials)
    }

    override suspend fun save(apiKey: String) {
        val clean = apiKey.trim()
        if (clean.isBlank()) {
            remove()
        } else {
            secretStore.putSecret(API_KEY, clean)
            secretStore.removeSecret(LEGACY_USERNAME_KEY)
            secretStore.removeSecret(LEGACY_TOKEN_KEY)
        }
    }

    override suspend fun remove() {
        secretStore.removeSecret(API_KEY)
        secretStore.removeSecret(LEGACY_USERNAME_KEY)
        secretStore.removeSecret(LEGACY_TOKEN_KEY)
    }

    private companion object {
        const val API_KEY = "sports.soccers_api.api_key"
        const val LEGACY_USERNAME_KEY = "sports.soccers_api.username"
        const val LEGACY_TOKEN_KEY = "sports.soccers_api.api_token"
    }
}

interface TheSportsDbCredentialStore {
    suspend fun get(): String?
    suspend fun save(value: String)
    suspend fun remove()
}

class SecureTheSportsDbCredentialStore(private val secretStore: SecretStore) : TheSportsDbCredentialStore {
    override suspend fun get(): String? = secretStore.getSecret(KEY)?.trim()?.takeIf(String::isNotBlank)

    override suspend fun save(value: String) {
        val clean = value.trim()
        if (clean.isBlank()) remove() else secretStore.putSecret(KEY, clean)
    }

    override suspend fun remove() = secretStore.removeSecret(KEY)

    private companion object {
        const val KEY = "sports.the_sports_db.api_key"
    }
}
