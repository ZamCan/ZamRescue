package org.zamcan.zamrescue.identity

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import org.zamcan.zamrescue.protocol.PeerFingerprint
import org.zamcan.zamrescue.protocol.RescueCrypto
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec

/** Describes only what this device reports; StrongBox is never assumed. */
enum class KeySecurityLevel {
    SOFTWARE,
    TRUSTED_ENVIRONMENT,
    STRONGBOX,
    UNKNOWN,
}

data class DeviceIdentity(
    /** Android Keystore private-key handle. Its encoded private key is not exportable. */
    val keyPair: KeyPair,
    val fingerprintHex: String,
    val securityLevel: KeySecurityLevel,
) {
    val publicKeyEncoded: ByteArray get() = keyPair.public.encoded.copyOf()
}

class IdentityKeyException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Owns one app-scoped P-256 ECDSA identity in AndroidKeyStore. Call from a worker thread;
 * Keystore loading and key generation can take long enough to affect UI responsiveness.
 */
class AndroidKeystoreIdentityProvider(private val alias: String = DEFAULT_ALIAS) {
    @Synchronized
    @Throws(IdentityKeyException::class)
    fun load(): DeviceIdentity? {
        return try {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(alias)) readIdentity(keyStore) else null
        } catch (e: IdentityKeyException) {
            throw e
        } catch (e: Exception) {
            throw IdentityKeyException("Could not load the device identity", e)
        }
    }

    /** Creates the identity only when explicitly requested; never silently replaces a broken key. */
    @Synchronized
    @Throws(IdentityKeyException::class)
    fun create(): DeviceIdentity {
        return try {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(alias)) return readIdentity(keyStore)

            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            generator.initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec(P256_CURVE))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKeyPair()
            readIdentity(loadKeyStore())
        } catch (e: IdentityKeyException) {
            throw e
        } catch (e: Exception) {
            throw IdentityKeyException("Could not create the device identity", e)
        }
    }

    private fun readIdentity(keyStore: KeyStore): DeviceIdentity {
        val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            ?: throw IdentityKeyException("The stored device identity has no private-key handle")
        if (!privateKey.algorithm.equals(KeyProperties.KEY_ALGORITHM_EC, ignoreCase = true)) {
            throw IdentityKeyException("The stored device identity is not an EC signing key")
        }
        val publicKey = keyStore.getCertificate(alias)?.publicKey
            ?: throw IdentityKeyException("The stored device identity has no public certificate")
        try {
            RescueCrypto.decodeEcPublicKey(publicKey.encoded)
        } catch (e: Exception) {
            throw IdentityKeyException("The stored device identity is not a valid P-256 key", e)
        }
        val securityLevel = try {
            readSecurityLevel(privateKey)
        } catch (_: Exception) {
            KeySecurityLevel.UNKNOWN
        }
        val keyPair = KeyPair(publicKey, privateKey)
        return DeviceIdentity(keyPair, PeerFingerprint.of(publicKey.encoded).hex, securityLevel)
    }

    private fun readSecurityLevel(privateKey: PrivateKey): KeySecurityLevel {
        val factory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
        val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (keyInfo.securityLevel) {
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeySecurityLevel.SOFTWARE
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeySecurityLevel.TRUSTED_ENVIRONMENT
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeySecurityLevel.STRONGBOX
                else -> KeySecurityLevel.UNKNOWN
            }
        }
        @Suppress("DEPRECATION")
        return if (keyInfo.isInsideSecureHardware) {
            KeySecurityLevel.TRUSTED_ENVIRONMENT
        } else {
            KeySecurityLevel.SOFTWARE
        }
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_ALIAS = "org.zamcan.zamrescue.identity.signing.v1"
        private const val P256_CURVE = "secp256r1"
    }
}
