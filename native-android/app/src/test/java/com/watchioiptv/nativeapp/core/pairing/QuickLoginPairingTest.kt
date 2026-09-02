package com.watchioiptv.nativeapp.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickLoginPairingTest {
    private val now = 1_700_000_000_000L
    private val credentials = QuickLoginCredentials("Home IPTV", "https://example.com", "user", "password")

    @Test
    fun validInvitationRoundTripsWithoutCredentials() {
        val session = session()
        val encoded = session.invitation.encode()

        assertEquals(session.invitation, QuickLoginInvitation.parse(encoded))
        assertFalse(encoded.contains(credentials.username))
        assertFalse(encoded.contains(credentials.password))
        assertFalse(encoded.contains("payload"))
    }

    @Test
    fun malformedUnsupportedAndIncompleteInvitationsRejected() {
        assertNull(QuickLoginInvitation.parse("https://example.com/not-watchio"))
        assertNull(QuickLoginInvitation.parse("watchio-pair://join?v=2&host=192.168.1.2&port=1&session=${"a".repeat(32)}&exp=1&key=test"))
        assertNull(QuickLoginInvitation.parse("watchio-pair://join?v=1&host=192.168.1.2&port=0&exp=1&key=test"))
        assertNull(QuickLoginInvitation.parse("watchio-pair://join?v=1&host=192.168.1.2&port=1&session=${"a".repeat(32)}&exp=1"))
    }

    @Test
    fun freshSessionsUseFreshSessionAndEphemeralKey() {
        val first = session()
        val second = session()

        assertNotEquals(first.invitation.session, second.invitation.session)
        assertNotEquals(first.invitation.receiverPublicKey, second.invitation.receiverPublicKey)
    }

    @Test
    fun ecdhHkdfAndAesGcmRoundTrip() {
        val session = session()
        val envelope = QuickLoginCrypto.encrypt(session.invitation, credentials)

        assertEquals(credentials, session.accept(envelope, now))
    }

    @Test
    fun tamperedCiphertextNonceTagWrongKeyAndSessionFailClosed() {
        assertFails { session().accept(QuickLoginCrypto.encrypt(session().invitation, credentials), now) }

        val validSession = session()
        val valid = QuickLoginCrypto.encrypt(validSession.invitation, credentials)
        assertFails { validSession.accept(valid.copy(payload = valid.payload.dropLast(1) + "A"), now) }

        val nonceSession = session()
        val nonce = QuickLoginCrypto.encrypt(nonceSession.invitation, credentials)
        assertFails { nonceSession.accept(nonce.copy(iv = nonce.iv.dropLast(1) + "A"), now) }

        val sessionIdSession = session()
        val sessionId = QuickLoginCrypto.encrypt(sessionIdSession.invitation, credentials)
        assertFails { sessionIdSession.accept(sessionId.copy(session = "b".repeat(32)), now) }
    }

    @Test
    fun expiryAndReplayRejectAndClearSession() {
        val expired = session()
        val expiredEnvelope = QuickLoginCrypto.encrypt(expired.invitation, credentials)
        assertFails { expired.accept(expiredEnvelope, expired.invitation.expiresAtEpochMs + 1) }
        assertFalse(expired.isActive(now))

        val consumed = session()
        val envelope = QuickLoginCrypto.encrypt(consumed.invitation, credentials)
        assertEquals(credentials, consumed.accept(envelope, now))
        assertFails { consumed.accept(envelope, now) }
        assertFalse(consumed.isActive(now))
    }

    private fun session(): QuickLoginSession = QuickLoginSession.create("192.168.1.20", 45678, now)

    private fun assertFails(action: () -> Unit) {
        runCatching(action).onSuccess { throw AssertionError("Expected operation to fail") }
    }
}
