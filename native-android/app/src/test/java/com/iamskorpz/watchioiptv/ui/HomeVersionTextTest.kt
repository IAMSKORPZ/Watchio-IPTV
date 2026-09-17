package com.iamskorpz.watchioiptv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeVersionTextTest {
    @Test
    fun formatsPublicVersionFromBuildVersionName() {
        assertEquals("v0.1.3", formatHomeVersion("0.1.3"))
    }

    @Test
    fun preservesVariantSuffixFromBuildVersionName() {
        assertEquals("v0.1.3-debug", formatHomeVersion("0.1.3-debug"))
        assertEquals("v0.1.3-local", formatHomeVersion("0.1.3-local"))
        assertEquals("v0.1.3-uitest", formatHomeVersion("0.1.3-uitest"))
    }
}
