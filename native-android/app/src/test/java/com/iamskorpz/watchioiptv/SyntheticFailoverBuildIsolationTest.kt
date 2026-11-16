package com.iamskorpz.watchioiptv

import org.junit.Assert.assertEquals
import org.junit.Test

class SyntheticFailoverBuildIsolationTest {
    @Test
    fun syntheticCredentialsExistOnlyInSyntheticTestVariants() {
        if (BuildConfig.UPDATE_CHANNEL in setOf("syntheticfailovertest", "providerremovaltest")) {
            assertEquals("Watchio Synthetic A", BuildConfig.FAILOVER_TEST_PROVIDER_NAME)
            assertEquals("watchio_test_a", BuildConfig.FAILOVER_TEST_USERNAME)
            assertEquals("synthetic_only_a", BuildConfig.FAILOVER_TEST_PASSWORD)
        } else {
            assertEquals("", BuildConfig.FAILOVER_TEST_PROVIDER_NAME)
            assertEquals("", BuildConfig.FAILOVER_TEST_USERNAME)
            assertEquals("", BuildConfig.FAILOVER_TEST_PASSWORD)
        }
    }
}
