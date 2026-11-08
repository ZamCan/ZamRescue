# Implementation report

Updated: 2026-11-08

## Implemented in source

- Android Studio Gradle project with a Compose `app` module and an Android-independent `secure-protocol` module.
- Four localized Compose destinations: Overview, This device, Peers, and Security. Runtime diagnostics still read actual Android `Build` properties.
- `AndroidKeystoreIdentityProvider`: explicit creation/loading of an app-scoped P-256 ECDSA signing key in Android Keystore; private key is used as a non-exportable platform handle. Corrupt/missing key material is not silently replaced. Security level is queried and displayed as reported by the provider.
- `PeerEnrollmentRepository`: validates P-256 public keys, requires full SHA-256 fingerprint re-entry after an out-of-band comparison, stores only public peer records and labels in app-private preferences, preserves revocations, and exposes only non-revoked peer keys to the protocol layer.
- Peer UI for public-key exchange by clipboard/paste, fingerprint confirmation, peer listing, and revocation. Each device must enroll the other independently. This is a manual bootstrap, not an authenticated network pairing flow.
- `AndroidNsdPeerDiscovery`: explicit user-started DNS-SD discovery for the ZamCan service type; returns service metadata as untrusted candidates and releases discovery/multicast resources on stop. Its optional advertiser requires a live bound TCP server socket and is not activated because no TCP listener exists.
- Only the normal `INTERNET` and `CHANGE_WIFI_MULTICAST_STATE` permissions were added for local service discovery. No Bluetooth, camera, storage, or accessibility permissions were added.
- Added Android instrumentation test sources for identity persistence/signing and fingerprint-gated peer enrollment/revocation, using unique test-only Keystore aliases and preferences so production records are not touched.
- Android platform references captured in `docs/ANDROID_TRANSPORT_REFERENCES.md`; architecture, threat model, and transport roadmap updated.
- Nine JVM protocol tests passed with `./gradlew --no-daemon :secure-protocol:test` on 2026-11-07. These tests cover valid exchange/key agreement, RFC 5869 output, untrusted-peer rejection/retry, transcript tampering, invitation expiry and one-use behavior, record tampering/replay/order, codec bounds, cross-session ciphertext variation, and rejection of non-P-256 public keys.

## Conditional or unverified

- The new Android source has not been compiled. The current sandbox has a Java 21 runtime but no `javac` and no configured Android SDK. No APK was built.
- Keystore key creation, persistence across process/device restarts, signature use, reported TEE/StrongBox/software levels, and provider compatibility remain unverified on Android devices.
- NSD discovery, mDNS multicast behavior, service resolution, stop-on-background handling, permission behavior, and advertiser registration remain unverified on device/emulator.
- The existing Compose instrumentation tests and the new identity/enrollment instrumentation tests have not been executed. No physical devices or `adb` target are attached.
- The new Compose UI and English/Swahili resource parity have not been run through an Android build or accessibility review.

## Not implemented

- A live TCP listener/client or socket framing; Android NSD metadata is not a connection.
- Wiring the Android Keystore identity and peer allowlist into the authenticated handshake over a transport; session transcript confirmation, user consent scopes, audit events, and monotonic host-side invitation expiry.
- QR or authenticated automatic enrollment. Manual Base64 key exchange and full in-person fingerprint comparison are the current bootstrap only.
- Target-SDK-37 local-network runtime permission/system-picker flow, BLE, Wi-Fi Direct, USB-C, background transport, transfer resumption, or peer-connection lifecycle.
- Encrypted backup vault, independent recovery credential, backup/restore, file selection, integrity manifest, or destructive restore confirmation.
- Device health/recovery-state detection beyond displaying actual Android build identifiers; consent-based screen sharing; Direct Boot; enterprise-management integration; or recovery support claims.
- Independent mobile-security review, cryptographic audit, Android compilation, instrumentation execution, physical-device compatibility testing, or APK build.

The project is still a development foundation, not a working phone-to-phone rescue product. Discovery results do not authenticate devices, and peer enrollment does not authorize any rescue operation.
