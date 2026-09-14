package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.data.xtream.XtreamImportStage
import com.watchioiptv.nativeapp.data.xtream.XtreamImportState
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
                username = "fake-user",
                password = "fake-pass",
            ).canSubmit,
        )
    }

    @Test
    fun importingStateBlocksRepeatSubmit() {
        assertFalse(
            XtreamProviderFormState(
                providerName = "Provider",
                username = "fake-user",
                password = "fake-pass",
                importState = XtreamImportState.Importing(XtreamImportStage.Authenticating, "Provider"),
            ).canSubmit,
        )
    }
}
