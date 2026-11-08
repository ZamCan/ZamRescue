package org.zamcan.zamrescue.protocol

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock

/** In-memory, fail-closed authorization independent of peer authentication. */
class RecoveryGrantStore(private val clock: Clock = Clock.systemUTC()) {
    enum class Operation { SEND_BACKUP, RECEIVE_BACKUP, VERIFY_BACKUP }
    data class Grant(
        val id: String,
        val peerFingerprint: String,
        val operation: Operation,
        val datasetId: String,
        val expiresAtEpochMs: Long,
        val singleUse: Boolean
    )

    private val random = SecureRandom()
    private val active = mutableMapOf<String, Grant>()

    @Synchronized
    fun issue(
        peerFingerprint: String,
        operation: Operation,
        datasetId: String,
        lifetimeMs: Long,
        singleUse: Boolean = true
    ): Grant {
        require(peerFingerprint.matches(Regex("[a-fA-F0-9]{64}")))
        require(datasetId.isNotBlank() && datasetId.length <= 256)
        require(lifetimeMs in 1..(15 * 60 * 1000L))
        val token = ByteArray(32).also(random::nextBytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val grant = Grant(token, peerFingerprint.lowercase(), operation, datasetId,
            Math.addExact(clock.millis(), lifetimeMs), singleUse)
        active[token] = grant
        return grant
    }

    @Synchronized
    fun authorize(
        id: String, peerFingerprint: String, operation: Operation, datasetId: String
    ): Boolean {
        val grant = active[id] ?: return false
        if (clock.millis() >= grant.expiresAtEpochMs) {
            active.remove(id)
            return false
        }
        val peerMatches = MessageDigest.isEqual(
            grant.peerFingerprint.toByteArray(Charsets.US_ASCII),
            peerFingerprint.lowercase().toByteArray(Charsets.US_ASCII)
        )
        if (!peerMatches || grant.operation != operation || grant.datasetId != datasetId) return false
        if (grant.singleUse) active.remove(id)
        return true
    }

    @Synchronized
    fun revoke(id: String): Boolean = active.remove(id) != null

    @Synchronized
    fun revokePeer(peerFingerprint: String) {
        active.entries.removeAll { it.value.peerFingerprint == peerFingerprint.lowercase() }
    }

    @Synchronized
    fun revokeAll() = active.clear()
}
