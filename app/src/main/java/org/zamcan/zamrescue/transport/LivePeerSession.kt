package org.zamcan.zamrescue.transport

import android.content.Context
import org.zamcan.zamrescue.identity.AndroidKeystoreIdentityProvider
import org.zamcan.zamrescue.identity.PeerEnrollmentRepository
import org.zamcan.zamrescue.protocol.AuthenticatedRescueSocket
import org.zamcan.zamrescue.protocol.EncryptedBackupTransfer
import org.zamcan.zamrescue.protocol.RecoveryGrantStore
import java.security.MessageDigest
import java.io.File
import java.io.InputStream
import java.io.Closeable
import java.net.ServerSocket

/**
 * Explicit, foreground-only, one-connection authenticated peer diagnostics.
 * No filesystem, remote-control or unlock authority is exposed.
 */
class LivePeerSession(private val context: Context) : Closeable {
    private val identity = AndroidKeystoreIdentityProvider()
    private val repository = PeerEnrollmentRepository(context, identity)
    @Volatile private var listener: ServerSocket? = null
    private val grants = RecoveryGrantStore()

    /** Blocks on worker thread; closing the session interrupts the accept. */
    fun receiveProbe(port: Int = 42761): String {
        require(port in 1024..65535)
        val keys = repository.trustedPublicKeys()
        require(keys.isNotEmpty()) { "Enroll a peer before listening" }
        val own = requireNotNull(identity.load()) { "Create an identity first" }
        val server = ServerSocket(port)
        listener = server
        try {
            server.soTimeout = 60_000
            server.accept().use { socket ->
                AuthenticatedRescueSocket.accept(socket, own.keyPair, keys).use { session ->
                    val received = session.receive()
                    require(received.contentEquals("ZAMRESCUE_PING_V1".toByteArray())) {
                        "Unsupported peer operation"
                    }
                    session.send("ZAMRESCUE_PONG_V1".toByteArray())
                    return "Authenticated peer probe succeeded"
                }
            }
        } finally {
            listener = null
            server.close()
        }
    }

    fun sendProbe(host: String, port: Int, peerFingerprint: String): String {
        val peer = repository.peers().singleOrNull {
            !it.isRevoked && it.fingerprintHex.equals(peerFingerprint, ignoreCase = true)
        } ?: error("Select an active enrolled peer")
        val own = requireNotNull(identity.load()) { "Create an identity first" }
        AuthenticatedRescueSocket.connect(host, port, own.keyPair, peer.publicKeyEncoded).use { session ->
            session.send("ZAMRESCUE_PING_V1".toByteArray())
            require(session.receive().contentEquals("ZAMRESCUE_PONG_V1".toByteArray())) {
                "Peer response invalid"
            }
        }
        return "Authenticated peer probe succeeded"
    }

    /**
     * Explicit receiver action. Only a verified enrolled peer can connect.
     * The received encrypted archive stays private and is not auto-restored.
     */
    fun receiveArchive(port: Int = 42761, approvalCode: String, senderFingerprint: String): File {
        require(approvalCode.matches(Regex("[0-9]{6}"))) { "Invalid approval code" }
        val sender = repository.peers().singleOrNull {
            !it.isRevoked && it.fingerprintHex.equals(senderFingerprint, ignoreCase = true)
        } ?: error("Select an active enrolled sender")
        val grant = grants.issue(sender.fingerprintHex, RecoveryGrantStore.Operation.RECEIVE_BACKUP, "encrypted-archive", 60_000)
        val keys = listOf(sender.publicKeyEncoded)
        val own = requireNotNull(identity.load()) { "Create an identity first" }
        val server = ServerSocket(port)
        listener = server
        val staged = File.createTempFile("incoming-", ".zrv", context.cacheDir)
        var success = false
        try {
            server.soTimeout = 60_000
            server.accept().use { socket ->
                AuthenticatedRescueSocket.accept(socket, own.keyPair, keys).use { session ->
                    val suppliedCode = session.receive()
                    require(MessageDigest.isEqual(suppliedCode, approvalCode.toByteArray(Charsets.US_ASCII))) {
                        "Receiver approval code mismatch"
                    }
                    require(repository.peers().any { !it.isRevoked && it.fingerprintHex == sender.fingerprintHex }) { "Peer revoked" }
                    require(grants.authorize(grant.id, sender.fingerprintHex, RecoveryGrantStore.Operation.RECEIVE_BACKUP, "encrypted-archive")) { "Authorization expired" }
                    session.send("ZAMRESCUE_APPROVED_V1".toByteArray())
                    require(context.cacheDir.usableSpace > 576L * 1024 * 1024) { "Insufficient storage reserve" }
                    staged.outputStream().use { output ->
                        EncryptedBackupTransfer.receive(session, output)
                    }
                }
            }
            success = true
            return staged
        } finally {
            listener = null
            server.close()
            grants.revoke(grant.id)
            if (!success) staged.delete()
        }
    }

    /** Explicit sender action; only selected, enrolled, non-revoked receiver. */
    fun sendArchive(host: String, port: Int, peerFingerprint: String, input: InputStream, size: Long, approvalCode: String) {
        require(approvalCode.matches(Regex("[0-9]{6}"))) { "Invalid approval code" }
        val peer = repository.peers().singleOrNull {
            !it.isRevoked && it.fingerprintHex.equals(peerFingerprint, ignoreCase = true)
        } ?: error("Select an active enrolled peer")
        val own = requireNotNull(identity.load()) { "Create an identity first" }
        AuthenticatedRescueSocket.connect(host, port, own.keyPair, peer.publicKeyEncoded).use { session ->
            session.send(approvalCode.toByteArray(Charsets.US_ASCII))
            require(session.receive().contentEquals("ZAMRESCUE_APPROVED_V1".toByteArray())) {
                "Receiver did not authorize this transfer"
            }
            EncryptedBackupTransfer.send(session, input, size)
        }
    }

    override fun close() {
        listener?.close()
        listener = null
    }
}
