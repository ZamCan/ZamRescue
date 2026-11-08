# Android transport implementation references

These official Android references inform the current implementation. API levels and permission behavior should be rechecked before increasing `targetSdk` or shipping a transport.

- Android Keystore keys can be used through standard Java security APIs while private key material remains non-exportable. Android explicitly warns not to assume hardware-backed storage; inspect key security level where available. [1]
- `KeyGenParameterSpec` supports P-256 EC key generation with `PURPOSE_SIGN` and `DIGEST_SHA256`; the generated private key can be reloaded with `KeyStore.getKey` and the public key from the stored certificate. [2]
- `NsdManager` operations are asynchronous. Discovery should be stopped when no longer needed. NSD can publish a service's name, type, and port; discovered services still require a separate authentication protocol. On Android versions before T extension 7, foreground apps need a Wi-Fi multicast lock to receive mDNS packets. [3]
- A multicast lock should be held only while discovery is active because it allows multicast receive and can increase battery drain. [4]
- Android 17 / target SDK 37 introduces mandatory local-network runtime permission behavior. Android documents an NSD system picker for user-selected devices; whether it covers both client and listening roles must be validated for the final flow. [5]

## References

[1]: https://developer.android.com/privacy-and-security/keystore "Android Keystore system"
[2]: https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec "Android KeyGenParameterSpec reference"
[3]: https://developer.android.com/reference/android/net/nsd/NsdManager "Android NsdManager reference"
[4]: https://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock "Android Wi-Fi multicast-lock reference"
[5]: https://developer.android.com/privacy-and-security/local-network-permission "Android local network permission guidance"
