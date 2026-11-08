package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.data.xtream.AccountValidationResult
import com.iamskorpz.watchioiptv.data.xtream.AccountBlockReason
import com.iamskorpz.watchioiptv.data.xtream.XtreamAuthInfo
import com.iamskorpz.watchioiptv.data.xtream.classifyAccountValidation
import com.iamskorpz.watchioiptv.feature.provider.ProviderAccountState
import com.iamskorpz.watchioiptv.feature.provider.toProviderAccountState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountValidationTest {
    @Test fun activeAccountIsAccepted() {
        assertEquals(AccountValidationResult.Active, classifyAccountValidation(auth(true, "Active", "2"), 1_000L))
    }

    @Test fun expiredDisabledAndRevokedAccountsAreBlocked() {
        val expired = classifyAccountValidation(auth(true, "Active", "1"), 2_000L) as AccountValidationResult.Blocked
        val disabled = classifyAccountValidation(auth(true, "Disabled"), 2_000L) as AccountValidationResult.Blocked
        val revoked = classifyAccountValidation(auth(true, "Revoked"), 2_000L) as AccountValidationResult.Blocked

        assertEquals(AccountBlockReason.Expired, expired.reason)
        assertEquals(AccountBlockReason.Disabled, disabled.reason)
        assertEquals(AccountBlockReason.Revoked, revoked.reason)
        assertEquals(ProviderAccountState.Expired, expired.toProviderAccountState())
        assertEquals(ProviderAccountState.Disabled, disabled.toProviderAccountState())
        assertEquals(ProviderAccountState.Revoked, revoked.toProviderAccountState())
    }

    @Test fun rejectedAuthenticationTakesPriorityOverUntrustedStatusText() {
        val blocked = classifyAccountValidation(auth(false, "Disabled"), 2_000L) as AccountValidationResult.Blocked

        assertEquals(AccountBlockReason.InvalidCredentials, blocked.reason)
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
