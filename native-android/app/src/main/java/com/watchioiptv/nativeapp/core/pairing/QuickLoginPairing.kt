package com.watchioiptv.nativeapp.core.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val PairingScheme = "watchio-pair"
private const val ProtocolVersion = 1
private const val PairingLifetimeMs = 120_000L
private const val CipherTransformation = "AES/GCM/NoPadding"
private const val GcmTagLengthBits = 128
private const val AesKeyLengthBytes = 32
private const val HkdfHmac = "HmacSHA256"
private const val HkdfSalt = "Watchio Quick Login HKDF salt v1"
private const val HkdfInfoPrefix = "Watchio Quick Login AES-GCM v1"

@Serializable
data class QuickLoginCredentials(
    val providerName: String,
    val serverUrl: String,
    val username: String,
    val password: String,
) {
    override fun toString(): String = "QuickLoginCredentials(providerName=***, serverUrl=***, username=***, password=***)"
}

@Serializable
data class QuickLoginEnvelope(
    val version: Int,
    val session: String,
    val expiresAtEpochMs: Long,
    val senderPublicKey: String,
    val iv: String,
    val payload: String,
)

data class QuickLoginInvitation(
    val version: Int,
    val host: String,
    val port: Int,
    val session: String,
    val expiresAtEpochMs: Long,
    val receiverPublicKey: String,
) {
    fun encode(): String = buildString {
        append("$PairingScheme://join?")
        append("v=").append(version)
        append("&host=").append(host.urlEncode())
        append("&port=").append(port)
        append("&session=").append(session.urlEncode())
        append("&exp=").append(expiresAtEpochMs)
        append("&key=").append(receiverPublicKey.urlEncode())
    }

    companion object {
        fun parse(rawValue: String): QuickLoginInvitation? = runCatching {
            val uri = java.net.URI(rawValue)
            require(uri.scheme == PairingScheme && uri.host == "join")
            val values = uri.rawQuery.orEmpty().split('&').associate { part ->
                val keyValue = part.split('=', limit = 2)
                keyValue.first().urlDecode() to keyValue.getOrElse(1) { "" }.urlDecode()
            }
            QuickLoginInvitation(
                version = values.getValue("v").toInt().also { require(it == ProtocolVersion) },
                host = values.getValue("host").also { require(it.isNotBlank()) },
                port = values.getValue("port").toInt().also { require(it in 1..65535) },
                session = values.getValue("session").also { require(it.length == 32) },
                expiresAtEpochMs = values.getValue("exp").toLong().also { require(it > 0) },
                receiverPublicKey = values.getValue("key").also { require(it.isNotBlank()) },
            )
        }.getOrNull()
    }
}

class QuickLoginReceiverKeys private constructor(val keyPair: KeyPair) {
    val publicKey: String = keyPair.public.encoded.toBase64Url()

    companion object {
        fun create(): QuickLoginReceiverKeys {
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(ECGenParameterSpec("secp256r1"))
            return QuickLoginReceiverKeys(generator.generateKeyPair())
        }
    }
}

class QuickLoginSession private constructor(
    val invitation: QuickLoginInvitation,
    private var receiverKeys: QuickLoginReceiverKeys?,
    private var state: State = State.Active,
) {
    private enum class State { Active, Consumed, Expired, Closed }

    fun accept(envelope: QuickLoginEnvelope, nowEpochMs: Long): QuickLoginCredentials = synchronized(this) {
        check(state == State.Active) { "Quick Login session is no longer active." }
        if (nowEpochMs > invitation.expiresAtEpochMs) {
            clear(State.Expired)
            throw IllegalStateException("Quick Login code expired.")
        }
        val credentials = QuickLoginCrypto.decrypt(receiverKeys ?: error("Quick Login session closed."), envelope, invitation)
        clear(State.Consumed)
        credentials
    }

    fun close() = synchronized(this) { clear(State.Closed) }
    fun isActive(nowEpochMs: Long): Boolean = synchronized(this) { state == State.Active && nowEpochMs <= invitation.expiresAtEpochMs }

    private fun clear(newState: State) {
        receiverKeys = null
        state = newState
    }

    companion object {
        fun create(host: String, port: Int, nowEpochMs: Long): QuickLoginSession {
            val keys = QuickLoginReceiverKeys.create()
            val invitation = QuickLoginInvitation(
                version = ProtocolVersion,
                host = host,
                port = port,
                session = java.util.UUID.randomUUID().toString().replace("-", ""),
                expiresAtEpochMs = nowEpochMs + PairingLifetimeMs,
                receiverPublicKey = keys.publicKey,
            )
            return QuickLoginSession(invitation, keys)
        }
    }
}

