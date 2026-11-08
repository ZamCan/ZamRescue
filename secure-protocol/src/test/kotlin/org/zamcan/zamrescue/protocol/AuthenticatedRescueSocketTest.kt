package org.zamcan.zamrescue.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AuthenticatedRescueSocketTest {
    @Test
    fun enrolledPeersExchangeEncryptedMessages() {
        val serverIdentity = RescueCrypto.generateEcKeyPair()
        val clientIdentity = RescueCrypto.generateEcKeyPair()
        ServerSocket(0).use { listener ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val server = executor.submit<Boolean> {
                    AuthenticatedRescueSocket.accept(
                        listener.accept(), serverIdentity, listOf(clientIdentity.public.encoded)
                    ).use { connection ->
                        assertArrayEquals("ping".toByteArray(), connection.receive())
                        connection.send("pong".toByteArray())
                        true
                    }
                }
                AuthenticatedRescueSocket.connect(
                    "127.0.0.1", listener.localPort, clientIdentity, serverIdentity.public.encoded
                ).use { connection ->
                    connection.send("ping".toByteArray())
                    assertArrayEquals("pong".toByteArray(), connection.receive())
                }
                assertTrue(server.get(20, TimeUnit.SECONDS))
            } finally {
                listener.close()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun untrustedClientCannotEstablishSession() {
        val serverIdentity = RescueCrypto.generateEcKeyPair()
        val enrolled = RescueCrypto.generateEcKeyPair()
        val stranger = RescueCrypto.generateEcKeyPair()
        ServerSocket(0).use { listener ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val server = executor.submit<Boolean> {
                    try {
                        AuthenticatedRescueSocket.accept(
                            listener.accept(), serverIdentity, listOf(enrolled.public.encoded)
                        ).close()
                        false
                    } catch (_: ProtocolException) {
                        true
                    }
                }
                try {
                    AuthenticatedRescueSocket.connect(
                        "127.0.0.1", listener.localPort, stranger, serverIdentity.public.encoded
                    ).close()
                } catch (_: Exception) {
                    // The responder rejects the untrusted identity and closes the socket.
                }
                assertTrue(server.get(20, TimeUnit.SECONDS))
            } finally {
                listener.close()
                executor.shutdownNow()
            }
        }
    }
}
