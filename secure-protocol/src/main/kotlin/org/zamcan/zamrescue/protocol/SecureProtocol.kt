package org.zamcan.zamrescue.protocol

import com.google.crypto.tink.subtle.Hkdf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Protocol constants and cryptographic operations shared by Android and JVM tests. */
object RescueCrypto {
    private val curve = ECGenParameterSpec("secp256r1")
    private val p256Parameters: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(ECParameterSpec::class.java)
        }
    }

    fun generateEcKeyPair(random: SecureRandom = SecureRandom()): KeyPair =
        KeyPairGenerator.getInstance("EC").run {
            initialize(curve, random)
            generateKeyPair()
        }

    fun sign(privateKey: PrivateKey, message: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(message)
            sign()
        }

    fun verify(publicKey: PublicKey, message: ByteArray, signature: ByteArray): Boolean = try {
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(message)
            verify(signature)
        }
    } catch (_: GeneralSecurityException) {
        false
    }

    fun decodeEcPublicKey(encoded: ByteArray): PublicKey {
        require(encoded.size in 1..MAX_PUBLIC_KEY_BYTES) { "Invalid public-key size" }
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded)) as? ECPublicKey
            ?: throw GeneralSecurityException("Not an EC public key")
        if (!sameCurve(key.params, p256Parameters)) throw GeneralSecurityException("EC key is not P-256")
        return key
    }

    private fun sameCurve(left: ECParameterSpec, right: ECParameterSpec): Boolean {
        val leftField = left.curve.field as? ECFieldFp ?: return false
        val rightField = right.curve.field as? ECFieldFp ?: return false
        return leftField.p == rightField.p &&
            left.curve.a == right.curve.a && left.curve.b == right.curve.b &&
            left.generator.affineX == right.generator.affineX &&
            left.generator.affineY == right.generator.affineY &&
            left.order == right.order && left.cofactor == right.cofactor
    }

    fun agree(privateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run {
            init(privateKey)
            doPhase(peerPublicKey, true)
            generateSecret()
        }

    /** RFC 5869 HKDF-SHA-256. Output is bounded to the RFC maximum. */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * HASH_LENGTH)) { "Invalid HKDF output length" }
        return Hkdf.computeHkdf("HMACSHA256", ikm, salt, info, length)
    }

    internal fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    const val HASH_LENGTH = 32
    const val MAX_PUBLIC_KEY_BYTES = 256
}

class ProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A peer identity is the X.509-encoded EC public key approved during explicit enrollment. */
@JvmInline
value class PeerFingerprint(val hex: String) {
    companion object {
        fun of(publicKey: ByteArray): PeerFingerprint = PeerFingerprint(
            RescueCrypto.sha256(publicKey).joinToString("") {
                (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
            }
        )
    }
}

data class ServerHello(
    val identityPublicKey: ByteArray,
    val ephemeralPublicKey: ByteArray,
    val invitationNonce: ByteArray,
    val serverNonce: ByteArray,
    val expiresAtEpochSeconds: Long,
) {
    fun encode(): ByteArray = Wire.encodeServerHello(this)
}

data class ClientHello(
    val identityPublicKey: ByteArray,
    val ephemeralPublicKey: ByteArray,
    val clientNonce: ByteArray,
    val signature: ByteArray,
) {
    internal fun unsignedEncoding(serverHello: ServerHello): ByteArray =
        Wire.encodeClientUnsigned(serverHello, this)

    fun encode(serverHello: ServerHello): ByteArray =
        Wire.encodeClientHello(serverHello, this)
}

data class ServerAuthentication(val identityPublicKey: ByteArray, val signature: ByteArray) {
    fun encode(): ByteArray = Wire.encodeServerAuthentication(this)
}

class SessionKeys internal constructor(
    internal val initiatorToResponder: ByteArray,
    internal val responderToInitiator: ByteArray,
    internal val initiatorNoncePrefix: ByteArray,
    internal val responderNoncePrefix: ByteArray,
    internal val transcriptHash: ByteArray,
)

/** Single-use pending state for the responder's invitation. */
class ResponderPending internal constructor(
    val hello: ServerHello,
    internal val ephemeralKeyPair: KeyPair,
    internal val identityKeyPair: KeyPair,
    private var used: Boolean = false,
) {
    /** Checks the allowlist before accepting the signature or deriving a session key. */
    @Synchronized
    @Throws(ProtocolException::class)
    fun accept(client: ClientHello, trustedClientPublicKeys: Collection<ByteArray>, nowEpochSeconds: Long): ResponderResult {
        if (used) throw ProtocolException("Invitation was already used")
        if (nowEpochSeconds >= hello.expiresAtEpochSeconds) throw ProtocolException("Invitation expired")
        if (client.identityPublicKey.size !in 1..RescueCrypto.MAX_PUBLIC_KEY_BYTES ||
            client.ephemeralPublicKey.size !in 1..RescueCrypto.MAX_PUBLIC_KEY_BYTES ||
            client.signature.size !in 1..128 ||
            client.clientNonce.size != RescueCrypto.HASH_LENGTH ||
            hello.invitationNonce.size != RescueCrypto.HASH_LENGTH
        ) {
            throw ProtocolException("Invalid handshake nonce")
        }
        if (trustedClientPublicKeys.none { MessageDigest.isEqual(it, client.identityPublicKey) }) {
            throw ProtocolException("Peer is not enrolled")
        }
        val clientIdentity = decodeKey(client.identityPublicKey)
        val unsigned = client.unsignedEncoding(hello)
        if (!RescueCrypto.verify(clientIdentity, unsigned, client.signature)) {
            throw ProtocolException("Client authentication failed")
        }
        val serverIdentityBytes = identityKeyPair.public.encoded
        val serverSignatureInput = Wire.serverAuthenticationTranscript(hello, client, serverIdentityBytes)
        val auth = ServerAuthentication(serverIdentityBytes, RescueCrypto.sign(identityKeyPair.private, serverSignatureInput))
        val sharedSecret = try {
            RescueCrypto.agree(ephemeralKeyPair.private, decodeKey(client.ephemeralPublicKey))
        } catch (e: GeneralSecurityException) {
            throw ProtocolException("Invalid client key agreement key", e)
        }
        val result = ResponderResult(auth, deriveKeys(sharedSecret, hello, client))
        used = true
        return result
    }
}

data class ResponderResult(val authentication: ServerAuthentication, val sessionKeys: SessionKeys)

/** Client-side one-use state. Server identity is checked against the already enrolled key. */
class InitiatorPending internal constructor(
    val clientHello: ClientHello,
    private val serverHello: ServerHello,
    private val ephemeralKeyPair: KeyPair,
    private val expectedServerIdentity: ByteArray,
    private var used: Boolean = false,
) {
    @Synchronized
    @Throws(ProtocolException::class)
    fun finish(
        authentication: ServerAuthentication,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): SessionKeys {
        if (used) throw ProtocolException("Handshake state was already used")
        if (nowEpochSeconds >= serverHello.expiresAtEpochSeconds) throw ProtocolException("Invitation expired")
        if (!MessageDigest.isEqual(expectedServerIdentity, authentication.identityPublicKey) ||
            !MessageDigest.isEqual(serverHello.identityPublicKey, authentication.identityPublicKey)
        ) {
            throw ProtocolException("Server identity does not match the enrolled peer")
        }
        val serverIdentity = decodeKey(authentication.identityPublicKey)
        val input = Wire.serverAuthenticationTranscript(serverHello, clientHello, authentication.identityPublicKey)
        if (!RescueCrypto.verify(serverIdentity, input, authentication.signature)) {
            throw ProtocolException("Server authentication failed")
        }
        val sharedSecret = try {
            RescueCrypto.agree(ephemeralKeyPair.private, decodeKey(serverHello.ephemeralPublicKey))
        } catch (e: GeneralSecurityException) {
            throw ProtocolException("Invalid server key agreement key", e)
        }
        val keys = deriveKeys(sharedSecret, serverHello, clientHello)
        used = true
        return keys
    }
}

object RescueHandshake {
    /** Creates a 120-second invitation; the caller must make this pending state single-use. */
    fun createInvitation(
        identityKeyPair: KeyPair,
        invitationNonce: ByteArray = ByteArray(RescueCrypto.HASH_LENGTH).also(SecureRandom()::nextBytes),
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
        random: SecureRandom = SecureRandom(),
    ): ResponderPending {
        require(invitationNonce.size == RescueCrypto.HASH_LENGTH) { "Invitation nonce must be 32 bytes" }
        val ephemeral = RescueCrypto.generateEcKeyPair(random)
        val serverNonce = ByteArray(RescueCrypto.HASH_LENGTH).also(random::nextBytes)
        val hello = ServerHello(
            identityPublicKey = identityKeyPair.public.encoded,
            ephemeralPublicKey = ephemeral.public.encoded,
            invitationNonce = invitationNonce.copyOf(),
            serverNonce = serverNonce,
            expiresAtEpochSeconds = nowEpochSeconds + INVITATION_LIFETIME_SECONDS,
        )
        return ResponderPending(hello, ephemeral, identityKeyPair)
    }

    @Throws(ProtocolException::class)
    fun answerInvitation(
        serverHello: ServerHello,
        identityKeyPair: KeyPair,
        expectedServerIdentity: ByteArray,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
        random: SecureRandom = SecureRandom(),
    ): InitiatorPending {
        if (nowEpochSeconds >= serverHello.expiresAtEpochSeconds) throw ProtocolException("Invitation expired")
        if (serverHello.identityPublicKey.size > RescueCrypto.MAX_PUBLIC_KEY_BYTES ||
            serverHello.ephemeralPublicKey.size > RescueCrypto.MAX_PUBLIC_KEY_BYTES ||
            !MessageDigest.isEqual(expectedServerIdentity, serverHello.identityPublicKey)
        ) {
            throw ProtocolException("Invitation is not from the enrolled peer")
        }
        if (serverHello.invitationNonce.size != RescueCrypto.HASH_LENGTH ||
            serverHello.serverNonce.size != RescueCrypto.HASH_LENGTH
        ) {
            throw ProtocolException("Malformed invitation")
        }
        try {
            decodeKey(serverHello.identityPublicKey)
            decodeKey(serverHello.ephemeralPublicKey)
        } catch (e: GeneralSecurityException) {
            throw ProtocolException("Malformed public key in invitation", e)
        }
        val ephemeral = RescueCrypto.generateEcKeyPair(random)
        val clientNonce = ByteArray(RescueCrypto.HASH_LENGTH).also(random::nextBytes)
        val unsigned = ClientHello(identityKeyPair.public.encoded, ephemeral.public.encoded, clientNonce, byteArrayOf())
        val signature = RescueCrypto.sign(identityKeyPair.private, unsigned.unsignedEncoding(serverHello))
        val clientHello = unsigned.copy(signature = signature)
        return InitiatorPending(
            clientHello, serverHello, ephemeral, expectedServerIdentity.copyOf()
        )
    }

    const val INVITATION_LIFETIME_SECONDS = 120L
}

/** AES-GCM record channel with independent direction keys and strict monotonically increasing sequence numbers. */
class SecureChannel internal constructor(
    private val sendKey: ByteArray,
    private val receiveKey: ByteArray,
    private val sendPrefix: ByteArray,
    private val receivePrefix: ByteArray,
    private val sendDirection: Byte,
    private val receiveDirection: Byte,
    private val transcriptHash: ByteArray,
) {
    private var sendSequence = 0L
    private var receiveSequence = 0L

    @Synchronized
    fun seal(plaintext: ByteArray): ByteArray {
        require(plaintext.size <= MAX_RECORD_BYTES) { "Record exceeds maximum size" }
        check(sendSequence >= 0 && sendSequence < Long.MAX_VALUE) { "Send sequence exhausted" }
        val sequence = sendSequence
        val cipherText = crypt(CipherMode.ENCRYPT, sendKey, nonce(sendPrefix, sequence), aad(sendDirection, sequence), plaintext)
        sendSequence++
        return ByteBuffer.allocate(Long.SIZE_BYTES + cipherText.size).putLong(sequence).put(cipherText).array()
    }

    @Synchronized
    @Throws(ProtocolException::class)
    fun open(record: ByteArray): ByteArray {
        if (record.size < Long.SIZE_BYTES + GCM_TAG_BYTES || record.size > Long.SIZE_BYTES + MAX_RECORD_BYTES + GCM_TAG_BYTES) {
            throw ProtocolException("Invalid encrypted record size")
        }
        val buffer = ByteBuffer.wrap(record)
        val sequence = buffer.long
        if (sequence != receiveSequence) throw ProtocolException("Replay or out-of-order record")
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val plaintext = try {
            crypt(CipherMode.DECRYPT, receiveKey, nonce(receivePrefix, sequence), aad(receiveDirection, sequence), encrypted)
        } catch (e: GeneralSecurityException) {
            throw ProtocolException("Record authentication failed", e)
        }
        receiveSequence++
        return plaintext
    }

    private fun aad(direction: Byte, sequence: Long): ByteArray =
        ByteBuffer.allocate(4 + 1 + 8 + transcriptHash.size)
            .putInt(Wire.MAGIC).put(direction).putLong(sequence).put(transcriptHash).array()

    private fun nonce(prefix: ByteArray, sequence: Long): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putLong(sequence).array()

    companion object {
        const val MAX_RECORD_BYTES = 1_048_576
        private const val GCM_TAG_BYTES = 16
    }
}

object SecureChannels {
    fun forInitiator(keys: SessionKeys): SecureChannel = SecureChannel(
        keys.initiatorToResponder.copyOf(), keys.responderToInitiator.copyOf(),
        keys.initiatorNoncePrefix.copyOf(), keys.responderNoncePrefix.copyOf(),
        sendDirection = 0, receiveDirection = 1, keys.transcriptHash.copyOf()
    )

    fun forResponder(keys: SessionKeys): SecureChannel = SecureChannel(
        keys.responderToInitiator.copyOf(), keys.initiatorToResponder.copyOf(),
        keys.responderNoncePrefix.copyOf(), keys.initiatorNoncePrefix.copyOf(),
        sendDirection = 1, receiveDirection = 0, keys.transcriptHash.copyOf()
    )
}

private fun deriveKeys(sharedSecret: ByteArray, hello: ServerHello, client: ClientHello): SessionKeys {
    val transcript = Wire.fullTranscript(hello, client)
    val transcriptHash = RescueCrypto.sha256(transcript)
    val salt = hello.serverNonce + client.clientNonce
    val info = "ZamCan Rescue authenticated session v1".toByteArray(StandardCharsets.UTF_8) + transcriptHash
    val material = RescueCrypto.hkdfSha256(sharedSecret, salt, info, 72)
    return SessionKeys(
        initiatorToResponder = material.copyOfRange(0, 32),
        responderToInitiator = material.copyOfRange(32, 64),
        initiatorNoncePrefix = material.copyOfRange(64, 68),
        responderNoncePrefix = material.copyOfRange(68, 72),
        transcriptHash = transcriptHash,
    )
}

private fun decodeKey(encoded: ByteArray): PublicKey = try {
    RescueCrypto.decodeEcPublicKey(encoded)
} catch (e: GeneralSecurityException) {
    throw ProtocolException("Invalid EC public key", e)
}

private enum class CipherMode { ENCRYPT, DECRYPT }

private fun crypt(
    mode: CipherMode,
    key: ByteArray,
    nonce: ByteArray,
    aad: ByteArray,
    input: ByteArray,
): ByteArray = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").run {
    init(
        if (mode == CipherMode.ENCRYPT) javax.crypto.Cipher.ENCRYPT_MODE else javax.crypto.Cipher.DECRYPT_MODE,
        SecretKeySpec(key, "AES"),
        GCMParameterSpec(128, nonce)
    )
    updateAAD(aad)
    doFinal(input)
}

