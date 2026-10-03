package com.iamskorpz.watchioiptv.feature.sports.v2

import com.iamskorpz.watchioiptv.core.security.SecretStore

class SoccersApiCredentials(val username: String, val token: String)

interface SoccersApiCredentialStore {
    suspend fun get(): SoccersApiCredentials?
    suspend fun save(username: String, token: String)
    suspend fun remove()
}

class SecureSoccersApiCredentialStore(private val secretStore: SecretStore) : SoccersApiCredentialStore {
    override suspend fun get(): SoccersApiCredentials? {
        val username = secretStore.getSecret(USERNAME_KEY)?.trim().orEmpty()
        val token = secretStore.getSecret(TOKEN_KEY)?.trim().orEmpty()
        return if (username.isBlank() || token.isBlank()) null else SoccersApiCredentials(username, token)
    }

    override suspend fun save(username: String, token: String) {
        val cleanUsername = username.trim()
        val cleanToken = token.trim()
        if (cleanUsername.isBlank() || cleanToken.isBlank()) {
            remove()
        } else {
            secretStore.putSecret(USERNAME_KEY, cleanUsername)
            secretStore.putSecret(TOKEN_KEY, cleanToken)
        }
    }

    override suspend fun remove() {
        secretStore.removeSecret(USERNAME_KEY)
        secretStore.removeSecret(TOKEN_KEY)
    }

    private companion object {
        const val USERNAME_KEY = "sports.soccers_api.username"
        const val TOKEN_KEY = "sports.soccers_api.api_token"
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
