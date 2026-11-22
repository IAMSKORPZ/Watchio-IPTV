package com.iamskorpz.watchioiptv.feature.backup

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.iamskorpz.watchioiptv.BuildConfig
import com.iamskorpz.watchioiptv.core.database.EpgSourceEntity
import com.iamskorpz.watchioiptv.core.database.FavoriteEntity
import com.iamskorpz.watchioiptv.core.database.ProviderEntity
import com.iamskorpz.watchioiptv.core.database.WatchHistoryEntity
import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.security.AndroidSecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class WatchioBackupRepository(
    private val context: Context,
    private val database: WatchioDatabase,
    private val dataStore: DataStore<Preferences>,
    private val secretStore: AndroidSecretStore,
    private val crypto: WatchioBackupCrypto = WatchioBackupCrypto(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun create(password: CharArray, includeCredentials: Boolean): ByteArray = withContext(Dispatchers.IO) {
        crypto.encrypt(snapshot(includeCredentials), password)
    }

    suspend fun preview(bytes: ByteArray, password: CharArray): Pair<WatchioBackupPayload, BackupPreview> = withContext(Dispatchers.Default) {
        val payload = crypto.decrypt(bytes, password)
        validate(payload)
        payload to BackupPreview(
            createdAtEpochMs = payload.createdAtEpochMs,
            applicationVersion = payload.applicationVersion,
            providers = payload.providers.size,
            favorites = payload.favorites.size,
            history = payload.history.size,
            epgSources = payload.epgSources.size,
            includesCredentials = payload.encryptedCredentials.isNotEmpty(),
            excludedCategories = payload.excludedCategories,
        )
    }

    suspend fun restore(payload: WatchioBackupPayload) = withContext(Dispatchers.IO) {
        validate(payload)
        database.withTransaction {
            payload.providers.forEach { database.providerDao().upsert(it.toEntity()) }
            payload.favorites.forEach { database.favoriteDao().upsert(it.toEntity()) }
            payload.history.forEach { database.watchHistoryDao().upsert(it.toEntity()) }
            payload.epgSources.forEach { database.epgDao().upsertSource(it.toEntity()) }
        }
        restoreDataStore(payload.dataStore)
        payload.sharedPreferences.forEach { (name, values) -> restorePreferences(name, values) }
        if (payload.encryptedCredentials.isNotEmpty()) secretStore.mergePortableSecrets(payload.encryptedCredentials)
    }

    private suspend fun snapshot(includeCredentials: Boolean): WatchioBackupPayload {
        val providers = database.providerDao().getAll()
        val favorites = providers.flatMap { database.favoriteDao().getByProvider(it.id) }
        val history = providers.flatMap { database.watchHistoryDao().getRecent(it.id) }
        val epg = providers.flatMap { database.epgDao().getSources(it.id) }
        return WatchioBackupPayload(
            applicationVersion = BuildConfig.VERSION_NAME,
            databaseSchemaVersion = DATABASE_SCHEMA,
            createdAtEpochMs = now(),
            includedCategories = buildList {
                addAll(listOf("providers", "favourites", "watch-history", "epg-sources", "settings", "appearance", "playback", "search-history", "sports-settings"))
                if (includeCredentials) add("credentials")
            },
            excludedCategories = listOf("offline-media", "catalogue-cache", "epg-programme-cache", "image-cache", "update-files"),
            providers = providers.map { BackupProvider(it.id, it.displayName, it.type, it.serverUrl, it.createdAtEpochMs, it.updatedAtEpochMs, it.enabled) },
            favorites = favorites.map { BackupFavorite(it.providerId, it.contentType, it.contentId, it.subContentId, it.title, it.imageUrl, it.createdAtEpochMs) },
            history = history.map { BackupHistory(it.providerId, it.contentType, it.contentId, it.subContentId, it.title, it.imageUrl, it.positionMs, it.durationMs, it.lastWatchedAtEpochMs) },
            epgSources = epg.map { BackupEpgSource(it.providerId, it.sourceId, it.sourceType, it.url, it.enabled, it.priority) },
            dataStore = dataStore.data.first().asMap().entries.associate { it.key.name to it.value.toBackupValue() },
            sharedPreferences = PORTABLE_PREFERENCES.associateWith(::snapshotPreferences),
            encryptedCredentials = if (includeCredentials) secretStore.exportPortableSecrets() else emptyMap(),
        )
    }

    private fun validate(payload: WatchioBackupPayload) {
        require(payload.formatVersion == 1) { "Unsupported backup content version." }
        require(payload.databaseSchemaVersion in MIN_SUPPORTED_SCHEMA..DATABASE_SCHEMA) { "Backup database version is not supported by this Watchio version." }
        val providerIds = payload.providers.map { it.id }.toSet()
        require(payload.providers.none { it.id.isBlank() || it.displayName.isBlank() || it.type.isBlank() }) { "Backup contains an invalid provider." }
        require(payload.favorites.all { it.providerId in providerIds && it.contentId.isNotBlank() }) { "Backup contains an invalid favourite." }
        require(payload.history.all { it.providerId in providerIds && it.contentId.isNotBlank() }) { "Backup contains invalid watch history." }
        require(payload.epgSources.all { it.providerId in providerIds && it.sourceId.isNotBlank() }) { "Backup contains an invalid EPG source." }
        require(payload.providers.distinctBy { it.id }.size == payload.providers.size) { "Backup contains duplicate providers." }
        validateValues(payload.dataStore)
        payload.sharedPreferences.values.forEach(::validateValues)
    }

    private fun snapshotPreferences(name: String): Map<String, BackupValue> =
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapValues { it.value.toBackupValue() }

    private suspend fun restoreDataStore(values: Map<String, BackupValue>) {
        dataStore.edit { prefs -> values.forEach { (name, value) -> prefs.putBackup(name, value) } }
    }

    private fun restorePreferences(name: String, values: Map<String, BackupValue>) {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit(commit = true) {
            values.forEach { (key, value) ->
                when (value.type) {
                    "string" -> putString(key, value.value)
                    "boolean" -> putBoolean(key, requireNotNull(value.value).toBooleanStrict())
                    "int" -> putInt(key, value.value!!.toInt())
                    "long" -> putLong(key, value.value!!.toLong())
                    "float" -> putFloat(key, value.value!!.toFloat())
                    "strings" -> putStringSet(key, value.values.toSet())
                }
            }
        }
    }

    private fun MutablePreferences.putBackup(name: String, value: BackupValue) {
        when (value.type) {
            "string" -> this[stringPreferencesKey(name)] = value.value.orEmpty()
            "boolean" -> this[booleanPreferencesKey(name)] = requireNotNull(value.value).toBooleanStrict()
            "int" -> this[intPreferencesKey(name)] = value.value!!.toInt()
            "long" -> this[longPreferencesKey(name)] = value.value!!.toLong()
            "float" -> this[floatPreferencesKey(name)] = value.value!!.toFloat()
            "double" -> this[doublePreferencesKey(name)] = value.value!!.toDouble()
        }
    }

    private fun validateValues(values: Map<String, BackupValue>) {
        require(values.keys.none(String::isBlank)) { "Backup contains an invalid setting name." }
        values.values.forEach { value ->
            when (value.type) {
                "string" -> require(value.value != null)
                "boolean" -> requireNotNull(value.value).toBooleanStrict()
                "int" -> requireNotNull(value.value).toInt()
                "long" -> requireNotNull(value.value).toLong()
                "float" -> requireNotNull(value.value).toFloat()
                "double" -> requireNotNull(value.value).toDouble()
                "strings" -> Unit
                else -> throw IllegalArgumentException("Backup contains an unsupported setting type.")
            }
        }
    }

    private fun Any?.toBackupValue(): BackupValue = when (this) {
        is String -> BackupValue("string", this)
        is Boolean -> BackupValue("boolean", toString())
        is Int -> BackupValue("int", toString())
        is Long -> BackupValue("long", toString())
        is Float -> BackupValue("float", toString())
        is Double -> BackupValue("double", toString())
        is Set<*> -> BackupValue("strings", values = filterIsInstance<String>().sorted())
        else -> throw IllegalArgumentException("Unsupported preference value in backup.")
    }

    private fun BackupProvider.toEntity() = ProviderEntity(id, displayName, type, serverUrl, createdAt, updatedAt, null, enabled)
    private fun BackupFavorite.toEntity() = FavoriteEntity(providerId, contentType, contentId, subContentId, title, imageUrl, createdAt)
    private fun BackupHistory.toEntity() = WatchHistoryEntity(providerId, contentType, contentId, subContentId, title, imageUrl, positionMs, durationMs, watchedAt)
    private fun BackupEpgSource.toEntity() = EpgSourceEntity(providerId, sourceId, sourceType, url, enabled, priority, null, null, null, null, null, null, 0, 0, now(), now())

    companion object {
        const val DATABASE_SCHEMA = 10
        const val MIN_SUPPORTED_SCHEMA = 7
        val PORTABLE_PREFERENCES = listOf("watchio_endpoint_config", "watchio_search_history", "watchio_sports_v3", "sports_match_cache_v1", "sports_broadcast_cache_v1")
    }
}
