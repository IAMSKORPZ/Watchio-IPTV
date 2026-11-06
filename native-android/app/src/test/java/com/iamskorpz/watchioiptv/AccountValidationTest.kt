package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.data.xtream.AccountValidationResult
import com.iamskorpz.watchioiptv.data.xtream.XtreamAuthInfo
import com.iamskorpz.watchioiptv.data.xtream.classifyAccountValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountValidationTest {
    @Test fun activeAccountIsAccepted() {
        assertEquals(AccountValidationResult.Active, classifyAccountValidation(auth(true, "Active", "2"), 1_000L))
    }

    @Test fun expiredDisabledAndRevokedAccountsAreBlocked() {
        assertTrue(classifyAccountValidation(auth(true, "Active", "1"), 2_000L) is AccountValidationResult.Blocked)
        assertTrue(classifyAccountValidation(auth(false, "Disabled"), 2_000L) is AccountValidationResult.Blocked)
        assertTrue(classifyAccountValidation(auth(false, "Revoked"), 2_000L) is AccountValidationResult.Blocked)
    }

    @Test fun unknownAuthenticatedStatusRequiresRetry() {
        assertTrue(classifyAccountValidation(auth(true, "Pending"), 1_000L) is AccountValidationResult.Retryable)
    }

    private fun auth(authenticated: Boolean, status: String?, expiration: String? = null) = XtreamAuthInfo(
        authenticated = authenticated,
        username = null,
        status = status,
        expiration = expiration,
        trial = null,
        activeConnections = null,
        maxConnections = null,
        allowedOutputFormats = emptyList(),
        serverUrl = null,
        serverProtocol = null,
        timezone = null,
    )
}
