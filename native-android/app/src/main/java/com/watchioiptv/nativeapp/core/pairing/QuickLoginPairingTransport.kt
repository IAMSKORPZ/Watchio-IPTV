package com.watchioiptv.nativeapp.core.pairing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit

private val PairingTimeoutMs = TimeUnit.MINUTES.toMillis(2)
private const val PairingIoTimeoutMs = 10_000
private const val MaxEnvelopeBytes = 16_384
private const val QuickLoginLogTag = "QuickLogin"

class QuickLoginReceiver(
    private val scope: CoroutineScope,
    private val onCredentials: (QuickLoginCredentials) -> Unit,
    private val onError: (String) -> Unit,
    private val hostProvider: () -> String? = ::localIpv4Address,
    private val serverFactory: () -> ServerSocket = { ServerSocket(0) },
    private val isAllowedPeer: (InetAddress) -> Boolean = InetAddress::isLocalNetworkAddress,
    private val ioTimeoutMs: Int = PairingIoTimeoutMs,
) {
    private var socket: ServerSocket? = null
    private var receiverJob: Job? = null

    fun start(): QuickLoginInvitation {
        close()
        val host = hostProvider() ?: throw IllegalStateException("Connect TV to Wi-Fi before using Quick Login.")
        val server = serverFactory().apply { soTimeout = PairingTimeoutMs.toInt() }
        socket = server
        val session = QuickLoginSession.create(host, server.localPort, System.currentTimeMillis())
        stage("receiver_started")
        receiverJob = scope.launch(Dispatchers.IO) { accept(server, session) }
        return session.invitation
    }

    fun close() {
        receiverJob?.cancel()
        receiverJob = null
        socket?.close()
        socket = null
    }

    private fun accept(server: ServerSocket, session: QuickLoginSession) {
        try {
            while (!server.isClosed) {
                server.accept().use { client ->
                    client.soTimeout = ioTimeoutMs
                    stage("receiver_accept")
                    if (!isAllowedPeer(client.inetAddress)) {
                        client.respond(400)
                        continue
                    }
                    val envelope = client.readEnvelope()
                    if (envelope == null) {
                        client.respond(400)
                        continue
                    }
                    stage("receiver_decrypt")
                    val credentials = runCatching { session.accept(envelope, System.currentTimeMillis()) }.getOrElse {
                        client.respond(400)
                        return@use
                    }
                    client.respond(202)
                    stage("response_sent")
                    close()
                    onCredentials(credentials)
                    return
                }
            }
        } catch (error: SocketTimeoutException) {
            if (!server.isClosed) {
                close()
                onError("Quick Login code expired. Start a new code.")
            }
        } catch (error: Exception) {
            session.close()
            if (!server.isClosed) {
                close()
                onError("Quick Login connection failed.")
            }
        }
    }
}

object QuickLoginSender {
    suspend fun send(invitation: QuickLoginInvitation, credentials: QuickLoginCredentials) = send(
        invitation = invitation,
        credentials = credentials,
        isAllowedDestination = InetAddress::isLocalNetworkAddress,
        timeoutMs = PairingIoTimeoutMs,
    )

    internal suspend fun send(
        invitation: QuickLoginInvitation,
        credentials: QuickLoginCredentials,
        isAllowedDestination: (InetAddress) -> Boolean,
        timeoutMs: Int,
    ) = withContext(Dispatchers.IO) {
        stage("sender_started")
        val address = InetAddress.getByName(invitation.host)
        require(isAllowedDestination(address)) { "QR code must belong to a TV on your local Wi-Fi." }
        stage("destination_validated")
        val host = if (invitation.host.contains(':')) "[${invitation.host}]" else invitation.host
        val body = QuickLoginCrypto.encodeEnvelope(QuickLoginCrypto.encrypt(invitation, credentials)).toByteArray(Charsets.UTF_8)
        stage("payload_encrypted")
        val connection = (URL("http://$host:${invitation.port}/pair").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setFixedLengthStreamingMode(body.size)
        }
        try {
            stage("connect_started")
            connection.outputStream.use { output ->
                stage("socket_connected")
                output.write(body)
                output.flush()
                stage("payload_sent")
            }
            val responseCode = connection.responseCode
            stage("response_received")
            require(responseCode == 202) { "TV did not accept Quick Login. Scan a new QR code." }
        } catch (error: SocketTimeoutException) {
            throw IllegalStateException("Couldn't connect to TV. Make sure both devices are on the same Wi-Fi.")
        } catch (error: IOException) {
            throw IllegalStateException("Couldn't connect to TV. Make sure both devices are on the same Wi-Fi.")
        } finally {
            connection.disconnect()
        }
    }
}

private fun Socket.readEnvelope(): QuickLoginEnvelope? {
    val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
    val request = reader.readLine() ?: return null
    if (request != "POST /pair HTTP/1.1") return null
    var contentLength = 0
    while (true) {
        val line = reader.readLine() ?: return null
        if (line.isEmpty()) break
        if (line.startsWith("Content-Length:", ignoreCase = true)) {
            contentLength = line.substringAfter(':').trim().toIntOrNull() ?: return null
        }
    }
    if (contentLength !in 1..MaxEnvelopeBytes) return null
    val body = CharArray(contentLength)
    var offset = 0
    while (offset < contentLength) {
        val read = reader.read(body, offset, contentLength - offset)
        if (read < 0) return null
        offset += read
    }
    return runCatching { QuickLoginCrypto.decodeEnvelope(body.concatToString()) }.getOrNull()
}

private fun Socket.respond(code: Int) {
    getOutputStream().bufferedWriter().use { writer ->
        writer.write("HTTP/1.1 $code ${if (code == 202) "Accepted" else "Bad Request"}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
        writer.flush()
    }
}

private fun stage(name: String) {
    println("$QuickLoginLogTag:$name")
}

private fun localIpv4Address(): String? = NetworkInterface.getNetworkInterfaces().toList()
    .filter { it.isUp && !it.isLoopback }
    .flatMap { it.inetAddresses.toList() }
    .firstOrNull { it.isLocalNetworkAddress() }
    ?.hostAddress

private fun InetAddress.isLocalNetworkAddress(): Boolean = isSiteLocalAddress || isLinkLocalAddress
