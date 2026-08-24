package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.security.SecretStore
import com.watchioiptv.nativeapp.core.security.XtreamCredentials
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderCredentialStoreTest {
    @Test
    fun credentialsDoNotExposeSecretsInToString() {
        val credentials = XtreamCredentials("testuser", "testpass")

        assertFalse(credentials.toString().contains("testuser"))
        assertFalse(credentials.toString().contains("testpass"))
    }

    @Test
    fun saveReplaceDeleteAndProviderIsolation() = runTest {
        val store = ProviderCredentialStore(InMemorySecretStore())

        store.saveXtreamCredentials("provider-a", XtreamCredentials("user-a", "pass-a"))
        store.saveXtreamCredentials("provider-b", XtreamCredentials("user-b", "pass-b"))

        assertEquals("user-a", store.getXtreamCredentials("provider-a")?.username)
        assertEquals("pass-b", store.getXtreamCredentials("provider-b")?.password)

        store.saveXtreamCredentials("provider-a", XtreamCredentials("user-a2", "pass-a2"))
        assertEquals("user-a2", store.getXtreamCredentials("provider-a")?.username)

        store.deleteProviderSecrets("provider-a")
        assertNull(store.getXtreamCredentials("provider-a"))
        assertEquals("user-b", store.getXtreamCredentials("provider-b")?.username)
    }

    private class InMemorySecretStore : SecretStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun putSecret(key: String, value: String) {
            values[key] = value
        }
        override suspend fun getSecret(key: String): String? = values[key]
        override suspend fun removeSecret(key: String) {
            values.remove(key)
        }
    }
}
