package com.watchioiptv.nativeapp.core.security

interface SecretStore {
    suspend fun putSecret(key: String, value: String)
    suspend fun getSecret(key: String): String?
    suspend fun removeSecret(key: String)
}

data class XtreamCredentials(
    val username: String,
    val password: String,
) {
    override fun toString(): String = "XtreamCredentials(username=***, password=***)"
}

class ProviderCredentialStore(
    private val secretStore: SecretStore,
) {
    suspend fun saveXtreamCredentials(providerId: String, credentials: XtreamCredentials) {
        secretStore.putSecret(providerSecretKey(providerId, "xtream_username"), credentials.username)
        secretStore.putSecret(providerSecretKey(providerId, "xtream_password"), credentials.password)
    }

    suspend fun getXtreamCredentials(providerId: String): XtreamCredentials? {
        val username = secretStore.getSecret(providerSecretKey(providerId, "xtream_username"))
        val password = secretStore.getSecret(providerSecretKey(providerId, "xtream_password"))
        if (username.isNullOrBlank() || password.isNullOrBlank()) return null
        return XtreamCredentials(username = username, password = password)
    }

    suspend fun deleteProviderSecrets(providerId: String) {
        secretStore.removeSecret(providerSecretKey(providerId, "xtream_username"))
        secretStore.removeSecret(providerSecretKey(providerId, "xtream_password"))
        secretStore.removeSecret(providerSecretKey(providerId, "api_token"))
        secretStore.removeSecret(providerSecretKey(providerId, "parental_pin"))
    }

    private fun providerSecretKey(providerId: String, name: String): String =
        "provider.$providerId.$name"
}
