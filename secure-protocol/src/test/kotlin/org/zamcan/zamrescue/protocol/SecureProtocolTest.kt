package org.zamcan.zamrescue.protocol

import java.security.SecureRandom
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecureProtocolTest {
    private val random = SecureRandom()

    @Test
    fun hkdfMatchesRfc5869Sha256TestVector() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expected = hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865")

        assertContentEquals(expected, RescueCrypto.hkdfSha256(ikm, salt, info, 42))
    }

    @Test
    fun enrolledPeersCompleteMutualHandshakeAndExchangeEncryptedRecords() {
        val alice = RescueCrypto.generateEcKeyPair(random)
        val bob = RescueCrypto.generateEcKeyPair(random)
        val invitation = RescueHandshake.createInvitation(bob, nowEpochSeconds = 1_000, random = random)
        val client = RescueHandshake.answerInvitation(
            invitation.hello, alice, bob.public.encoded, nowEpochSeconds = 1_001, random = random
        )
        val accepted = invitation.accept(client.clientHello, listOf(alice.public.encoded), nowEpochSeconds = 1_001)
        val aliceKeys = client.finish(accepted.authentication, nowEpochSeconds = 1_001)

        assertContentEquals(aliceKeys.initiatorToResponder, accepted.sessionKeys.initiatorToResponder)
        assertContentEquals(aliceKeys.responderToInitiator, accepted.sessionKeys.responderToInitiator)
        assertContentEquals(aliceKeys.transcriptHash, accepted.sessionKeys.transcriptHash)

        val aliceChannel = SecureChannels.forInitiator(aliceKeys)
        val bobChannel = SecureChannels.forResponder(accepted.sessionKeys)
        val payload = "ZamCan Rescue authenticated session".toByteArray()
        val record = aliceChannel.seal(payload)
        assertContentEquals(payload, bobChannel.open(record))

        val reply = "ACK".toByteArray()
        assertContentEquals(reply, aliceChannel.open(bobChannel.seal(reply)))
    }

    @Test
    fun untrustedClientCannotCompleteHandshake() {
        val knownServer = RescueCrypto.generateEcKeyPair(random)
        val untrustedClient = RescueCrypto.generateEcKeyPair(random)
        val invitation = RescueHandshake.createInvitation(knownServer, nowEpochSeconds = 2_000, random = random)
        val client = RescueHandshake.answerInvitation(
            invitation.hello, untrustedClient, knownServer.public.encoded, nowEpochSeconds = 2_001, random = random
        )

        assertFailsWith<ProtocolException> {
            invitation.accept(client.clientHello, emptyList(), nowEpochSeconds = 2_001)
        }

        val enrolledClient = RescueCrypto.generateEcKeyPair(random)
        val validClient = RescueHandshake.answerInvitation(
            invitation.hello, enrolledClient, knownServer.public.encoded, nowEpochSeconds = 2_001, random = random
        )
        invitation.accept(validClient.clientHello, listOf(enrolledClient.public.encoded), nowEpochSeconds = 2_001)
    }

    @Test
    fun clientSignatureBindsHandshakeTranscript() {
        val clientIdentity = RescueCrypto.generateEcKeyPair(random)
        val serverIdentity = RescueCrypto.generateEcKeyPair(random)
        val invitation = RescueHandshake.createInvitation(serverIdentity, nowEpochSeconds = 3_000, random = random)
        val client = RescueHandshake.answerInvitation(
            invitation.hello, clientIdentity, serverIdentity.public.encoded, nowEpochSeconds = 3_001, random = random
        )
        val tampered = client.clientHello.copy(clientNonce = client.clientHello.clientNonce.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })

        assertFailsWith<ProtocolException> {
            invitation.accept(tampered, listOf(clientIdentity.public.encoded), nowEpochSeconds = 3_001)
        }
    }

    @Test
    fun invitationExpiresAndIsSingleUse() {
        val clientIdentity = RescueCrypto.generateEcKeyPair(random)
        val serverIdentity = RescueCrypto.generateEcKeyPair(random)
        val expired = RescueHandshake.createInvitation(serverIdentity, nowEpochSeconds = 4_000, random = random)
        val expiredClient = RescueHandshake.answerInvitation(
            expired.hello, clientIdentity, serverIdentity.public.encoded, nowEpochSeconds = 4_001, random = random
        )
        assertFailsWith<ProtocolException> {
            expired.accept(expiredClient.clientHello, listOf(clientIdentity.public.encoded), nowEpochSeconds = 4_121)
        }

        val oneUse = RescueHandshake.createInvitation(serverIdentity, nowEpochSeconds = 5_000, random = random)
        val validClient = RescueHandshake.answerInvitation(
            oneUse.hello, clientIdentity, serverIdentity.public.encoded, nowEpochSeconds = 5_001, random = random
        )
        oneUse.accept(validClient.clientHello, listOf(clientIdentity.public.encoded), nowEpochSeconds = 5_001)
        assertFailsWith<ProtocolException> {
            oneUse.accept(validClient.clientHello, listOf(clientIdentity.public.encoded), nowEpochSeconds = 5_001)
        }
    }

    @Test
    fun encryptedRecordsRejectTamperingReplayAndOutOfOrderData() {
        val a = RescueCrypto.generateEcKeyPair(random)
        val b = RescueCrypto.generateEcKeyPair(random)
        val invitation = RescueHandshake.createInvitation(b, nowEpochSeconds = 6_000, random = random)
        val pending = RescueHandshake.answerInvitation(invitation.hello, a, b.public.encoded, 6_001, random)
        val responder = invitation.accept(pending.clientHello, listOf(a.public.encoded), 6_001)
        val initiator = SecureChannels.forInitiator(pending.finish(responder.authentication, nowEpochSeconds = 6_001))
        val receiver = SecureChannels.forResponder(responder.sessionKeys)

        val first = initiator.seal(byteArrayOf(1, 2, 3))
        val second = initiator.seal(byteArrayOf(4, 5, 6))
        assertFailsWith<ProtocolException> { receiver.open(second) }

        val changed = first.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertFailsWith<ProtocolException> { receiver.open(changed) }
        assertContentEquals(byteArrayOf(1, 2, 3), receiver.open(first))
        assertFailsWith<ProtocolException> { receiver.open(first) }
        assertContentEquals(byteArrayOf(4, 5, 6), receiver.open(second))
    }

    @Test
    fun wireMessagesRoundTripAndRejectTrailingData() {
        val clientIdentity = RescueCrypto.generateEcKeyPair(random)
        val serverIdentity = RescueCrypto.generateEcKeyPair(random)
        val invitation = RescueHandshake.createInvitation(serverIdentity, nowEpochSeconds = 7_000, random = random)
        val decodedServer = HandshakeWireCodec.decodeServerHello(HandshakeWireCodec.encodeServerHello(invitation.hello))
        assertContentEquals(invitation.hello.identityPublicKey, decodedServer.identityPublicKey)
        assertContentEquals(invitation.hello.invitationNonce, decodedServer.invitationNonce)

        val pending = RescueHandshake.answerInvitation(
            decodedServer, clientIdentity, serverIdentity.public.encoded, nowEpochSeconds = 7_001, random = random
        )
        val decodedClient = HandshakeWireCodec.decodeClientHello(
            HandshakeWireCodec.encodeClientHello(decodedServer, pending.clientHello)
        )
        assertContentEquals(pending.clientHello.signature, decodedClient.second.signature)
        assertEquals(decodedServer.expiresAtEpochSeconds, decodedClient.first.expiresAtEpochSeconds)

        val auth = invitation.accept(pending.clientHello, listOf(clientIdentity.public.encoded), 7_001).authentication
        val encodedAuth = HandshakeWireCodec.encodeServerAuthentication(auth)
        assertContentEquals(auth.signature, HandshakeWireCodec.decodeServerAuthentication(encodedAuth).signature)
        assertFailsWith<ProtocolException> { HandshakeWireCodec.decodeServerHello(encodedAuth + byteArrayOf(0)) }
    }

    @Test
    fun samePlaintextProducesDifferentCiphertextForDifferentSessions() {
        val first = sessionChannels()
        val second = sessionChannels()
        val message = "sensitive".toByteArray()
        val firstRecord = first.first.seal(message)
        val secondRecord = second.first.seal(message)
        assertNotEquals(firstRecord.toList(), secondRecord.toList())
        assertTrue(first.second.open(firstRecord).contentEquals(message))
        assertTrue(second.second.open(secondRecord).contentEquals(message))
    }

    @Test
    fun publicKeyDecoderRejectsNonP256Curves() {
        val p384 = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp384r1"), random)
            generateKeyPair()
        }
        assertFailsWith<GeneralSecurityException> { RescueCrypto.decodeEcPublicKey(p384.public.encoded) }
    }

    private fun sessionChannels(): Pair<SecureChannel, SecureChannel> {
        val client = RescueCrypto.generateEcKeyPair(random)
        val server = RescueCrypto.generateEcKeyPair(random)
        val invitation = RescueHandshake.createInvitation(server, random = random)
        val pending = RescueHandshake.answerInvitation(invitation.hello, client, server.public.encoded, random = random)
        val accepted = invitation.accept(pending.clientHello, listOf(client.public.encoded), System.currentTimeMillis() / 1000)
        return SecureChannels.forInitiator(pending.finish(accepted.authentication)) to
            SecureChannels.forResponder(accepted.sessionKeys)
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
