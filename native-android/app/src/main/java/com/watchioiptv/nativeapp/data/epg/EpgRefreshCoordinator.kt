package com.watchioiptv.nativeapp.data.epg

import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EpgRefreshSummary(
    val refreshedProviders: Int,
    val failedProviders: Int,
)

class EpgRefreshCoordinator(
    private val database: WatchioDatabase,
    private val epgRepository: EpgRepository,
) {
    suspend fun refreshAllEnabledProviders(): EpgRefreshSummary {
        val providers = database.providerDao().getAll().filter { it.enabled }
        var success = 0
        var failure = 0
        providers.forEach { provider ->
            val result = runCatching { refreshProvider(provider.id) }
            if (result.isSuccess) success++ else failure++
        }
        return EpgRefreshSummary(success, failure)
    }

    suspend fun refreshProvider(providerId: String): EpgImportResult =
        lockFor(providerId).withLock {
            epgRepository.refresh(providerId)
        }

    suspend fun latestSuccessForProvider(providerId: String?): Long? =
        providerId?.let { database.epgDao().latestSuccess(it) }

    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()

        private fun lockFor(providerId: String): Mutex =
            locks.getOrPut(providerId) { Mutex() }
    }
}
