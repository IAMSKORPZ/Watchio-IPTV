package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.backup.BackupFavorite
import com.iamskorpz.watchioiptv.feature.backup.BackupProvider
import com.iamskorpz.watchioiptv.feature.backup.WatchioBackupCrypto
import com.iamskorpz.watchioiptv.feature.backup.WatchioBackupEnvelope
import com.iamskorpz.watchioiptv.feature.backup.WatchioBackupPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WatchioBackupCryptoTest {
    private val crypto = WatchioBackupCrypto()

    @Test fun encryptedBackupRoundTripsWithoutPlaintextSecretLeakage() {
        val payload = fixture()
        val encrypted = crypto.encrypt(payload, "correct horse battery".toCharArray())

        assertFalse(encrypted.decodeToString().contains("synthetic-secret"))
        assertFalse(encrypted.decodeToString().contains("https://synthetic.invalid"))
        assertEquals(payload, crypto.decrypt(encrypted, "correct horse battery".toCharArray()))
    }

    @Test fun wrongPasswordAndCorruptionAreRejected() {
        val encrypted = crypto.encrypt(fixture(), "correct horse battery".toCharArray())
        assertThrows(IllegalArgumentException::class.java) {
            crypto.decrypt(encrypted, "wrong-password-value".toCharArray())
        }
        val corrupted = encrypted.copyOf().also { it[it.lastIndex - 8] = (it[it.lastIndex - 8].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            crypto.decrypt(corrupted, "correct horse battery".toCharArray())
        }
    }

    @Test fun weakPasswordEmptyAndOversizedBackupsAreRejected() {
        val weakPassword = "short".toCharArray()
        val rejectedPassword = "correct horse battery".toCharArray()
        assertThrows(IllegalArgumentException::class.java) { crypto.encrypt(fixture(), weakPassword) }
        assertThrows(IllegalArgumentException::class.java) { crypto.decrypt(byteArrayOf(), rejectedPassword) }
        assertEquals(CharArray(weakPassword.size).toList(), weakPassword.toList())
        assertEquals(CharArray(rejectedPassword.size).toList(), rejectedPassword.toList())
        assertThrows(IllegalArgumentException::class.java) {
            crypto.decrypt(ByteArray(WatchioBackupCrypto.MAX_BACKUP_BYTES + 1), "correct horse battery".toCharArray())
        }
    }

    @Test fun envelopesUseUniqueParametersAndRejectUnsupportedOrExcessiveValues() {
        val first = crypto.encrypt(fixture(), "correct horse battery".toCharArray())
        val second = crypto.encrypt(fixture(), "correct horse battery".toCharArray())
        val json = Json { encodeDefaults = true }
        val firstEnvelope = json.decodeFromString<WatchioBackupEnvelope>(first.decodeToString())
        val secondEnvelope = json.decodeFromString<WatchioBackupEnvelope>(second.decodeToString())
        assertNotEquals(firstEnvelope.salt, secondEnvelope.salt)
        assertNotEquals(firstEnvelope.nonce, secondEnvelope.nonce)

        val unsupported = json.encodeToString(firstEnvelope.copy(formatVersion = 2)).encodeToByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            crypto.decrypt(unsupported, "correct horse battery".toCharArray())
        }
        val excessive = json.encodeToString(firstEnvelope.copy(iterations = WatchioBackupCrypto.MAX_KDF_ITERATIONS + 1)).encodeToByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            crypto.decrypt(excessive, "correct horse battery".toCharArray())
        }
    }

    private fun fixture() = WatchioBackupPayload(
        applicationVersion = "test",
        databaseSchemaVersion = 10,
        createdAtEpochMs = 1,
        includedCategories = listOf("providers", "credentials"),
        excludedCategories = listOf("offline-media"),
        providers = listOf(BackupProvider("provider", "Synthetic", "Xtream", "https://synthetic.invalid", 1, 1, true)),
        favorites = listOf(BackupFavorite("provider", "Movie", "movie", "", "Movie", null, 1)),
        history = emptyList(),
        epgSources = emptyList(),
        dataStore = emptyMap(),
        sharedPreferences = emptyMap(),
        encryptedCredentials = mapOf("provider.provider.xtream_password" to "synthetic-secret"),
    )
}
