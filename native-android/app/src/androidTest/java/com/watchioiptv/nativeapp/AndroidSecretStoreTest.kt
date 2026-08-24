package com.watchioiptv.nativeapp

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watchioiptv.nativeapp.core.security.AndroidSecretStore
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.security.XtreamCredentials
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSecretStoreTest {
    @Test
    fun encryptedProviderCredentialsRoundTripAndDelete() = runBlocking {
        val store = ProviderCredentialStore(AndroidSecretStore(ApplicationProvider.getApplicationContext()))

        store.saveXtreamCredentials("android-secret-a", XtreamCredentials("fake-user", "fake-pass"))
        assertEquals("fake-user", store.getXtreamCredentials("android-secret-a")?.username)

        store.deleteProviderSecrets("android-secret-a")
        assertNull(store.getXtreamCredentials("android-secret-a"))
    }
}
