package org.zamcan.zamrescue.recovery

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class StreamingRecoveryVaultTest {
    private val password = "a-strong-recovery-passphrase".toCharArray()

    private fun archive(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        StreamingRecoveryVault.encrypt(ByteArrayInputStream(data), out, password)
        return out.toByteArray()
    }

    private fun restore(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        StreamingRecoveryVault.decrypt(ByteArrayInputStream(bytes), out, password)
        return out.toByteArray()
    }

    @Test fun emptyFile() = assertArrayEquals(byteArrayOf(), restore(archive(byteArrayOf())))

    @Test fun multiChunkFile() {
        val data = ByteArray(900_000) { (it % 239).toByte() }
        assertArrayEquals(data, restore(archive(data)))
    }

    @Test fun exactChunkBoundary() {
        val data = ByteArray(256 * 1024) { (it % 241).toByte() }
        assertArrayEquals(data, restore(archive(data)))
    }

    @Test fun tamperDetected() {
        val bytes = archive(ByteArray(500_000) { 3 })
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { restore(bytes) }
    }

    @Test fun missingEndFrameRejected() {
        val bytes = archive(ByteArray(300_000) { 7 })
        assertThrows(Exception::class.java) { restore(bytes.copyOf(bytes.size - 20)) }
    }

    @Test fun trailingBytesRejected() {
        val bytes = archive("hello".toByteArray()) + byteArrayOf(1)
        assertThrows(Exception::class.java) { restore(bytes) }
    }

    @Test fun incorrectPasswordRejected() {
        val bytes = archive("private".toByteArray())
        assertThrows(Exception::class.java) {
            StreamingRecoveryVault.decrypt(ByteArrayInputStream(bytes), ByteArrayOutputStream(), "wrong-password".toCharArray())
        }
    }
}
