package org.zamcan.zamrescue.protocol

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.security.KeyPair
import java.security.MessageDigest

/**
 * A foreground, authenticated TCP session. Never exposes an unauthenticated socket as a
 * trusted peer. The caller must obtain the expected identity from explicit enrollment.
 * No remote commands, backup, or filesystem access are granted by this transport.
 */
class AuthenticatedRescueSocket private constructor(
    private val socket: Socket,
    private val channel: SecureChannel,
) : Closeable {
    @Synchronized
    fun send(plaintext: ByteArray) {
        val record = channel.seal(plaintext)
        val output = DataOutputStream(socket.getOutputStream())
        output.writeInt(record.size)
        output.write(record)
        output.flush()
    }

    @Synchronized
    fun receive(): ByteArray {
        val input = DataInputStream(socket.getInputStream())
        val length = input.readInt()
        if (length !in 24..(SecureChannel.MAX_RECORD_BYTES + 24)) {
            throw ProtocolException("Invalid encrypted frame length")
        }
        val record = ByteArray(length)
        input.readFully(record)
        return channel.open(record)
    }

    override fun close() = socket.close()

    companion object {
        private const val TIMEOUT_MS = 15_000

        /**
         * Connect to a previously enrolled responder. An address discovered through NSD is
         * only a routing hint: the responder must prove possession of the pinned identity key.
         */
        fun connect(
            host: String,
            port: Int,
            localIdentity: KeyPair,
            expectedResponderPublicKey: ByteArray,
        ): AuthenticatedRescueSocket {
            require(port in 1..65535)
            val socket = Socket()
            try {
                socket.connect(java.net.InetSocketAddress(host, port), TIMEOUT_MS)
                socket.soTimeout = TIMEOUT_MS
                val serverHello = HandshakeWireCodec.decodeServerHello(readHandshakeMessage(socket.getInputStream()))
                val pending = RescueHandshake.answerInvitation(
                    serverHello, localIdentity, expectedResponderPublicKey
                )
                writeHandshakeMessage(
                    socket.getOutputStream(),
                    HandshakeWireCodec.encodeClientHello(serverHello, pending.clientHello)
                )
                val auth = HandshakeWireCodec.decodeServerAuthentication(readHandshakeMessage(socket.getInputStream()))
                val keys = pending.finish(auth)
                return AuthenticatedRescueSocket(socket, SecureChannels.forInitiator(keys))
            } catch (e: Exception) {
                socket.close()
                throw e
            }
        }

        /**
         * Authenticate one accepted socket using a fresh single-use invitation. Caller owns
         * the listening ServerSocket and must explicitly authorize each client identity.
         */
        fun accept(
            socket: Socket,
            localIdentity: KeyPair,
            enrolledClientPublicKeys: Collection<ByteArray>,
        ): AuthenticatedRescueSocket {
            try {
                socket.soTimeout = TIMEOUT_MS
                val pending = RescueHandshake.createInvitation(localIdentity)
                writeHandshakeMessage(socket.getOutputStream(), HandshakeWireCodec.encodeServerHello(pending.hello))
                val (echoedHello, clientHello) = HandshakeWireCodec.decodeClientHello(
                    readHandshakeMessage(socket.getInputStream())
                )
                if (!MessageDigest.isEqual(pending.hello.encode(), echoedHello.encode())) {
                    throw ProtocolException("Invitation transcript mismatch")
                }
                val result = pending.accept(
                    clientHello, enrolledClientPublicKeys, System.currentTimeMillis() / 1000
                )
                writeHandshakeMessage(
                    socket.getOutputStream(),
                    HandshakeWireCodec.encodeServerAuthentication(result.authentication)
                )
                return AuthenticatedRescueSocket(socket, SecureChannels.forResponder(result.sessionKeys))
            } catch (e: Exception) {
                socket.close()
                throw e
            }
        }
    }
}
