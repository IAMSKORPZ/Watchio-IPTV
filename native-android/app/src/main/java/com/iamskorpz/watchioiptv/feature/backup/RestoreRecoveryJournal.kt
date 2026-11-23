package com.iamskorpz.watchioiptv.feature.backup

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class RestoreState {
    PREPARING,
    RECOVERY_POINT_READY,
    APPLYING_DATABASE,
    APPLYING_PREFERENCES,
    APPLYING_CREDENTIALS,
    VERIFYING,
    COMMITTED,
    ROLLBACK_REQUIRED,
    ROLLING_BACK,
    ROLLED_BACK,
    RECOVERY_FAILED,
}

enum class RestoreBoundary {
    BEFORE_RECOVERY_POINT,
    AFTER_JOURNAL,
    DURING_RECOVERY_POINT,
    AFTER_RECOVERY_POINT,
    BEFORE_DATABASE,
    DURING_DATABASE,
    AFTER_DATABASE,
    BEFORE_PREFERENCES,
    DURING_PREFERENCES,
    AFTER_PREFERENCES,
    BEFORE_CREDENTIALS,
    DURING_CREDENTIALS,
    AFTER_CREDENTIALS,
    BEFORE_VERIFICATION,
    AFTER_VERIFICATION,
    AFTER_COMMIT,
    DURING_ROLLBACK,
    DURING_RECOVERY_VERIFICATION,
}

fun interface RestoreFailureInjector {
    fun check(boundary: RestoreBoundary)

    companion object {
        val NONE = RestoreFailureInjector { }
    }
}

@Serializable
data class RestoreJournal(
    val operationId: String,
    val state: RestoreState,
    val recoveryFileName: String,
    val recoveryKeyAlias: String,
    val recoverySha256: String? = null,
    val updatedAtEpochMs: Long,
)

class RestoreJournalStore(
    context: Context,
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = false },
) {
    private val directory = File(context.noBackupFilesDir, "backup-restore-recovery")
    private val journalFile = AtomicFile(File(directory, "restore-journal.json"))

    fun read(): RestoreJournal? {
        if (!journalFile.baseFile.exists()) return null
        return runCatching { json.decodeFromString<RestoreJournal>(journalFile.readFully().decodeToString()) }
            .getOrElse { throw IllegalStateException("Restore recovery journal is invalid.") }
    }

    fun write(journal: RestoreJournal) {
        directory.mkdirs()
        val stream = journalFile.startWrite()
        try {
            stream.write(json.encodeToString(journal).encodeToByteArray())
            stream.fd.sync()
            journalFile.finishWrite(stream)
        } catch (error: Throwable) {
            journalFile.failWrite(stream)
            throw error
        }
    }

    fun writeRecovery(fileName: String, bytes: ByteArray) {
        directory.mkdirs()
        val atomic = AtomicFile(File(directory, safeFileName(fileName)))
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    fun readRecovery(fileName: String): ByteArray {
        val file = File(directory, safeFileName(fileName))
        require(file.exists() && file.length() in 1..WatchioBackupCrypto.MAX_BACKUP_BYTES) { "Restore recovery point is unavailable." }
        return file.readBytes()
    }

    fun verifyRecovery(fileName: String, expectedSha256: String) {
        val bytes = readRecovery(fileName)
        try {
            require(MessageDigest.isEqual(bytes.sha256().encodeToByteArray(), expectedSha256.encodeToByteArray())) {
                "Restore recovery point integrity check failed."
            }
        } finally {
            bytes.fill(0)
        }
    }

    fun cleanup(journal: RestoreJournal) {
        File(directory, safeFileName(journal.recoveryFileName)).delete()
        journalFile.delete()
        directory.delete()
    }

    fun recoverySha256(fileName: String): String {
        val bytes = readRecovery(fileName)
        return try { bytes.sha256() } finally { bytes.fill(0) }
    }

    private fun safeFileName(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9._-]{1,96}"))) { "Invalid restore recovery file name." }
        return value
    }

    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
}

enum class BackupRecoveryStatus { READY, RECOVERING, RECOVERY_REQUIRED }
