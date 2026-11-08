package org.zamcan.zamrescue.recovery

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * User-selected, password-encrypted file backup format.
 * Does not access private application data or protected Android storage.
 * Format: magic(4), version(1), salt(16), nonce(12), plaintext length(8),
 * AES-256-GCM ciphertext and 128-bit authentication tag.
 */
object RecoveryVault {
    private const val MAGIC = 0x5A525631 // ZRV1
    private const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val MAX_BYTES = 32 * 1024 * 1024
    private const val TAG_BYTES = 16
    private val random = SecureRandom()

    fun encrypt(input: InputStream, output: OutputStream, password: CharArray) {
        require(password.size >= 12) { "Use a password of at least 12 characters" }
        val plaintext = readBounded(input, MAX_BYTES)
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val key = derive(password, salt)
        try {
            val header = ByteArrayOutputStream().also { buffer ->
                DataOutputStream(buffer).apply {
                    writeInt(MAGIC)
                    writeByte(VERSION)
                    write(salt)
                    write(nonce)
                    writeLong(plaintext.size.toLong())
                }
            }.toByteArray()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            val ciphertext = cipher.doFinal(plaintext)
            output.write(header)
            output.write(ciphertext)
            output.flush()
            ciphertext.fill(0)
        } finally {
            plaintext.fill(0)
            key.encoded.fill(0)
        }
    }

    /** Authenticates the entire archive before writing a single restored byte. */
    fun decrypt(input: InputStream, output: OutputStream, password: CharArray) {
        require(password.isNotEmpty())
        val header = ByteArray(41)
        DataInputStream(input).readFully(header)
        val fields = DataInputStream(header.inputStream())
        require(fields.readInt() == MAGIC && fields.readUnsignedByte() == VERSION) { "Unsupported vault format" }
        val salt = ByteArray(16).also(fields::readFully)
        val nonce = ByteArray(12).also(fields::readFully)
        val length = fields.readLong()
        require(length in 0..MAX_BYTES.toLong()) { "Invalid archive length" }
        val ciphertext = readBounded(input, MAX_BYTES + TAG_BYTES)
        require(ciphertext.size.toLong() == length + TAG_BYTES) { "Truncated or oversized archive" }
        val key = derive(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            val plaintext = cipher.doFinal(ciphertext)
            try {
                require(plaintext.size.toLong() == length)
                output.write(plaintext)
                output.flush()
            } finally {
                plaintext.fill(0)
            }
        } finally {
            ciphertext.fill(0)
            key.encoded.fill(0)
        }
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            val material = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try { SecretKeySpec(material, "AES") } finally { material.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            if (count == 0) continue
            require(result.size().toLong() + count <= limit) { "File exceeds vault size limit" }
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }
}
