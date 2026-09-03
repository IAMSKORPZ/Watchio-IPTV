package com.watchioiptv.nativeapp.core.pairing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class QuickLoginPairingTransportTest {
    private val credentials = QuickLoginCredentials("Test", "https://example.com", "user", "password")

    @Test
    fun localTransportRoundTripCompletes() = runBlocking {
        val received = CountDownLatch(1)
        var actual: QuickLoginCredentials? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val receiver = loopbackReceiver(scope, { credentials -> actual = credentials; received.countDown() }, {})

        try {
            val invitation = receiver.start()
            QuickLoginSender.send(invitation, credentials, { true }, 1_000)

            assertTrue(received.await(2, TimeUnit.SECONDS))
            assertEquals(credentials, actual)
        } finally {
            receiver.close()
            scope.cancel()
        }
    }

    @Test
    fun unavailableReceiverFailsWithoutIndefiniteSuspend() = runBlocking {
        val closedPort = ServerSocket(0).use { it.localPort }
        val invitation = QuickLoginSession.create("127.0.0.1", closedPort, System.currentTimeMillis()).invitation

        val failure = runCatching { QuickLoginSender.send(invitation, credentials, { true }, 250) }.exceptionOrNull()

        assertTrue(failure != null)
    }

    @Test
    fun handshakeReadTimesOutAndClosesReceiver() {
        val error = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val receiver = loopbackReceiver(scope, {}, { error.countDown() }, ioTimeoutMs = 100)

        try {
            val invitation = receiver.start()
            Socket(InetAddress.getLoopbackAddress(), invitation.port).use {
                assertTrue(error.await(1, TimeUnit.SECONDS))
            }
        } finally {
            receiver.close()
            scope.cancel()
        }
    }

    @Test
    fun malformedFramingIsRejectedWithoutCredentials() {
        val received = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val receiver = loopbackReceiver(scope, { received.countDown() }, {})

        try {
            val invitation = receiver.start()
            Socket(InetAddress.getLoopbackAddress(), invitation.port).use { socket ->
                val writer = socket.getOutputStream().bufferedWriter()
                writer.write("POST /wrong HTTP/1.1\r\nContent-Length: 1\r\n\r\n{")
                writer.flush()
                assertEquals("HTTP/1.1 400 Bad Request", socket.getInputStream().bufferedReader().readLine())
            }
            assertTrue(!received.await(100, TimeUnit.MILLISECONDS))
        } finally {
            receiver.close()
            scope.cancel()
        }
    }

    private fun loopbackReceiver(
        scope: CoroutineScope,
        onCredentials: (QuickLoginCredentials) -> Unit,
        onError: (String) -> Unit,
        ioTimeoutMs: Int = 1_000,
    ) = QuickLoginReceiver(
        scope = scope,
        onCredentials = onCredentials,
        onError = onError,
        hostProvider = { "127.0.0.1" },
        serverFactory = { ServerSocket(0, 50, InetAddress.getLoopbackAddress()) },
        isAllowedPeer = { true },
        ioTimeoutMs = ioTimeoutMs,
    )
}
