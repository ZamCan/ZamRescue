package org.zamcan.zamrescue.protocol

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Length-bounded backup transfer over an already authenticated SecureChannel socket.
 * No path, command or remote filesystem operation is accepted. Caller must obtain
 * local user consent, validate the peer and publish the temporary output only on success.
 */
object EncryptedBackupTransfer {
    private const val START: Byte = 1
    private const val DATA: Byte = 2
    private const val END: Byte = 3
    private const val ACK: Byte = 4
    private const val CHUNK = 64 * 1024
    private const val MAX_BYTES = 4L * 1024 * 1024 * 1024

    fun send(session: AuthenticatedRescueSocket, source: InputStream, length: Long) {
        require(length in 0..MAX_BYTES) { "Unsupported transfer length" }
        session.send(ByteBuffer.allocate(9).put(START).putLong(length).array())
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK)
        var remaining = length
        while (remaining > 0) {
            val n = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            require(n > 0) { "Source ended before advertised length" }
            digest.update(buffer, 0, n)
            session.send(byteArrayOf(DATA) + buffer.copyOf(n))
            remaining -= n
        }
        session.send(byteArrayOf(END) + digest.digest())
        require(session.receive().contentEquals(byteArrayOf(ACK))) { "Receiver did not confirm" }
        buffer.fill(0)
    }

    /** Writes untrusted bytes to caller-owned staging output; never directly to a published file. */
    fun receive(session: AuthenticatedRescueSocket, staging: OutputStream): Long {
        val first = session.receive()
        require(first.size == 9 && first[0] == START) { "Invalid transfer start" }
        val length = ByteBuffer.wrap(first, 1, 8).long
        require(length in 0..MAX_BYTES) { "Unsupported transfer length" }
        val digest = MessageDigest.getInstance("SHA-256")
        var remaining = length
        while (remaining > 0) {
            val record = session.receive()
            require(record.isNotEmpty() && record[0] == DATA &&
                record.size in 2..(CHUNK + 1) &&
                record.size - 1 <= remaining) { "Invalid data record" }
            digest.update(record, 1, record.size - 1)
            staging.write(record, 1, record.size - 1)
            remaining -= record.size - 1
        }
        val end = session.receive()
        require(end.size == 33 && end[0] == END &&
            MessageDigest.isEqual(digest.digest(), end.copyOfRange(1, 33))) {
            "Transfer checksum mismatch"
        }
        staging.flush()
        session.send(byteArrayOf(ACK))
        return length
    }
}
