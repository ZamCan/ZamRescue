package org.zamcan.zamrescue.identity

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONException
import org.json.JSONObject
import org.zamcan.zamrescue.protocol.PeerFingerprint
import org.zamcan.zamrescue.protocol.RescueCrypto
import java.security.MessageDigest
import java.util.Locale

enum class PeerEnrollmentError { FINGERPRINT_MISMATCH, OTHER }

class PeerEnrollmentException(
    message: String,
    cause: Throwable? = null,
    val error: PeerEnrollmentError = PeerEnrollmentError.OTHER,
) : Exception(message, cause)

data class PeerCandidate(val publicKeyEncoded: ByteArray, val fingerprintHex: String)

data class TrustedPeerRecord(
    val fingerprintHex: String,
    val displayName: String,
    val publicKeyEncoded: ByteArray,
    val enrolledAtEpochMillis: Long,
    val revokedAtEpochMillis: Long?,
) {
    val isRevoked: Boolean get() = revokedAtEpochMillis != null
}

/**
 * Stores only peer public keys and user-assigned labels in app-private preferences. The signing
 * private key remains in AndroidKeyStore. Enrollment requires the user to re-enter the full
 * fingerprint they compared out of band on both phones.
 */
class PeerEnrollmentRepository(
    context: Context,
    private val identityProvider: AndroidKeystoreIdentityProvider,
    preferencesName: String = PREFERENCES_NAME,
) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    @Throws(PeerEnrollmentException::class)
    fun preview(publicKeyEncoded: ByteArray): PeerCandidate {
        val copy = publicKeyEncoded.copyOf()
        try {
            RescueCrypto.decodeEcPublicKey(copy)
        } catch (e: Exception) {
            throw PeerEnrollmentException("The peer key is not a valid P-256 public key", e)
        }
        return PeerCandidate(copy, PeerFingerprint.of(copy).hex)
    }

    @Throws(PeerEnrollmentException::class)
    fun enroll(
        candidate: PeerCandidate,
        expectedFingerprint: String,
        displayName: String,
    ): TrustedPeerRecord = synchronized(STORE_LOCK) {
        val identity = try {
            identityProvider.load()
        } catch (e: IdentityKeyException) {
            throw PeerEnrollmentException("Could not load this device's identity", e)
        } ?: throw PeerEnrollmentException("Create this device's identity before enrolling a peer")
        val checkedCandidate = preview(candidate.publicKeyEncoded)
        if (!constantTimeTextEquals(checkedCandidate.fingerprintHex, normalizeFingerprint(candidate.fingerprintHex))) {
            throw PeerEnrollmentException("The peer key changed after its fingerprint was displayed")
        }
        if (constantTimeTextEquals(checkedCandidate.fingerprintHex, identity.fingerprintHex)) {
            throw PeerEnrollmentException("This device's own identity cannot be enrolled as a peer")
        }
        val normalizedExpected = normalizeFingerprint(expectedFingerprint)
        if (!FINGERPRINT_REGEX.matches(normalizedExpected) ||
            !constantTimeTextEquals(checkedCandidate.fingerprintHex, normalizedExpected)
        ) {
            throw PeerEnrollmentException(
                "The confirmed fingerprint does not match the peer key",
                error = PeerEnrollmentError.FINGERPRINT_MISMATCH,
            )
        }
        val cleanName = sanitizeDisplayName(displayName)
        if (cleanName.isEmpty()) throw PeerEnrollmentException("Enter a peer name")

        val document = readDocument()
        if (document.has(checkedCandidate.fingerprintHex)) {
            throw PeerEnrollmentException("This peer is already recorded; revoked identities cannot be silently restored")
        }
        val enrolledAt = System.currentTimeMillis()
        document.put(
            checkedCandidate.fingerprintHex,
            JSONObject()
                .put("fingerprint", checkedCandidate.fingerprintHex)
                .put("displayName", cleanName)
                .put("publicKey", Base64.encodeToString(checkedCandidate.publicKeyEncoded, Base64.NO_WRAP))
                .put("enrolledAt", enrolledAt)
                .put("revokedAt", JSONObject.NULL)
        )
        persist(document)
        TrustedPeerRecord(
            checkedCandidate.fingerprintHex,
            cleanName,
            checkedCandidate.publicKeyEncoded.copyOf(),
            enrolledAt,
            null,
        )
    }

    @Throws(PeerEnrollmentException::class)
    fun peers(): List<TrustedPeerRecord> = synchronized(STORE_LOCK) {
        readRecords().sortedBy { it.displayName.lowercase(Locale.ROOT) }
    }

    /** The responder protocol receives only currently enrolled, non-revoked identities. */
    @Throws(PeerEnrollmentException::class)
    fun trustedPublicKeys(): List<ByteArray> = synchronized(STORE_LOCK) {
        readRecords()
            .asSequence()
            .filterNot(TrustedPeerRecord::isRevoked)
            .map { it.publicKeyEncoded.copyOf() }
            .toList()
    }

    @Throws(PeerEnrollmentException::class)
    fun revoke(fingerprintHex: String) {
        synchronized(STORE_LOCK) {
            val fingerprint = normalizeFingerprint(fingerprintHex)
            if (!FINGERPRINT_REGEX.matches(fingerprint)) throw PeerEnrollmentException("Invalid peer fingerprint")
            val document = readDocument()
            val record = document.optJSONObject(fingerprint)
                ?: throw PeerEnrollmentException("Peer was not found")
            if (record.isNull("revokedAt")) {
                record.put("revokedAt", System.currentTimeMillis())
                persist(document)
            }
        }
    }

    private fun readRecords(): List<TrustedPeerRecord> {
        val document = readDocument()
        val result = ArrayList<TrustedPeerRecord>(document.length())
        val keys = document.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val record = document.optJSONObject(key)
                ?: throw PeerEnrollmentException("Saved peer data is malformed")
            try {
                val fingerprint = normalizeFingerprint(record.getString("fingerprint"))
                val publicKey = Base64.decode(record.getString("publicKey"), Base64.DEFAULT)
                RescueCrypto.decodeEcPublicKey(publicKey)
                val actualFingerprint = PeerFingerprint.of(publicKey).hex
                if (!FINGERPRINT_REGEX.matches(fingerprint) || !constantTimeTextEquals(fingerprint, actualFingerprint) || key != fingerprint) {
                    throw PeerEnrollmentException("Saved peer identity did not pass fingerprint validation")
                }
                val revokedAt = if (record.isNull("revokedAt")) null else record.getLong("revokedAt")
                result += TrustedPeerRecord(
                    fingerprint,
                    sanitizeDisplayName(record.getString("displayName")),
                    publicKey.copyOf(),
                    record.getLong("enrolledAt"),
                    revokedAt,
                )
            } catch (e: PeerEnrollmentException) {
                throw e
            } catch (e: Exception) {
                throw PeerEnrollmentException("Saved peer data is malformed; no peer was trusted", e)
            }
        }
        return result
    }

    private fun readDocument(): JSONObject = try {
        JSONObject(preferences.getString(RECORDS_KEY, "{}") ?: "{}")
    } catch (e: JSONException) {
        throw PeerEnrollmentException("Saved peer data is malformed; no peer was trusted", e)
    }

    private fun persist(document: JSONObject) {
        if (!preferences.edit().putString(RECORDS_KEY, document.toString()).commit()) {
            throw PeerEnrollmentException("Could not persist the peer change")
        }
    }

    private fun normalizeFingerprint(value: String): String =
        value.filterNot { it.isWhitespace() || it == ':' || it == '-' }.uppercase(Locale.ROOT)

    private fun constantTimeTextEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(left.toByteArray(Charsets.US_ASCII), right.toByteArray(Charsets.US_ASCII))

    private fun sanitizeDisplayName(value: String): String = value
        .filterNot { it.isISOControl() }
        .trim()
        .take(MAX_DISPLAY_NAME_LENGTH)

    companion object {
        private const val PREFERENCES_NAME = "zamcan_trusted_peers_v1"
        private const val RECORDS_KEY = "records"
        private const val MAX_DISPLAY_NAME_LENGTH = 48
        private val STORE_LOCK = Any()
        private val FINGERPRINT_REGEX = Regex("[A-F0-9]{64}")
    }
}
