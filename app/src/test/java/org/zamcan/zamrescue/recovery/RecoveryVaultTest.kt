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
}