private object Wire {
    const val MAGIC = 0x5A435231 // "ZCR1"
    private const val VERSION = 1
    private const val TYPE_SERVER_HELLO = 1
    private const val TYPE_CLIENT_HELLO = 2
    private const val TYPE_SERVER_AUTH = 3
    private const val MAX_MESSAGE_BYTES = 8_192
    private const val MAX_SIGNATURE_BYTES = 128
    private const val MAX_NONCE_BYTES = 32

    fun encodeServerHello(value: ServerHello): ByteArray = writer(TYPE_SERVER_HELLO) {
        bytes(value.identityPublicKey, RescueCrypto.MAX_PUBLIC_KEY_BYTES)
        bytes(value.ephemeralPublicKey, RescueCrypto.MAX_PUBLIC_KEY_BYTES)
        bytes(value.invitationNonce, MAX_NONCE_BYTES)
        bytes(value.serverNonce, MAX_NONCE_BYTES)
        writeLong(value.expiresAtEpochSeconds)
    }

    fun decodeServerHello(encoded: ByteArray): ServerHello = reader(encoded, TYPE_SERVER_HELLO) {
        ServerHello(
            bytes(RescueCrypto.MAX_PUBLIC_KEY_BYTES),
            bytes(RescueCrypto.MAX_PUBLIC_KEY_BYTES),
            bytes(MAX_NONCE_BYTES),
            bytes(MAX_NONCE_BYTES),
            readLong()
        ).also {
            if (it.invitationNonce.size != RescueCrypto.HASH_LENGTH || it.serverNonce.size != RescueCrypto.HASH_LENGTH) {
                throw ProtocolException("Invalid hello nonce")
            }
        }
    }

