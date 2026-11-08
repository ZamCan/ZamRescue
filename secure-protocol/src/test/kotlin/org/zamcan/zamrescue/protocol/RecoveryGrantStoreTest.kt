package org.zamcan.zamrescue.protocol

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RecoveryGrantStoreTest {
    private val peer = "a".repeat(64)
    private val other = "b".repeat(64)
    @Test fun grantIsScopedAndSingleUse() {
        val store = RecoveryGrantStore()
        val grant = store.issue(peer, RecoveryGrantStore.Operation.SEND_BACKUP, "photos", 60_000)
        assertFalse(store.authorize(grant.id, other, grant.operation, "photos"))
        assertFalse(store.authorize(grant.id, peer, grant.operation, "documents"))
        assertFalse(store.authorize(grant.id, peer, RecoveryGrantStore.Operation.RECEIVE_BACKUP, "photos"))
        assertTrue(store.authorize(grant.id, peer, grant.operation, "photos"))
        assertFalse(store.authorize(grant.id, peer, grant.operation, "photos"))
    }

    @Test fun expiredGrantsDenied() {
        val issue = RecoveryGrantStore(Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC))
        val grant = issue.issue(peer, RecoveryGrantStore.Operation.VERIFY_BACKUP, "file", 1)
        val verifier = RecoveryGrantStore(Clock.fixed(Instant.ofEpochMilli(1002), ZoneOffset.UTC))
        // A grant is never valid in another store/process.
        assertFalse(verifier.authorize(grant.id, peer, grant.operation, "file"))
    }

    @Test fun revocationDenies() {
        val store = RecoveryGrantStore()
        val grant = store.issue(peer, RecoveryGrantStore.Operation.SEND_BACKUP, "file", 1000, false)
        assertTrue(store.authorize(grant.id, peer, grant.operation, "file"))
        store.revokePeer(peer)
        assertFalse(store.authorize(grant.id, peer, grant.operation, "file"))
    }
}
