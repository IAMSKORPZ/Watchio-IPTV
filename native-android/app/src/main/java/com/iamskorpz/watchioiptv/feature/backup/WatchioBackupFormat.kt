package com.iamskorpz.watchioiptv.feature.backup

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WatchioBackupEnvelope(
    val format: String = MAGIC,
    val formatVersion: Int = FORMAT_VERSION,
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = KDF_ITERATIONS,
    val salt: String,
    val cipher: String = "AES-256-GCM",
    val nonce: String,
    val payloadSha256: String,
    val ciphertext: String,
) {
    companion object {
        const val MAGIC = "watchio-backup"
        const val FORMAT_VERSION = 1
        const val KDF_ITERATIONS = 310_000
    }
}

@Serializable data class BackupProvider(val id: String, val displayName: String, val type: String, val serverUrl: String?, val createdAt: Long, val updatedAt: Long, val enabled: Boolean, val lastRefreshAt: Long? = null)
@Serializable data class BackupFavorite(val providerId: String, val contentType: String, val contentId: String, val subContentId: String, val title: String, val imageUrl: String?, val createdAt: Long)
@Serializable data class BackupHistory(val providerId: String, val contentType: String, val contentId: String, val subContentId: String, val title: String, val imageUrl: String?, val positionMs: Long?, val durationMs: Long?, val watchedAt: Long)
@Serializable data class BackupEpgSource(
    val providerId: String,
    val sourceId: String,
    val sourceType: String,
    val url: String?,
    val enabled: Boolean,
    val priority: Int,
    val lastRefreshAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastErrorAt: Long? = null,
    val lastError: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val channelCount: Int = 0,
    val programmeCount: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
@Serializable data class BackupValue(val type: String, val value: String? = null, val values: List<String> = emptyList())

@Serializable
data class WatchioBackupPayload(
    val formatVersion: Int = 1,
    val applicationVersion: String,
    val databaseSchemaVersion: Int,
    val createdAtEpochMs: Long,
    val includedCategories: List<String>,
    val excludedCategories: List<String>,
    val providers: List<BackupProvider>,
    val favorites: List<BackupFavorite>,
    val history: List<BackupHistory>,
    val epgSources: List<BackupEpgSource>,
    val dataStore: Map<String, BackupValue>,
    val sharedPreferences: Map<String, Map<String, BackupValue>>,
    val encryptedCredentials: Map<String, String> = emptyMap(),
)

data class BackupPreview(
    val createdAtEpochMs: Long,
    val applicationVersion: String,
    val providers: Int,
    val favorites: Int,
    val history: Int,
    val epgSources: Int,
    val includesCredentials: Boolean,
    val excludedCategories: List<String>,
)

class WatchioBackupCrypto(
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true },
    private val random: SecureRandom = SecureRandom(),
) {
    fun encrypt(payload: WatchioBackupPayload, password: CharArray): ByteArray {
        var plaintext: ByteArray? = null
        try {
            require(password.size >= 10) { "Backup password must contain at least 10 characters." }
            plaintext = json.encodeToString(payload).encodeToByteArray()
            val salt = ByteArray(16).also(random::nextBytes)
            val nonce = ByteArray(12).also(random::nextBytes)
            val key = derive(password, salt, WatchioBackupEnvelope.KDF_ITERATIONS)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.updateAAD("${WatchioBackupEnvelope.MAGIC}:${WatchioBackupEnvelope.FORMAT_VERSION}".encodeToByteArray())
            val encrypted = cipher.doFinal(plaintext)
            val envelope = WatchioBackupEnvelope(
                salt = salt.b64(), nonce = nonce.b64(), payloadSha256 = plaintext.sha256(), ciphertext = encrypted.b64(),
            )
            return json.encodeToString(envelope).encodeToByteArray()
        } finally {
            password.fill('\u0000')
            plaintext?.fill(0)
        }
    }

    fun decrypt(bytes: ByteArray, password: CharArray): WatchioBackupPayload {
        var plaintext: ByteArray? = null
        try {
            require(bytes.size in 1..MAX_BACKUP_BYTES) { "Backup file is empty or too large." }
            val envelope = runCatching { json.decodeFromString<WatchioBackupEnvelope>(bytes.decodeToString()) }
                .getOrElse { throw IllegalArgumentException("Invalid Watchio backup file.") }
            require(envelope.format == WatchioBackupEnvelope.MAGIC && envelope.formatVersion == 1) { "Unsupported Watchio backup format." }
            require(envelope.kdf == "PBKDF2WithHmacSHA256" && envelope.cipher == "AES-256-GCM") { "Unsupported backup encryption." }
            require(envelope.iterations in WatchioBackupEnvelope.KDF_ITERATIONS..MAX_KDF_ITERATIONS) { "Unsupported backup encryption strength." }
            val salt = envelope.salt.b64Decode(16)
            val nonce = envelope.nonce.b64Decode(12)
            val encrypted = envelope.ciphertext.b64Decode()
            val key = derive(password, salt, envelope.iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.updateAAD("${WatchioBackupEnvelope.MAGIC}:${WatchioBackupEnvelope.FORMAT_VERSION}".encodeToByteArray())
            plaintext = runCatching { cipher.doFinal(encrypted) }
                .getOrElse { throw IllegalArgumentException("Wrong password or corrupted backup.") }
            require(MessageDigest.isEqual(plaintext.sha256().encodeToByteArray(), envelope.payloadSha256.lowercase().encodeToByteArray())) { "Backup integrity check failed." }
            return runCatching { json.decodeFromString<WatchioBackupPayload>(plaintext.decodeToString()) }
                .getOrElse { throw IllegalArgumentException("Backup content is invalid.") }
        } finally {
            password.fill('\u0000')
            plaintext?.fill(0)
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }

    private fun ByteArray.b64() = Base64.getEncoder().encodeToString(this)
    private fun String.b64Decode(expected: Int? = null): ByteArray = runCatching { Base64.getDecoder().decode(this) }
        .getOrElse { throw IllegalArgumentException("Invalid backup encoding.") }
        .also { if (expected != null) require(it.size == expected) { "Invalid backup encryption parameters." } }
    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
        const val MAX_KDF_ITERATIONS = 2_000_000
    }
}
