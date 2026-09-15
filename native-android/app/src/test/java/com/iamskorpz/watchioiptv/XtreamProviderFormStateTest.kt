package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.data.xtream.XtreamImportStage
import com.iamskorpz.watchioiptv.data.xtream.XtreamImportState
import com.iamskorpz.watchioiptv.feature.provider.XtreamProviderFormState
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
