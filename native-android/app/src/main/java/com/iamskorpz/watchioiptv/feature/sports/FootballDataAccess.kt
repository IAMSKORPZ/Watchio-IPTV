package com.iamskorpz.watchioiptv.feature.sports

import com.iamskorpz.watchioiptv.core.security.SecretStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

interface FootballDataCredentialStore {
    suspend fun get(): String?
    suspend fun save(value: String)
    suspend fun remove()
}

class SecureFootballDataCredentialStore(
    private val secretStore: SecretStore,
) : FootballDataCredentialStore {
    override suspend fun get(): String? = secretStore.getSecret(KEY)?.takeIf { it.isNotBlank() }
    override suspend fun save(value: String) = secretStore.putSecret(KEY, value)
    override suspend fun remove() = secretStore.removeSecret(KEY)

    private companion object {
        const val KEY = "sports.football_data.api_key"
    }
}

sealed interface FootballDataValidationResult {
    data object Valid : FootballDataValidationResult
    data object Invalid : FootballDataValidationResult
    data object RateLimited : FootballDataValidationResult
    data object NetworkError : FootballDataValidationResult
}

interface FootballDataCredentialValidator {
    suspend fun validate(apiKey: String): FootballDataValidationResult
}

class RemoteFootballDataCredentialValidator(
    private val api: FootballDataApi,
) : FootballDataCredentialValidator {
    override suspend fun validate(apiKey: String): FootballDataValidationResult = try {
        val response = api.validate(apiKey)
        when {
            response.isSuccessful -> FootballDataValidationResult.Valid
            response.code() == 401 || response.code() == 403 -> FootballDataValidationResult.Invalid
            response.code() == 429 -> FootballDataValidationResult.RateLimited
            else -> FootballDataValidationResult.NetworkError
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        FootballDataValidationResult.NetworkError
    } catch (_: HttpException) {
        FootballDataValidationResult.NetworkError
    } catch (_: Throwable) {
        FootballDataValidationResult.NetworkError
    }
}
