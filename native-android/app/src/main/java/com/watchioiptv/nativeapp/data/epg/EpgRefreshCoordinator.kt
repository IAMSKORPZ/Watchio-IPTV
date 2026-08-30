package com.watchioiptv.nativeapp.data.epg

import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EpgRefreshSummary(
    val refreshedProviders: Int,
    val failedProviders: Int,
)

open class EpgRefreshCoordinator(
    private val database: WatchioDatabase? = null,
    private val epgRepository: EpgRepository? = null,
) {
    open suspend fun refreshAllEnabledProviders(): EpgRefreshSummary {
        val db = database ?: return EpgRefreshSummary(0, 0)
        val providers = db.providerDao().getAll().filter { it.enabled }
        var success = 0
        var failure = 0
        providers.forEach { provider ->
            val result = runCatching { refreshProvider(provider.id) }
            if (result.isSuccess) success++ else failure++
        }
        return EpgRefreshSummary(success, failure)
    }

    open suspend fun refreshProvider(providerId: String): EpgImportResult =
        lockFor(providerId).withLock {
            epgRepository?.refresh(providerId) ?: EpgImportResult(providerId, 0, 0)
        }

    open suspend fun latestSuccessForProvider(providerId: String?): Long? =
        providerId?.let { database?.epgDao()?.latestSuccess(it) }

    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()

        private fun lockFor(providerId: String): Mutex =
            locks.getOrPut(providerId) { Mutex() }
    }
}
