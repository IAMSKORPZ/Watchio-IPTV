package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.security.SecretStore
import com.iamskorpz.watchioiptv.feature.sports.v2.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BroadcastCredentialStoresTest {
    @Test fun soccersApiCredentialsUseDedicatedSecretKeysAndDoNotLeakFromObject() = runTest {
        val secrets = FakeSecretStore()
        val store = SecureSoccersApiCredentialStore(secrets)
        store.save(" synthetic-key ")
        val credentials = store.get()!!
        assertEquals("synthetic-key", credentials.apiKey)
        assertEquals(setOf("sports.soccers_api.api_key"), secrets.values.keys)
        assertFalse(credentials.toString().contains("synthetic-key"))
        store.remove()
        assertNull(store.get())
    }

    @Test fun legacyTokenMigratesWithoutRequiringDeprecatedUsername() = runTest {
        val secrets = FakeSecretStore().apply { values["sports.soccers_api.api_token"] = "legacy-key" }
        val store = SecureSoccersApiCredentialStore(secrets)
        assertEquals("legacy-key", store.get()?.apiKey)
        store.save("new-key")
        assertEquals(setOf("sports.soccers_api.api_key"), secrets.values.keys)
    }

    @Test fun theSportsDbKeyUsesSecretStoreAndBlankRemovesIt() = runTest {
        val secrets = FakeSecretStore()
        val store = SecureTheSportsDbCredentialStore(secrets)
        store.save(" synthetic-key ")
        assertEquals("synthetic-key", store.get())
        assertEquals(setOf("sports.the_sports_db.api_key"), secrets.values.keys)
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
