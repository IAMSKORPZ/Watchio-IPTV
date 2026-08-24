package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.feature.provider.XtreamProviderFormState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamProviderFormStateTest {
    @Test
    fun requiresAllFieldsBeforeSubmit() {
        assertFalse(XtreamProviderFormState().canSubmit)
        assertTrue(
            XtreamProviderFormState(
                providerName = "Provider",
                serverUrl = "http://example.invalid",
                username = "fake-user",
                password = "fake-pass",
            ).canSubmit,
        )
    }
}
