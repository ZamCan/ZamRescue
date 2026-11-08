package org.zamcan.zamrescue.protocol

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EncryptedBackupTransferTest {
    @Test fun authenticatedPeersTransferExactArchiveBytes() {
        val receiverIdentity = RescueCrypto.generateEcKeyPair()
        val senderIdentity = RescueCrypto.generateEcKeyPair()
        val archive = ByteArray(350_000) { (it % 251).toByte() }
        ServerSocket(0).use { listener ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val received = executor.submit<ByteArray> {
                    AuthenticatedRescueSocket.accept(
                        listener.accept(), receiverIdentity, listOf(senderIdentity.public.encoded)
                    ).use { session ->
                        ByteArrayOutputStream().also { out ->
                            assertEquals(archive.size.toLong(), EncryptedBackupTransfer.receive(session, out))
                        }.toByteArray()
                    }
                }
                AuthenticatedRescueSocket.connect(
                    "127.0.0.1", listener.localPort, senderIdentity, receiverIdentity.public.encoded
                ).use { session ->
                    EncryptedBackupTransfer.send(session, ByteArrayInputStream(archive), archive.size.toLong())
                }
                assertArrayEquals(archive, received.get(20, TimeUnit.SECONDS))
            } finally {
                listener.close()
                executor.shutdownNow()
            }
        }
    }

    @Test fun senderRejectsPrematureEndOfSource() {
        val receiverIdentity = RescueCrypto.generateEcKeyPair()
        val senderIdentity = RescueCrypto.generateEcKeyPair()
        ServerSocket(0).use { listener ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val receiver = executor.submit<Boolean> {
                    try {
                        AuthenticatedRescueSocket.accept(
                            listener.accept(), receiverIdentity, listOf(senderIdentity.public.encoded)
                        ).use { session ->
                            EncryptedBackupTransfer.receive(session, ByteArrayOutputStream())
                        }
                        false
                    } catch (_: Exception) { true }
                }
                AuthenticatedRescueSocket.connect(
                    "127.0.0.1", listener.localPort, senderIdentity, receiverIdentity.public.encoded
                ).use { session ->
                    assertThrows(IllegalArgumentException::class.java) {
                        EncryptedBackupTransfer.send(session, ByteArrayInputStream(byteArrayOf(1)), 10)
                    }
                }
                assertTrue(receiver.get(20, TimeUnit.SECONDS))
            } finally {
                listener.close()
                executor.shutdownNow()
            }
        }
    }
}
