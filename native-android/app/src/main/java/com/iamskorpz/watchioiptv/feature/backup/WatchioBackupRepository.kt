package com.iamskorpz.watchioiptv.feature.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
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
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WatchioBackupRepository(
    private val context: Context,
    private val database: WatchioDatabase,
    private val dataStore: DataStore<Preferences>,
    private val secretStore: AndroidSecretStore,
    private val crypto: WatchioBackupCrypto = WatchioBackupCrypto(),
    private val journalStore: RestoreJournalStore = RestoreJournalStore(context),
    private val failureInjector: RestoreFailureInjector = RestoreFailureInjector.NONE,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val operationMutex = Mutex()
    private val mutableRecoveryStatus = MutableStateFlow(BackupRecoveryStatus.READY)
    val recoveryStatus = mutableRecoveryStatus.asStateFlow()

    suspend fun create(password: CharArray, includeCredentials: Boolean): ByteArray = operationMutex.withLock {
        withContext(Dispatchers.IO) { crypto.encrypt(snapshot(includeCredentials), password) }
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

    suspend fun restore(payload: WatchioBackupPayload) = operationMutex.withLock {
        withContext(Dispatchers.IO) { restoreLocked(payload) }
    }

    suspend fun recoverInterruptedRestore() = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            mutableRecoveryStatus.value = BackupRecoveryStatus.RECOVERING
            var journal: RestoreJournal? = null
            try {
                journal = journalStore.read() ?: run {
                    mutableRecoveryStatus.value = BackupRecoveryStatus.READY
                    return@withContext
                }
                when (journal!!.state) {
                    RestoreState.PREPARING -> cleanup(journal!!)
                    RestoreState.COMMITTED, RestoreState.ROLLED_BACK -> cleanup(journal!!)
                    else -> rollback(journal!!)
                }
                mutableRecoveryStatus.value = BackupRecoveryStatus.READY
            } catch (_: Throwable) {
                journal?.let { persist(it.copy(state = RestoreState.RECOVERY_FAILED, updatedAtEpochMs = now())) }
                mutableRecoveryStatus.value = BackupRecoveryStatus.RECOVERY_REQUIRED
            }
        }
    }

    fun hasUnresolvedRecovery(): Boolean = journalStore.read() != null

    private suspend fun restoreLocked(payload: WatchioBackupPayload) {
        validate(payload)
        check(journalStore.read() == null) { "Another restore requires recovery before continuing." }
        mutableRecoveryStatus.value = BackupRecoveryStatus.RECOVERING
        val operationId = UUID.randomUUID().toString()
        val journal = RestoreJournal(
            operationId = operationId,
            state = RestoreState.PREPARING,
            recoveryFileName = "recovery-$operationId.watchio",
            recoveryKeyAlias = "$RECOVERY_KEY_PREFIX$operationId",
            updatedAtEpochMs = now(),
        )
        persist(journal)
        try {
            failureInjector.check(RestoreBoundary.BEFORE_RECOVERY_POINT)
            failureInjector.check(RestoreBoundary.AFTER_JOURNAL)
            val recoveryPassword = randomRecoveryPassword()
            val recoveryBytes = crypto.encrypt(
                snapshot(includeCredentials = true, includeDeviceSpecificSettings = true),
                recoveryPassword.toCharArray(),
            )
            try {
                journalStore.writeRecovery(journal.recoveryFileName, recoveryBytes)
            } finally {
                recoveryBytes.fill(0)
            }
            failureInjector.check(RestoreBoundary.DURING_RECOVERY_POINT)
            secretStore.putSecret(journal.recoveryKeyAlias, recoveryPassword)
            val recoverySha = journalStore.recoverySha256(journal.recoveryFileName)
            journalStore.verifyRecovery(journal.recoveryFileName, recoverySha)
            var current = journal.copy(
                state = RestoreState.RECOVERY_POINT_READY,
                recoverySha256 = recoverySha,
                updatedAtEpochMs = now(),
            )
            persist(current)
            failureInjector.check(RestoreBoundary.AFTER_RECOVERY_POINT)

            current = transition(current, RestoreState.APPLYING_DATABASE)
            failureInjector.check(RestoreBoundary.BEFORE_DATABASE)
            applyDatabase(payload)
            failureInjector.check(RestoreBoundary.AFTER_DATABASE)

            current = transition(current, RestoreState.APPLYING_PREFERENCES)
            failureInjector.check(RestoreBoundary.BEFORE_PREFERENCES)
            mergePreferences(payload)
            failureInjector.check(RestoreBoundary.AFTER_PREFERENCES)

            current = transition(current, RestoreState.APPLYING_CREDENTIALS)
            failureInjector.check(RestoreBoundary.BEFORE_CREDENTIALS)
            if (payload.encryptedCredentials.isNotEmpty()) secretStore.mergePortableSecrets(payload.encryptedCredentials)
            failureInjector.check(RestoreBoundary.DURING_CREDENTIALS)
            failureInjector.check(RestoreBoundary.AFTER_CREDENTIALS)

            current = transition(current, RestoreState.VERIFYING)
            failureInjector.check(RestoreBoundary.BEFORE_VERIFICATION)
            verifyApplied(payload)
            failureInjector.check(RestoreBoundary.AFTER_VERIFICATION)
            current = transition(current, RestoreState.COMMITTED)
            failureInjector.check(RestoreBoundary.AFTER_COMMIT)
            cleanup(current)
            mutableRecoveryStatus.value = BackupRecoveryStatus.READY
        } catch (error: Throwable) {
            val latest = journalStore.read()
            if (latest?.state == RestoreState.COMMITTED) {
                mutableRecoveryStatus.value = BackupRecoveryStatus.READY
                throw IllegalStateException("Restore completed, but recovery cleanup is pending.", error)
            }
            if (latest?.state == RestoreState.PREPARING) {
                cleanup(latest)
                mutableRecoveryStatus.value = BackupRecoveryStatus.READY
                throw error
            }
            val rollbackJournal = requireNotNull(latest).copy(state = RestoreState.ROLLBACK_REQUIRED, updatedAtEpochMs = now())
            persist(rollbackJournal)
            try {
                rollback(rollbackJournal)
                mutableRecoveryStatus.value = BackupRecoveryStatus.READY
            } catch (rollbackError: Throwable) {
                persist(rollbackJournal.copy(state = RestoreState.RECOVERY_FAILED, updatedAtEpochMs = now()))
                mutableRecoveryStatus.value = BackupRecoveryStatus.RECOVERY_REQUIRED
                throw IllegalStateException("Restore failed and automatic recovery requires attention.", rollbackError)
            }
            throw IllegalStateException("Restore failed. Original application data was recovered.", error)
        }
    }

    private suspend fun rollback(journal: RestoreJournal) {
        requireNotNull(journal.recoverySha256) { "Restore recovery point is incomplete." }
        val key = requireNotNull(secretStore.getSecret(journal.recoveryKeyAlias)) { "Restore recovery key is unavailable." }
        journalStore.verifyRecovery(journal.recoveryFileName, journal.recoverySha256)
        val bytes = journalStore.readRecovery(journal.recoveryFileName)
        val recovery = try { crypto.decrypt(bytes, key.toCharArray()) } finally { bytes.fill(0) }
        validate(recovery)
        var current = transition(journal, RestoreState.ROLLING_BACK)
        replaceDatabase(recovery)
        failureInjector.check(RestoreBoundary.DURING_ROLLBACK)
        replacePreferences(recovery)
        secretStore.replacePortableSecrets(recovery.encryptedCredentials + (journal.recoveryKeyAlias to key))
        verifyExact(recovery, journal.recoveryKeyAlias)
        failureInjector.check(RestoreBoundary.DURING_RECOVERY_VERIFICATION)
        current = transition(current, RestoreState.ROLLED_BACK)
        cleanup(current)
    }

    private suspend fun snapshot(
        includeCredentials: Boolean,
        includeDeviceSpecificSettings: Boolean = false,
    ): WatchioBackupPayload {
        val databaseSnapshot = database.withTransaction {
            val providers = database.providerDao().getAll()
            DatabaseSnapshot(
                providers,
                providers.flatMap { database.favoriteDao().getByProvider(it.id) },
                providers.flatMap { database.watchHistoryDao().getRecent(it.id) },
                providers.flatMap { database.epgDao().getSources(it.id) },
            )
        }
        return WatchioBackupPayload(
            applicationVersion = BuildConfig.VERSION_NAME,
            databaseSchemaVersion = DATABASE_SCHEMA,
            createdAtEpochMs = now(),
            includedCategories = buildList {
                addAll(listOf("providers", "favourites", "watch-history", "epg-sources", "settings", "appearance", "playback", "search-history", "sports-settings"))
                if (includeCredentials) add("credentials")
            },
            excludedCategories = listOf("offline-media", "catalogue-cache", "epg-programme-cache", "image-cache", "update-files"),
            providers = databaseSnapshot.providers.map { BackupProvider(it.id, it.displayName, it.type, it.serverUrl, it.createdAtEpochMs, it.updatedAtEpochMs, it.enabled, it.lastRefreshAtEpochMs) },
            favorites = databaseSnapshot.favorites.map { BackupFavorite(it.providerId, it.contentType, it.contentId, it.subContentId, it.title, it.imageUrl, it.createdAtEpochMs) },
            history = databaseSnapshot.history.map { BackupHistory(it.providerId, it.contentType, it.contentId, it.subContentId, it.title, it.imageUrl, it.positionMs, it.durationMs, it.lastWatchedAtEpochMs) },
            epgSources = databaseSnapshot.epg.map { it.toBackup() },
            dataStore = dataStore.data.first().asMap().entries
                .filter { includeDeviceSpecificSettings || it.key.name !in DEVICE_SPECIFIC_DATASTORE_KEYS }
                .associate { it.key.name to it.value.toBackupValue() },
            sharedPreferences = PORTABLE_PREFERENCES.associateWith(::snapshotPreferences),
            encryptedCredentials = if (includeCredentials) secretStore.exportPortableSecrets().filterKeys { !it.startsWith(RECOVERY_KEY_PREFIX) } else emptyMap(),
        )
    }

    private fun validate(payload: WatchioBackupPayload) {
        require(payload.formatVersion == 1) { "Unsupported backup content version." }
        require(payload.databaseSchemaVersion in MIN_SUPPORTED_SCHEMA..DATABASE_SCHEMA) { "Backup database version is not supported by this Watchio version." }
        require(payload.createdAtEpochMs in 0..MAX_TIMESTAMP) { "Backup contains an invalid creation time." }
        require(payload.providers.size <= MAX_PROVIDERS && payload.favorites.size <= MAX_ITEMS && payload.history.size <= MAX_ITEMS && payload.epgSources.size <= MAX_EPG_SOURCES) { "Backup contains too many records." }
        val providerIds = payload.providers.map { it.id }.toSet()
        require(payload.providers.none { !it.id.validId() || !it.displayName.validText() || !it.type.validId() || !it.serverUrl.validOptionalValue() || !it.createdAt.validTimestamp() || !it.updatedAt.validTimestamp() || (it.lastRefreshAt != null && !it.lastRefreshAt.validTimestamp()) }) { "Backup contains an invalid provider." }
        require(payload.favorites.all { it.providerId in providerIds && it.contentType.validId() && it.contentId.validId() && it.subContentId.length <= MAX_ID && it.title.validText() && it.imageUrl.validOptionalValue() && it.createdAt.validTimestamp() }) { "Backup contains an invalid favourite." }
        require(payload.history.all { it.providerId in providerIds && it.contentType.validId() && it.contentId.validId() && it.subContentId.length <= MAX_ID && it.title.validText() && it.imageUrl.validOptionalValue() && it.watchedAt.validTimestamp() && (it.positionMs ?: 0) >= 0 && (it.durationMs ?: 0) >= 0 }) { "Backup contains invalid watch history." }
        require(payload.epgSources.all { it.providerId in providerIds && it.sourceId.validId() && it.sourceType.validId() && it.url.validOptionalValue() && it.lastError.validOptionalValue() && it.etag.validOptionalValue() && it.lastModified.validOptionalValue() && it.channelCount >= 0 && it.programmeCount >= 0 && it.createdAt.validTimestamp() && it.updatedAt.validTimestamp() }) { "Backup contains an invalid EPG source." }
        require(payload.providers.distinctBy { it.id }.size == payload.providers.size) { "Backup contains duplicate providers." }
        require(payload.favorites.distinctBy { listOf(it.providerId, it.contentType, it.contentId, it.subContentId) }.size == payload.favorites.size) { "Backup contains duplicate favourites." }
        require(payload.history.distinctBy { listOf(it.providerId, it.contentType, it.contentId, it.subContentId) }.size == payload.history.size) { "Backup contains duplicate history." }
        require(payload.epgSources.distinctBy { it.providerId to it.sourceId }.size == payload.epgSources.size) { "Backup contains duplicate EPG sources." }
        require(payload.dataStore.size <= MAX_SETTINGS && payload.sharedPreferences.size <= PORTABLE_PREFERENCES.size && payload.encryptedCredentials.size <= MAX_SECRETS) { "Backup contains too many settings." }
        require(payload.sharedPreferences.keys.all { it in PORTABLE_PREFERENCES }) { "Backup contains an unsupported preference store." }
        require(payload.encryptedCredentials.all { it.key.validId() && it.value.length <= MAX_VALUE }) { "Backup contains an invalid credential entry." }
        validateValues(payload.dataStore)
        payload.sharedPreferences.values.forEach(::validateValues)
    }

    private suspend fun applyDatabase(payload: WatchioBackupPayload) = database.withTransaction {
        payload.providers.forEach { database.providerDao().upsert(it.toEntity()) }
        failureInjector.check(RestoreBoundary.DURING_DATABASE)
        payload.favorites.forEach { database.favoriteDao().upsert(it.toEntity()) }
        payload.history.forEach { database.watchHistoryDao().upsert(it.toEntity()) }
        payload.epgSources.forEach { database.epgDao().upsertSource(it.toEntity()) }
    }

    private suspend fun replaceDatabase(payload: WatchioBackupPayload) = database.withTransaction {
        val sql = database.openHelper.writableDatabase
        sql.execSQL("DELETE FROM favorites")
        sql.execSQL("DELETE FROM watch_history")
        sql.execSQL("DELETE FROM epg_sources")
        val keep = payload.providers.map { it.id }.toSet()
        database.providerDao().getAll().filter { it.id !in keep }.forEach { database.providerDao().deleteProvider(it.id) }
        payload.providers.forEach { database.providerDao().upsert(it.toEntity()) }
        payload.favorites.forEach { database.favoriteDao().upsert(it.toEntity()) }
        payload.history.forEach { database.watchHistoryDao().upsert(it.toEntity()) }
        payload.epgSources.forEach { database.epgDao().upsertSource(it.toEntity()) }
    }

    private suspend fun mergePreferences(payload: WatchioBackupPayload) {
        dataStore.edit { prefs -> portableDataStore(payload).forEach { (name, value) -> prefs.putBackup(name, value) } }
        failureInjector.check(RestoreBoundary.DURING_PREFERENCES)
        payload.sharedPreferences.forEach { (name, values) -> restorePreferences(name, values, replace = false) }
    }

    private suspend fun replacePreferences(payload: WatchioBackupPayload) {
        dataStore.edit { prefs -> prefs.clear(); payload.dataStore.forEach { (name, value) -> prefs.putBackup(name, value) } }
        PORTABLE_PREFERENCES.forEach { name -> restorePreferences(name, payload.sharedPreferences[name].orEmpty(), replace = true) }
    }

    private suspend fun verifyApplied(payload: WatchioBackupPayload) {
        val providers = database.providerDao().getAll().associateBy { it.id }
        require(payload.providers.all { providers[it.id] == it.toEntity() }) { "Restored providers could not be verified." }
        require(payload.favorites.all { database.favoriteDao().find(it.providerId, it.contentType, it.contentId, it.subContentId) == it.toEntity() }) { "Restored favourites could not be verified." }
        require(payload.history.all { database.watchHistoryDao().find(it.providerId, it.contentType, it.contentId, it.subContentId) == it.toEntity() }) { "Restored history could not be verified." }
        require(payload.epgSources.all { source -> database.epgDao().getSources(source.providerId).any { it == source.toEntity() } }) { "Restored EPG sources could not be verified." }
        val settings = dataStore.data.first().asMap().entries.associate { it.key.name to it.value.toBackupValue() }
        require(portableDataStore(payload).all { settings[it.key] == it.value }) { "Restored settings could not be verified." }
        if (payload.encryptedCredentials.isNotEmpty()) {
            val secrets = secretStore.exportPortableSecrets()
            require(payload.encryptedCredentials.all { secrets[it.key] == it.value }) { "Restored credentials could not be verified." }
        }
    }

    private suspend fun verifyExact(payload: WatchioBackupPayload, recoveryAlias: String) {
        verifyApplied(payload)
        require(database.providerDao().getAll().map { it.id }.toSet() == payload.providers.map { it.id }.toSet()) { "Provider rollback verification failed." }
        val providerIds = payload.providers.map { it.id }
        require(providerIds.flatMap { database.favoriteDao().getByProvider(it) }.toSet() == payload.favorites.map { it.toEntity() }.toSet()) { "Favourite rollback verification failed." }
        require(providerIds.flatMap { database.watchHistoryDao().getRecent(it) }.toSet() == payload.history.map { it.toEntity() }.toSet()) { "History rollback verification failed." }
        require(providerIds.flatMap { database.epgDao().getSources(it) }.toSet() == payload.epgSources.map { it.toEntity() }.toSet()) { "EPG rollback verification failed." }
        val settings = dataStore.data.first().asMap().entries.associate { it.key.name to it.value.toBackupValue() }
        require(settings == payload.dataStore) { "Settings rollback verification failed." }
        require(PORTABLE_PREFERENCES.all { snapshotPreferences(it) == payload.sharedPreferences[it].orEmpty() }) { "Preference rollback verification failed." }
        val secrets = secretStore.exportPortableSecrets().filterKeys { it != recoveryAlias }
        require(secrets == payload.encryptedCredentials) { "Credential rollback verification failed." }
    }

    private fun restorePreferences(name: String, values: Map<String, BackupValue>, replace: Boolean) {
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
        if (replace) editor.clear()
        values.forEach { (key, value) -> editor.putBackup(key, value) }
        check(editor.commit()) { "Unable to persist restored preferences." }
    }

    private fun android.content.SharedPreferences.Editor.putBackup(key: String, value: BackupValue) {
        when (value.type) {
            "string" -> putString(key, value.value)
            "boolean" -> putBoolean(key, requireNotNull(value.value).toBooleanStrict())
            "int" -> putInt(key, value.value!!.toInt())
            "long" -> putLong(key, value.value!!.toLong())
            "float" -> putFloat(key, value.value!!.toFloat())
            "strings" -> putStringSet(key, value.values.toSet())
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
        require(values.size <= MAX_SETTINGS && values.keys.all { it.validId() }) { "Backup contains an invalid setting name." }
        values.values.forEach { value ->
            require((value.value?.length ?: 0) <= MAX_VALUE && value.values.size <= MAX_STRING_SET && value.values.all { it.length <= MAX_VALUE }) { "Backup contains an oversized setting." }
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

    private fun snapshotPreferences(name: String): Map<String, BackupValue> =
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapValues { it.value.toBackupValue() }

    private fun portableDataStore(payload: WatchioBackupPayload): Map<String, BackupValue> =
        payload.dataStore.filterKeys { it !in DEVICE_SPECIFIC_DATASTORE_KEYS }

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

    private fun transition(journal: RestoreJournal, state: RestoreState): RestoreJournal =
        journal.copy(state = state, updatedAtEpochMs = now()).also(::persist)

    private fun persist(journal: RestoreJournal) = journalStore.write(journal)

    private suspend fun cleanup(journal: RestoreJournal) {
        secretStore.removeSecret(journal.recoveryKeyAlias)
        journalStore.cleanup(journal)
    }

    private fun randomRecoveryPassword(): String = ByteArray(32).also(SecureRandom()::nextBytes).let(Base64.getEncoder()::encodeToString)
    private fun String.validId() = isNotBlank() && length <= MAX_ID && none { it.code < 0x20 }
    private fun String.validText() = isNotBlank() && length <= MAX_TEXT && none { it.code == 0 }
    private fun String?.validOptionalValue() = this == null || (length <= MAX_VALUE && none { it.code == 0 })
    private fun Long.validTimestamp() = this in 0..MAX_TIMESTAMP

    private fun BackupProvider.toEntity() = ProviderEntity(id, displayName, type, serverUrl, createdAt, updatedAt, lastRefreshAt, enabled)
    private fun BackupFavorite.toEntity() = FavoriteEntity(providerId, contentType, contentId, subContentId, title, imageUrl, createdAt)
    private fun BackupHistory.toEntity() = WatchHistoryEntity(providerId, contentType, contentId, subContentId, title, imageUrl, positionMs, durationMs, watchedAt)
    private fun BackupEpgSource.toEntity() = EpgSourceEntity(providerId, sourceId, sourceType, url, enabled, priority, lastRefreshAt, lastSuccessAt, lastErrorAt, lastError, etag, lastModified, channelCount, programmeCount, createdAt, updatedAt)
    private fun EpgSourceEntity.toBackup() = BackupEpgSource(providerId, sourceId, sourceType, url, enabled, priority, lastRefreshAtEpochMs, lastSuccessAtEpochMs, lastErrorAtEpochMs, lastError, etag, lastModified, channelCount, programmeCount, createdAtEpochMs, updatedAtEpochMs)

    private data class DatabaseSnapshot(
        val providers: List<ProviderEntity>,
        val favorites: List<FavoriteEntity>,
        val history: List<WatchHistoryEntity>,
        val epg: List<EpgSourceEntity>,
    )

    companion object {
        const val DATABASE_SCHEMA = 10
        const val MIN_SUPPORTED_SCHEMA = 7
        const val RECOVERY_KEY_PREFIX = "backup_restore_recovery."
        const val MAX_PROVIDERS = 1_000
        const val MAX_ITEMS = 100_000
        const val MAX_EPG_SOURCES = 5_000
        const val MAX_SETTINGS = 10_000
        const val MAX_SECRETS = 10_000
        const val MAX_STRING_SET = 10_000
        const val MAX_ID = 512
        const val MAX_TEXT = 8_192
        const val MAX_VALUE = 65_536
        const val MAX_TIMESTAMP = 253_402_300_799_000L
        val PORTABLE_PREFERENCES = listOf("watchio_endpoint_config", "watchio_search_history", "watchio_sports_v3", "sports_match_cache_v1", "sports_broadcast_cache_v1")
        val DEVICE_SPECIFIC_DATASTORE_KEYS = setOf("input_mode")
    }
}
