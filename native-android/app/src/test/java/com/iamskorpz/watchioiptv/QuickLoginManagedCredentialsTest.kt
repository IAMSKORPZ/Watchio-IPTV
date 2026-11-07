package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.pairing.QuickLoginCredentials
import com.iamskorpz.watchioiptv.feature.provider.toManagedInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickLoginManagedCredentialsTest {
    @Test
    fun receivedCredentialsAlwaysUseManagedEndpointSelection() {
        val input = QuickLoginCredentials(
            providerName = "Living Room",
            serverUrl = "https://legacy.example",
            username = "user",
            password = "password",
            mode = "legacy",
        ).toManagedInput()

        assertEquals("Living Room", input.displayName)
        assertEquals("user", input.username)
        assertEquals("password", input.password)
        assertNull(input.serverUrl)
        assertTrue(input.managed)
    }
}
