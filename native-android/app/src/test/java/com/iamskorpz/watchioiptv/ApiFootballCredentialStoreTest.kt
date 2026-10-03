package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.security.SecretStore
import com.iamskorpz.watchioiptv.feature.sports.v2.SecureApiFootballCredentialStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ApiFootballCredentialStoreTest {
    @Test fun storesOnlyInSecretStoreAndTrimsValue() = runTest {
        val secrets = FakeSecretStore()
        val store = SecureApiFootballCredentialStore(secrets)
        store.save("  synthetic-key  ")
        assertEquals("synthetic-key", store.get())
        assertEquals(setOf("sports.api_football.api_key"), secrets.values.keys)
        assertFalse(store.toString().contains("synthetic-key"))
    }

    @Test fun blankValueRemovesCredential() = runTest {
        val secrets = FakeSecretStore()
        val store = SecureApiFootballCredentialStore(secrets)
        store.save("synthetic-key")
        store.save(" ")
        assertNull(store.get())
    }

    private class FakeSecretStore : SecretStore {
        val values = mutableMapOf<String, String>()
        override suspend fun putSecret(key: String, value: String) { values[key] = value }
        override suspend fun getSecret(key: String) = values[key]
        override suspend fun removeSecret(key: String) { values.remove(key) }
    }
}
