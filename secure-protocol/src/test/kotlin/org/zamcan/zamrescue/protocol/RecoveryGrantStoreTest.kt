package org.zamcan.zamrescue.protocol

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.Duration

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
        val base = Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC)
        val advancing = object : Clock() {
            var now = 1000L
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(now)
        }
        val store = RecoveryGrantStore(advancing)
        val grant = store.issue(peer, RecoveryGrantStore.Operation.VERIFY_BACKUP, "file", 1)
        advancing.now = 1001L
        assertFalse(store.authorize(grant.id, peer, grant.operation, "file"))
    }

    @Test fun revocationDenies() {
        val store = RecoveryGrantStore()
        val grant = store.issue(peer, RecoveryGrantStore.Operation.SEND_BACKUP, "file", 1000, false)
        assertTrue(store.authorize(grant.id, peer, grant.operation, "file"))
        store.revokePeer(peer)
        assertFalse(store.authorize(grant.id, peer, grant.operation, "file"))
    }
}
