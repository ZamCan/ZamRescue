package org.zamcan.zamrescue

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.zamcan.zamrescue.identity.AndroidKeystoreIdentityProvider
import org.zamcan.zamrescue.identity.PeerEnrollmentException
import org.zamcan.zamrescue.identity.PeerEnrollmentRepository
import org.zamcan.zamrescue.protocol.RescueCrypto
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PeerIdentityRepositoryTest {
    @Test
    fun keystoreIdentitySurvivesProviderRecreationAndCanSignWithoutExportingPrivateKey() {
        val alias = "org.zamcan.zamrescue.identity.instrumentation.${UUID.randomUUID()}"
        val provider = AndroidKeystoreIdentityProvider(alias)
        try {
            val created = provider.create()
            val loaded = AndroidKeystoreIdentityProvider(alias).load()!!
            val message = "keystore-signing-test".toByteArray()

            assertEquals(created.fingerprintHex, loaded.fingerprintHex)
            assertNull("Android Keystore private material must not be exportable", loaded.keyPair.private.encoded)
            val signature = RescueCrypto.sign(loaded.keyPair.private, message)
            assertTrue(RescueCrypto.verify(loaded.keyPair.public, message, signature))
        } finally {
            deleteKeystoreAlias(alias)
        }
    }

    @Test
    fun peerEnrollmentRequiresMatchingFullFingerprintAndRevocationRemovesActiveKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alias = "org.zamcan.zamrescue.identity.instrumentation.${UUID.randomUUID()}"
        val preferencesName = "zamcan_peer_test_${UUID.randomUUID()}"
        val provider = AndroidKeystoreIdentityProvider(alias)
        val repository = PeerEnrollmentRepository(context, provider, preferencesName)
        val peerKey = RescueCrypto.generateEcKeyPair().public.encoded
        try {
            provider.create()
            val candidate = repository.preview(peerKey)
            assertThrows(PeerEnrollmentException::class.java) {
                repository.enroll(candidate, "0".repeat(64), "Test peer")
            }
            assertTrue(repository.peers().isEmpty())

            val enrolled = repository.enroll(candidate, candidate.fingerprintHex, "Test peer")
            assertEquals(candidate.fingerprintHex, enrolled.fingerprintHex)
            assertEquals(1, repository.trustedPublicKeys().size)
            assertArrayEquals(peerKey, repository.trustedPublicKeys().single())

            repository.revoke(candidate.fingerprintHex)
            assertTrue(repository.peers().single().isRevoked)
            assertFalse(repository.trustedPublicKeys().any { it.contentEquals(peerKey) })
            val reopenedRepository = PeerEnrollmentRepository(context, provider, preferencesName)
            assertTrue(reopenedRepository.peers().single().isRevoked)
            assertTrue(reopenedRepository.trustedPublicKeys().isEmpty())
            assertThrows(PeerEnrollmentException::class.java) {
                repository.enroll(candidate, candidate.fingerprintHex, "Test peer")
            }
        } finally {
            context.getSharedPreferences(preferencesName, android.content.Context.MODE_PRIVATE)
                .edit().clear().commit()
            deleteKeystoreAlias(alias)
        }
    }

    private fun deleteKeystoreAlias(alias: String) {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        } catch (_: Exception) {
            // Cleanup is best-effort if the test failed before creating an entry.
        }
    }
}
