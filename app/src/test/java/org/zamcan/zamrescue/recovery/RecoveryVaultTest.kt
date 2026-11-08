package org.zamcan.zamrescue.recovery

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RecoveryVaultTest {
    @Test fun roundTrip() {
        val data = ByteArray(65_537) { (it % 251).toByte() }
        val encrypted = ByteArrayOutputStream()
        RecoveryVault.encrypt(ByteArrayInputStream(data), encrypted, "correct-horse-vault".toCharArray())
        val restored = ByteArrayOutputStream()
        RecoveryVault.decrypt(ByteArrayInputStream(encrypted.toByteArray()), restored, "correct-horse-vault".toCharArray())
        assertArrayEquals(data, restored.toByteArray())
    }

    @Test fun wrongPasswordDoesNotWritePlaintext() {
        val encrypted = ByteArrayOutputStream()
        RecoveryVault.encrypt(ByteArrayInputStream("private".toByteArray()), encrypted, "correct-horse-vault".toCharArray())
        val restored = ByteArrayOutputStream()
        assertThrows(Exception::class.java) {
            RecoveryVault.decrypt(ByteArrayInputStream(encrypted.toByteArray()), restored, "incorrect-password".toCharArray())
        }
        assertArrayEquals(byteArrayOf(), restored.toByteArray())
    }

    @Test fun tamperingIsRejected() {
        val encrypted = ByteArrayOutputStream()
        RecoveryVault.encrypt(ByteArrayInputStream("private".toByteArray()), encrypted, "correct-horse-vault".toCharArray())
        val bytes = encrypted.toByteArray()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) {
            RecoveryVault.decrypt(ByteArrayInputStream(bytes), ByteArrayOutputStream(), "correct-horse-vault".toCharArray())
        }
    }
    @Test fun truncatedArchiveIsRejectedWithoutWriting() {
        val encrypted = ByteArrayOutputStream()
        RecoveryVault.encrypt(ByteArrayInputStream("valuable".toByteArray()), encrypted, "correct-horse-vault".toCharArray())
        val bytes = encrypted.toByteArray().copyOf(encrypted.size() - 5)
        val destination = ByteArrayOutputStream()
        assertThrows(Exception::class.java) {
            RecoveryVault.decrypt(ByteArrayInputStream(bytes), destination, "correct-horse-vault".toCharArray())
        }
        assertArrayEquals(byteArrayOf(), destination.toByteArray())
    }

    @Test fun encryptingSameFileTwiceProducesDifferentArchives() {
        val first = ByteArrayOutputStream()
        val second = ByteArrayOutputStream()
        val data = "same data".toByteArray()
        RecoveryVault.encrypt(ByteArrayInputStream(data), first, "correct-horse-vault".toCharArray())
        RecoveryVault.encrypt(ByteArrayInputStream(data), second, "correct-horse-vault".toCharArray())
        org.junit.Assert.assertFalse(first.toByteArray().contentEquals(second.toByteArray()))
    }

    @Test fun emptyFileRoundTrips() {
        val encrypted = ByteArrayOutputStream()
        RecoveryVault.encrypt(ByteArrayInputStream(byteArrayOf()), encrypted, "correct-horse-vault".toCharArray())
        val restored = ByteArrayOutputStream()
        RecoveryVault.decrypt(ByteArrayInputStream(encrypted.toByteArray()), restored, "correct-horse-vault".toCharArray())
        assertArrayEquals(byteArrayOf(), restored.toByteArray())
    }

}
