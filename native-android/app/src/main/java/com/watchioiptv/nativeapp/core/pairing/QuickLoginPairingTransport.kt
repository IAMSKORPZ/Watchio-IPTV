package com.watchioiptv.nativeapp.core.pairing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit

private val PairingTimeoutMs = TimeUnit.MINUTES.toMillis(2)

class QuickLoginReceiver(
    private val scope: CoroutineScope,
    private val onCredentials: (QuickLoginCredentials) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var socket: ServerSocket? = null
    private var receiverJob: Job? = null

    fun start(): QuickLoginInvitation {
        close()
        val host = localIpv4Address() ?: throw IllegalStateException("Connect TV to Wi-Fi before using Quick Login.")
        val server = ServerSocket(0).apply { soTimeout = PairingTimeoutMs.toInt() }
        socket = server
        val session = QuickLoginSession.create(host, server.localPort, System.currentTimeMillis())
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
                    if (!client.inetAddress.isLocalNetworkAddress()) {
                        client.respond(400)
                        continue
                    }
                    val envelope = client.readEnvelope()
                    if (envelope == null) {
                        client.respond(400)
                        continue
                    }
                    val credentials = runCatching { session.accept(envelope, System.currentTimeMillis()) }.getOrElse {
                        client.respond(400)
                        return@use
                    }
                    client.respond(202)
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
            if (!server.isClosed) onError("Quick Login connection failed.")
        }
    }
}

object QuickLoginSender {
    suspend fun send(invitation: QuickLoginInvitation, credentials: QuickLoginCredentials) = withContext(Dispatchers.IO) {
        val address = InetAddress.getByName(invitation.host)
        require(address.isLocalNetworkAddress()) { "QR code must belong to a TV on your local Wi-Fi." }
        val host = if (invitation.host.contains(':')) "[${invitation.host}]" else invitation.host
        val connection = (URL("http://$host:${invitation.port}/pair").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { output ->
            output.write(QuickLoginCrypto.encodeEnvelope(QuickLoginCrypto.encrypt(invitation, credentials)).toByteArray(Charsets.UTF_8))
        }
        require(connection.responseCode == 202) { "TV did not accept Quick Login. Scan a new QR code." }
        connection.disconnect()
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
    if (contentLength !in 1..16_384) return null
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

private fun localIpv4Address(): String? = NetworkInterface.getNetworkInterfaces().toList()
    .filter { it.isUp && !it.isLoopback }
    .flatMap { it.inetAddresses.toList() }
    .firstOrNull { it.isLocalNetworkAddress() }
    ?.hostAddress

private fun InetAddress.isLocalNetworkAddress(): Boolean = isSiteLocalAddress || isLinkLocalAddress