    fun encodeClientUnsigned(serverHello: ServerHello, value: ClientHello): ByteArray =
        serverHello.encode() + writer(TYPE_CLIENT_HELLO) {
            bytes(value.identityPublicKey, RescueCrypto.MAX_PUBLIC_KEY_BYTES)
            bytes(value.ephemeralPublicKey, RescueCrypto.MAX_PUBLIC_KEY_BYTES)
            bytes(value.clientNonce, MAX_NONCE_BYTES)
        }

    fun encodeClientHello(serverHello: ServerHello, value: ClientHello): ByteArray =
        writer(TYPE_CLIENT_HELLO) {
            bytes(serverHello.encode(), MAX_MESSAGE_BYTES / 2)
            bytes(value.identityPublicKey, RescueCrypto.MAX_PUBLIC_KEY_BYTES)
            bytes(value.ephemeralPublicKey, RescueCrypto.MAX_PUBLIC_KEY_BYTES)
            bytes(value.clientNonce, MAX_NONCE_BYTES)
            bytes(value.signature, MAX_SIGNATURE_BYTES)
        }

    fun decodeClientHello(encoded: ByteArray): Pair<ServerHello, ClientHello> = reader(encoded, TYPE_CLIENT_HELLO) {
        val hello = decodeServerHello(bytes(MAX_MESSAGE_BYTES / 2))
        val client = ClientHello(
            bytes(RescueCrypto.MAX_PUBLIC_KEY_BYTES),
            bytes(RescueCrypto.MAX_PUBLIC_KEY_BYTES),
            bytes(MAX_NONCE_BYTES),
            bytes(MAX_SIGNATURE_BYTES),
        )
        if (client.clientNonce.size != RescueCrypto.HASH_LENGTH || client.signature.isEmpty()) {
            throw ProtocolException("Invalid client hello")
        }
        hello to client
    }

