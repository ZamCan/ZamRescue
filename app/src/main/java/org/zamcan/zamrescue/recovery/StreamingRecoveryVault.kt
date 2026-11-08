package org.zamcan.zamrescue.recovery

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming authenticated archive for large user-selected files.
 * V2 frames: header + [length, encrypted chunk]* + authenticated end frame.
 * The restore caller MUST write into a temporary file and publish it only on success.
 */
object StreamingRecoveryVault {
    private const val MAGIC = 0x5A525632 // ZRV2
    private const val VERSION = 2
    private const val ITERATIONS = 210_000
    private const val CHUNK = 256 * 1024
    private const val TAG = 16
    private val random = SecureRandom()

    fun encrypt(input: InputStream, output: OutputStream, password: CharArray) {
        require(password.size >= 12)
        val salt = ByteArray(16).also(random::nextBytes)
        val noncePrefix = ByteArray(8).also(random::nextBytes)
        val key = derive(password, salt)
        val stream = DataOutputStream(output)
        val header = java.io.ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).apply {
                writeInt(MAGIC); writeByte(VERSION); write(salt); write(noncePrefix)
            }
        }.toByteArray()
        stream.write(header)
        val buffer = ByteArray(CHUNK)
        var sequence = 0
        try {
            while (true) {
                var count = 0
                while (count < CHUNK) {
                    val n = input.read(buffer, count, CHUNK - count)
                    if (n < 0) break
                    if (n == 0) continue
                    count += n
                }
                val last = count == 0
                val nonce = nonce(noncePrefix, sequence)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD(header)
                cipher.updateAAD(byteArrayOf(if (last) 1 else 0))
                val encrypted = cipher.doFinal(buffer, 0, count)
                stream.writeInt(encrypted.size)
                stream.write(encrypted)
                sequence++
                require(sequence > 0) { "Too many chunks" }
                if (last) break
            }
            stream.flush()
        } finally {
            buffer.fill(0)
            key.fill(0)
        }
    }

    fun decrypt(input: InputStream, output: OutputStream, password: CharArray) {
        val stream = DataInputStream(input)
        val header = ByteArray(29)
        stream.readFully(header)
        val fields = DataInputStream(header.inputStream())
        require(fields.readInt() == MAGIC && fields.readUnsignedByte() == VERSION) { "Unsupported archive" }
        val salt = ByteArray(16).also(fields::readFully)
        val prefix = ByteArray(8).also(fields::readFully)
        val key = derive(password, salt)
        var sequence = 0
        try {
            while (true) {
                val size = stream.readInt()
                require(size in TAG..(CHUNK + TAG)) { "Invalid frame length" }
                val encrypted = ByteArray(size)
                stream.readFully(encrypted)
                val last = size == TAG
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce(prefix, sequence)))
                cipher.updateAAD(header)
                cipher.updateAAD(byteArrayOf(if (last) 1 else 0))
                val clear = cipher.doFinal(encrypted)
                try {
                    if (last) {
                        require(clear.isEmpty())
                        require(stream.read() == -1) { "Trailing archive bytes" }
                        output.flush()
                        return
                    }
                    output.write(clear)
                } finally {
                    clear.fill(0)
                    encrypted.fill(0)
                }
                sequence++
                require(sequence > 0) { "Too many chunks" }
            }
        } catch (error: EOFException) {
            throw IllegalArgumentException("Truncated archive", error)
        } finally {
            key.fill(0)
        }
    }

    private fun nonce(prefix: ByteArray, sequence: Int) = java.nio.ByteBuffer.allocate(12)
        .put(prefix).putInt(sequence).array()

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }
}
