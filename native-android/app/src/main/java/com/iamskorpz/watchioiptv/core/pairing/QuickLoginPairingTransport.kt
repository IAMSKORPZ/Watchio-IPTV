package com.iamskorpz.watchioiptv.core.pairing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.Inet4Address
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
        close("restart_before_start")
        stage("receiver_started")
        val host = hostProvider() ?: throw IllegalStateException("Connect TV to Wi-Fi before using Quick Login.")
        val server = serverFactory()
        stage("receiver_socket_created")
        server.soTimeout = PairingTimeoutMs.toInt()
        socket = server
        stage("receiver_socket_bound address=${server.inetAddress.hostAddress} port=${server.localPort}")
        stage("receiver_listening")
        val session = QuickLoginSession.create(host, server.localPort, System.currentTimeMillis())
        stage("qr_endpoint_created address=$host port=${server.localPort}")
        receiverJob = scope.launch(Dispatchers.IO) { accept(server, session) }
        return session.invitation
    }

    fun close(reason: String = "lifecycle_close") {
        val wasActive = receiverJob != null || socket != null
        receiverJob?.cancel()
        receiverJob = null
        socket?.close()
        socket = null
        if (wasActive) stage("receiver_stopped reason=$reason")
    }

    private fun accept(server: ServerSocket, session: QuickLoginSession) {
        try {
            while (!server.isClosed) {
                stage("receiver_accept_waiting")
                server.accept().use { client ->
                    client.soTimeout = ioTimeoutMs
                    stage("receiver_client_accepted address=${client.inetAddress.hostAddress}")
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
                    close("credentials_received")
                    onCredentials(credentials)
                    return
                }
            }
        } catch (error: SocketTimeoutException) {
            stage("receiver_accept_failed type=${error.javaClass.simpleName} message=${error.safeMessage()}")
            if (!server.isClosed) {
                close("accept_timeout")
                onError("Quick Login code expired. Start a new code.")
            }
        } catch (error: Exception) {
            stage("receiver_accept_failed type=${error.javaClass.simpleName} message=${error.safeMessage()}")
            session.close()
            if (!server.isClosed) {
                close("accept_error")
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
        stage("sender_destination address=${invitation.host} port=${invitation.port}")
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
            stage("connect_failed type=${error.javaClass.simpleName} message=${error.safeMessage()}")
            throw IllegalStateException("Couldn't connect to TV. Make sure both devices are on the same Wi-Fi.")
        } catch (error: IOException) {
            stage("connect_failed type=${error.javaClass.simpleName} message=${error.safeMessage()}")
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

private fun localIpv4Address(): String? {
    val selected = selectLanAddress(NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { networkInterface ->
            networkInterface.inetAddresses.toList().map { address ->
                LanAddressCandidate(
                    interfaceName = networkInterface.name,
                    isUp = networkInterface.isUp,
                    isLoopbackInterface = networkInterface.isLoopback,
                    isVirtual = networkInterface.isVirtual,
                    address = address,
                )
            }
        }
    ) ?: return null
    stage("receiver_interface_selected name=${selected.interfaceName}")
    stage("receiver_ip_selected address=${selected.address.hostAddress}")
    return selected.address.hostAddress
}

internal data class LanAddressCandidate(
    val interfaceName: String,
    val isUp: Boolean,
    val isLoopbackInterface: Boolean,
    val isVirtual: Boolean,
    val address: InetAddress,
)

internal fun selectLanAddress(candidates: List<LanAddressCandidate>): LanAddressCandidate? = candidates
    .asSequence()
    .filter { it.isUsableIpv4() }
    .sortedWith(
        compareByDescending<LanAddressCandidate> { it.address.isPrivateIpv4() }
            .thenByDescending { it.physicalInterfacePriority() }
            .thenBy { it.interfaceName }
            .thenBy { it.address.hostAddress },
    )
    .firstOrNull()

private fun LanAddressCandidate.isUsableIpv4(): Boolean =
    isUp &&
        !isLoopbackInterface &&
        !isVirtual &&
        !interfaceName.startsWith("dummy", ignoreCase = true) &&
        address is Inet4Address &&
        !address.isLoopbackAddress &&
        !address.isLinkLocalAddress &&
        !address.isMulticastAddress &&
        !address.isAnyLocalAddress

private fun LanAddressCandidate.physicalInterfacePriority(): Int = when {
    interfaceName.startsWith("wlan", ignoreCase = true) -> 3
    interfaceName.startsWith("eth", ignoreCase = true) -> 2
    interfaceName.startsWith("ethernet", ignoreCase = true) -> 2
    else -> 1
}

private fun InetAddress.isPrivateIpv4(): Boolean {
    if (this !is Inet4Address) return false
    val octets = address.map { it.toInt() and 0xff }
    return octets[0] == 10 ||
        (octets[0] == 172 && octets[1] in 16..31) ||
        (octets[0] == 192 && octets[1] == 168)
}

private fun Throwable.safeMessage(): String = message.orEmpty().replace('\r', ' ').replace('\n', ' ').take(200)

private fun InetAddress.isLocalNetworkAddress(): Boolean = isSiteLocalAddress || isLinkLocalAddress