    fun encodeServerAuthentication(value: ServerAuthentication): ByteArray = writer(TYPE_SERVER_AUTH) {
        bytes(value.identityPublicKey, RescueCrypto.MAX_PUBLIC_KEY_BYTES)
        bytes(value.signature, MAX_SIGNATURE_BYTES)
    }

    fun decodeServerAuthentication(encoded: ByteArray): ServerAuthentication = reader(encoded, TYPE_SERVER_AUTH) {
        ServerAuthentication(
            bytes(RescueCrypto.MAX_PUBLIC_KEY_BYTES),
            bytes(MAX_SIGNATURE_BYTES)
        ).also { if (it.signature.isEmpty()) throw ProtocolException("Invalid server authentication") }
    }

    fun serverAuthenticationTranscript(hello: ServerHello, client: ClientHello, serverIdentity: ByteArray): ByteArray =
        hello.encode() + encodeClientUnsigned(hello, client) + client.signature + serverIdentity

    fun fullTranscript(hello: ServerHello, client: ClientHello): ByteArray =
        hello.encode() + encodeClientUnsigned(hello, client) + client.signature

    private fun writer(type: Int, body: DataOutputStream.() -> Unit): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use {
            it.writeInt(MAGIC)
            it.writeInt(VERSION)
            it.writeInt(type)
            it.body()
        }
        return output.toByteArray().also { require(it.size <= MAX_MESSAGE_BYTES) { "Protocol message too large" } }
    }

    private fun <T> reader(encoded: ByteArray, expectedType: Int, body: DataInputStream.() -> T): T {
        if (encoded.size !in 12..MAX_MESSAGE_BYTES) throw ProtocolException("Invalid protocol message size")
        try {
            val input = DataInputStream(ByteArrayInputStream(encoded))
            if (input.readInt() != MAGIC || input.readInt() != VERSION || input.readInt() != expectedType) {
                throw ProtocolException("Unsupported protocol message")
            }
            val result = input.body()
            if (input.available() != 0) throw ProtocolException("Trailing protocol data")
            return result
        } catch (e: ProtocolException) {
            throw e
        } catch (e: Exception) {
            throw ProtocolException("Malformed protocol message", e)
        }
    }

    private fun DataOutputStream.bytes(value: ByteArray, maxSize: Int) {
        require(value.size in 1..maxSize) { "Invalid protocol field size" }
        writeInt(value.size)
        write(value)
    }

    private fun DataInputStream.bytes(maxSize: Int): ByteArray {
        val size = readInt()
        if (size !in 1..maxSize || size > available()) throw ProtocolException("Invalid protocol field length")
        return ByteArray(size).also(::readFully)
    }
}

