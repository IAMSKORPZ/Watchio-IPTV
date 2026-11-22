package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.backup.BackupFavorite
import com.iamskorpz.watchioiptv.feature.backup.BackupProvider
import com.iamskorpz.watchioiptv.feature.backup.WatchioBackupCrypto
import com.iamskorpz.watchioiptv.feature.backup.WatchioBackupPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
