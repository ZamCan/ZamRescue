# Security design and threat model

## Protected assets

Future versions may handle enrolled peer identities, rescue-session credentials, user-selected files, backup manifests, recovery keys, diagnostics, and consent/audit records. This build has no backup vault, no peer store, no transport, and no file access. It does not persist recovery material.

## Adversaries and assumptions

The design considers a passive or active attacker on the same local network, an unapproved nearby device, a replaying peer, a peer whose authorization was revoked, and accidental disclosure through logs or an overbroad permission. It assumes the Android operating system and cryptographic providers are functioning as specified. Rooted or compromised operating systems, coercion, physical attacks, malicious firmware, and vulnerabilities in the Android platform are outside the current protection claim.

A trusted peer can still be compromised. Enrollment therefore does not mean unrestricted access: each future recovery action needs a distinct scope, foreground consent, expiry, and revocation path. A peer identity is not a recovery key and must never be sufficient to decrypt backups.

## Security goals

1. No rescue action occurs without explicit user intent and an operation-specific authorization.
2. A transport connection is not treated as proof of identity.
3. Peers authenticate against explicitly enrolled identity keys; invitations expire and are single-use.
4. Handshake signatures bind both identities, ephemeral keys, and nonces to one transcript.
5. Session confidentiality and integrity use independent directional AES-GCM keys derived with HKDF-SHA-256.
6. Records are bounded, authenticated, sequenced, and rejected if replayed or delivered out of order.
7. User-selected backup content, when implemented, will use a recovery credential independent of Android device identity, with integrity checks and safe restoration semantics.
8. Denied or revoked permissions and peers fail closed without bypass attempts.

## Current protocol controls

`secure-protocol` uses Java Cryptography Architecture providers for P-256 ECDSA identity signatures, ephemeral P-256 ECDH, SHA-256, and AES-GCM. HKDF-SHA-256 is delegated to Google Tink 1.24.0. The module includes transcript-bound mutual authentication, 32-byte nonces, a 120-second invitation lifetime, one-use pending state, a trusted-key allowlist on the responder, independent directional session keys, and strict record sequence checks. Message fields and payloads are size-bounded.

This is protocol code only. It has not been connected to Android Keystore, network transport, durable peer revocation, permission prompts, audit storage, or any backup encryption. Software key generation in JVM tests is not production key storage. The dependencies and protocol have only been functionally tested on the JVM; they have not received an independent security audit. Before any release, review nonce lifecycle, persistence and revocation semantics, all parser boundaries, implementation/provider compatibility, and the complete transport state machine with an independent mobile-security reviewer.

## Non-goals and prohibited behavior

The app will not bypass Android lock-screen authentication, recover files hidden behind device encryption without the user's authorized access, extract protected encryption keys, exploit USB debugging, install an app silently, perform hidden remote control, capture the screen without consent, or claim a universal remote-unlock capability. Enterprise-management behavior may be considered only for correctly provisioned and explicitly managed devices using documented APIs.

## Risks and mitigations still required

- **No production transport:** there is no socket, BLE, Wi-Fi Direct, or USB-C implementation, so no real two-device communication is available. Add transport-specific timeouts, cancellation, permission handling, parser fuzzing, and physical-device tests before enabling a connect action.
- **No production identity lifecycle:** the app has no Keystore key generation, peer enrollment UI, fingerprint verification, durable allowlist, revocation, or audit log. Implement these before accepting real peers.
- **No recovery vault:** the protocol does not encrypt files. A separate recovery key, authenticated backup format, integrity verification, interrupted-transfer recovery, and safe restore design remain required.
- **Provider compatibility:** cryptographic algorithm availability can vary. Test at the declared minimum and representative vendor devices; do not silently downgrade algorithms.
- **Local-network permission changes:** Android 17 targeting SDK 37 requires local-network permission management for direct LAN sockets. Reassess the permission and privacy-preserving picker options before any transport launch. See [Android local-network permission](https://developer.android.com/privacy-and-security/local-network-permission).
- **Keystore hardware variation:** key material can be non-exportable without being StrongBox-backed. Query the actual security level and do not make uniform hardware claims. See [Android Keystore](https://developer.android.com/privacy-and-security/keystore).

## Security verification status

Nine JVM tests pass, including an RFC 5869 HKDF-SHA-256 known-answer test, valid exchanges, peer allowlisting, transcript tampering, expiry, one-use invitations, record tampering, replay, and serialization. No instrumentation run, device test, penetration test, or independent cryptographic audit is claimed. No APK was built.