/** Length-delimited handshake transport framing. Caller must impose read timeouts on sockets. */
object HandshakeWireCodec {
    fun encodeServerHello(hello: ServerHello): ByteArray = hello.encode()
    fun decodeServerHello(bytes: ByteArray): ServerHello = Wire.decodeServerHello(bytes)

    fun encodeClientHello(serverHello: ServerHello, hello: ClientHello): ByteArray = hello.encode(serverHello)
    fun decodeClientHello(bytes: ByteArray): Pair<ServerHello, ClientHello> = Wire.decodeClientHello(bytes)

    fun encodeServerAuthentication(authentication: ServerAuthentication): ByteArray = authentication.encode()
    fun decodeServerAuthentication(bytes: ByteArray): ServerAuthentication = Wire.decodeServerAuthentication(bytes)
}

/** Write one bounded handshake message to a stream. */
fun writeHandshakeMessage(output: java.io.OutputStream, message: ByteArray) {
    require(message.size in 12..8_192) { "Invalid handshake message size" }
    DataOutputStream(output).apply { writeInt(message.size); write(message); flush() }
}

/** Read one bounded handshake message from a stream. */
@Throws(ProtocolException::class)
fun readHandshakeMessage(input: java.io.InputStream): ByteArray {
    try {
        val data = DataInputStream(input)
        val size = data.readInt()
        if (size !in 12..8_192) throw ProtocolException("Invalid handshake frame size")
        return ByteArray(size).also(data::readFully)
    } catch (e: ProtocolException) {
        throw e
    } catch (e: Exception) {
        throw ProtocolException("Could not read handshake frame", e)
    }
}