object QuickLoginCrypto {
    private val json = Json { ignoreUnknownKeys = false }

    fun encrypt(invitation: QuickLoginInvitation, credentials: QuickLoginCredentials): QuickLoginEnvelope {
        val senderKeys = QuickLoginReceiverKeys.create()
        val secret = deriveAesKey(senderKeys.keyPair, invitation.receiverPublicKey.base64UrlDecode(), invitation)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(CipherTransformation).apply {
            init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(GcmTagLengthBits, iv))
            updateAAD(invitation.aad())
        }
        return QuickLoginEnvelope(
            version = invitation.version,
            session = invitation.session,
            expiresAtEpochMs = invitation.expiresAtEpochMs,
            senderPublicKey = senderKeys.publicKey,
            iv = iv.toBase64Url(),
            payload = cipher.doFinal(json.encodeToString(QuickLoginCredentials.serializer(), credentials).toByteArray(StandardCharsets.UTF_8)).toBase64Url(),
        )
    }

    fun decrypt(receiverKeys: QuickLoginReceiverKeys, envelope: QuickLoginEnvelope, invitation: QuickLoginInvitation): QuickLoginCredentials {
        require(envelope.version == invitation.version && envelope.session == invitation.session && envelope.expiresAtEpochMs == invitation.expiresAtEpochMs)
        val secret = deriveAesKey(receiverKeys.keyPair, envelope.senderPublicKey.base64UrlDecode(), invitation)
        val cipher = Cipher.getInstance(CipherTransformation).apply {
            init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(GcmTagLengthBits, envelope.iv.base64UrlDecode()))
            updateAAD(invitation.aad())
        }
        return json.decodeFromString(QuickLoginCredentials.serializer(), cipher.doFinal(envelope.payload.base64UrlDecode()).toString(StandardCharsets.UTF_8))
    }

    fun encodeEnvelope(envelope: QuickLoginEnvelope): String = json.encodeToString(QuickLoginEnvelope.serializer(), envelope)
    fun decodeEnvelope(value: String): QuickLoginEnvelope = json.decodeFromString(QuickLoginEnvelope.serializer(), value)

    private fun deriveAesKey(keyPair: KeyPair, otherPublicKey: ByteArray, invitation: QuickLoginInvitation): SecretKeySpec {
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(otherPublicKey))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(keyPair.private)
        agreement.doPhase(publicKey, true)
        return SecretKeySpec(hkdfSha256(agreement.generateSecret(), invitation.aad()), "AES")
    }

    private fun hkdfSha256(ikm: ByteArray, context: ByteArray): ByteArray {
        val extract = Mac.getInstance(HkdfHmac)
        extract.init(SecretKeySpec(HkdfSalt.toByteArray(StandardCharsets.UTF_8), HkdfHmac))
        val prk = extract.doFinal(ikm)
        val expand = Mac.getInstance(HkdfHmac)
        expand.init(SecretKeySpec(prk, HkdfHmac))
        return expand.doFinal((HkdfInfoPrefix.toByteArray(StandardCharsets.UTF_8) + byteArrayOf(0) + context + byteArrayOf(1))).copyOf(AesKeyLengthBytes)
    }
}

private fun QuickLoginInvitation.aad(): ByteArray = "$version|$session|$expiresAtEpochMs".toByteArray(StandardCharsets.UTF_8)
private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
private fun String.urlDecode(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())
private fun ByteArray.toBase64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)
private fun String.base64UrlDecode(): ByteArray = Base64.getUrlDecoder().decode(this)
