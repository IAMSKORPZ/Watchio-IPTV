package com.watchioiptv.nativeapp.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

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
    fun tamperedCiphertextFailsClosed() {
        repeatCryptoCheck {
            val session = session()
            val envelope = QuickLoginCrypto.encrypt(session.invitation, credentials)
            assertFails { session.accept(envelope.copy(payload = envelope.payload.mutateByte(0)), now) }
        }
    }

    @Test
    fun tamperedNonceFailsClosed() {
        repeatCryptoCheck {
            val session = session()
            val envelope = QuickLoginCrypto.encrypt(session.invitation, credentials)
            assertFails { session.accept(envelope.copy(iv = envelope.iv.mutateByte(0)), now) }
        }
    }

    @Test
    fun tamperedTagFailsClosed() {
        repeatCryptoCheck {
            val session = session()
            val envelope = QuickLoginCrypto.encrypt(session.invitation, credentials)
            assertFails { session.accept(envelope.copy(payload = envelope.payload.mutateByteAtEnd()), now) }
        }
    }

    @Test
    fun wrongKeyFailsClosed() {
        repeatCryptoCheck {
            val session = session()
            val invitationWithWrongKey = session.invitation.copy(receiverPublicKey = QuickLoginReceiverKeys.create().publicKey)
            val envelope = QuickLoginCrypto.encrypt(invitationWithWrongKey, credentials)
            assertFails { session.accept(envelope, now) }
        }
    }

    @Test
    fun wrongSessionFailsClosed() {
        repeatCryptoCheck {
            val session = session()
            val envelope = QuickLoginCrypto.encrypt(session.invitation, credentials)
            assertFails { session.accept(envelope.copy(session = "b".repeat(32)), now) }
        }
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

    private inline fun repeatCryptoCheck(action: () -> Unit) {
        repeat(20) { action() }
    }

    private fun String.mutateByte(index: Int): String = Base64.getUrlDecoder().decode(this)
        .also { bytes -> bytes[index] = (bytes[index].toInt() xor 1).toByte() }
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun String.mutateByteAtEnd(): String = Base64.getUrlDecoder().decode(this)
        .also { bytes -> bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte() }
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
}
