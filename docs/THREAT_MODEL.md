# Security design and threat model

## Protected assets

This build now creates an app-scoped P-256 signing identity in Android Keystore when the user asks it to, and can retain peer public keys, display labels, enrollment times, and revocation times in app-private preferences. Private signing material is not stored in preferences. The app has no backup vault, TCP session, authenticated peer connection, or file access, and it does not persist recovery material.

## Adversaries and assumptions

The design considers a passive or active attacker on the same local network, an unapproved nearby device, a replaying peer, a peer whose authorization was revoked, and accidental disclosure through logs or an overbroad permission. NSD service names, TXT attributes, IP addresses, and ports are attacker-controlled hints. The design assumes the Android operating system and cryptographic providers are functioning as specified. Rooted or compromised operating systems, coercion, physical attacks, malicious firmware, and vulnerabilities in the Android platform are outside the current protection claim.

A trusted peer can still be compromised. Enrollment therefore does not mean unrestricted access: each future recovery action needs a distinct scope, foreground consent, expiry, and revocation path. A peer identity is not a recovery key and must never be sufficient to decrypt backups.

## Security goals

1. No rescue action occurs without explicit user intent and an operation-specific authorization.
2. A transport connection or discovery result is never treated as proof of identity.
3. Peers authenticate against explicitly enrolled identity keys; invitations expire and are single-use.
4. Handshake signatures bind both identities, ephemeral keys, and nonces to one transcript.
5. Session confidentiality and integrity use independent directional AES-GCM keys derived with HKDF-SHA-256.
6. Records are bounded, authenticated, sequenced, and rejected if replayed or delivered out of order.
7. User-selected backup content, when implemented, will use a recovery credential independent of Android device identity, with integrity checks and safe restoration semantics.
8. Denied or revoked permissions and peers fail closed without bypass attempts.

## Current protocol and identity controls

`secure-protocol` uses Java Cryptography Architecture providers for P-256 ECDSA identity signatures, ephemeral P-256 ECDH, SHA-256, and AES-GCM. HKDF-SHA-256 is delegated to Google Tink 1.24.0. The module includes transcript-bound mutual authentication, 32-byte nonces, a 120-second invitation lifetime, one-use pending state, a trusted-key allowlist on the responder, independent directional session keys, and strict record sequence checks. Message fields and payloads are size-bounded.

`AndroidKeystoreIdentityProvider` explicitly creates or loads one app-scoped P-256 ECDSA signing key. It does not silently replace a missing/corrupt alias, requests signing authorization with SHA-256, and reports the security level Android actually returns rather than assuming StrongBox. The private key is exposed only as an Android Keystore signing handle; it is not exported or copied to preferences. The key does not currently require per-use biometric authentication, so session signatures must remain behind future explicit foreground consent.

`PeerEnrollmentRepository` accepts only valid P-256 public keys and saves them only after the user re-enters the full 64-hex-character SHA-256 fingerprint they compared out of band. Each device must enroll the other independently. Revocation persists and removes that key from the active protocol allowlist. Corrupt peer data is rejected instead of treated as an empty/trusted store. These records contain public keys, not secrets; they are app-private metadata, and manifest backup is disabled. The UI currently uses manual Base64 exchange, not QR or an authenticated network enrollment protocol.

`AndroidNsdPeerDiscovery` performs user-started DNS-SD browsing, resolves only the ZamCan service type, reports an address/port/version as an untrusted candidate, and stops with the foreground screen. Its optional advertiser requires a live bound TCP server socket and publishes only an ephemeral non-identifying service name, port, and protocol version. No TCP server is connected yet, so the app currently does not advertise itself. Discovery is not authentication and cannot add or authorize a peer.

## Non-goals and prohibited behavior

The app will not bypass Android lock-screen authentication, recover files hidden behind device encryption without the user's authorized access, extract protected encryption keys, exploit USB debugging, install an app silently, perform hidden remote control, capture the screen without consent, or claim a universal remote-unlock capability. Enterprise-management behavior may be considered only for correctly provisioned and explicitly managed devices using documented APIs.

## Risks and mitigations still required

- **No authenticated transport:** there is no TCP socket session, Bluetooth, Wi-Fi Direct, or USB-C implementation. NSD only finds service metadata. Add bounded framing, transcript/identity checks, timeouts, cancellation, permission handling, parser fuzzing, and physical-device tests before enabling any connect action.
- **Manual key exchange:** users copy public keys and compare full fingerprints in person on both devices. This is explicit but inconvenient and not cryptographically tied to a transport session; the next protocol integration must still authenticate the enrolled identities and obtain session-specific user consent.
- **Incomplete identity lifecycle:** key rotation, device-reinstall/loss recovery, revocation audit events, and independent storage integrity remain unimplemented. Keystore hardware security levels have not been observed on representative devices.
- **Invitation time semantics:** the protocol currently carries a wall-clock expiry. Before transport integration, enforce host-side lifetime with a monotonic clock and test clock skew.
- **Local-network permission changes:** the app targets SDK 36. Android 17 targeting SDK 37 changes local-network permission behavior; this build does not yet implement the target-37 runtime permission or the system NSD picker. Reassess both host and client roles before raising the target. See [Android local-network permission](https://developer.android.com/privacy-and-security/local-network-permission).
- **Multicast behavior:** NSD may require a Wi-Fi multicast lock on older Android/T-extension versions. The adapter holds it only during active discovery and releases it on stop; device/OS behavior remains untested. See [NsdManager](https://developer.android.com/reference/android/net/nsd/NsdManager).
- **Provider compatibility:** cryptographic algorithm availability can vary. Test at the declared minimum and representative vendor devices; do not silently downgrade algorithms.
- **No recovery vault:** the protocol does not encrypt files. A separate recovery key, authenticated backup format, integrity verification, interrupted-transfer recovery, and safe restore design remain required.

## Security verification status

Nine JVM protocol tests passed on 2026-11-07, including an RFC 5869 HKDF-SHA-256 known-answer test, valid exchanges, peer allowlisting, transcript tampering, expiry, one-use invitations, record tampering, replay, and serialization. The new Android identity, peer store, Compose UI, and NSD code have not been compiled or executed because the current sandbox lacks the Android SDK and `javac`; no instrumentation run, device test, penetration test, or independent cryptographic audit is claimed. No APK was built.
