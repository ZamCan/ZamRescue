# ZamCan Rescue implementation plan

## Scope and constraints

The primary product milestone remains authenticated communication between two physical Android devices followed by encrypted backup transfer and successful restoration. Neither that milestone, an APK, nor backup functionality is complete. The current environment has no full JDK (`javac`), Android SDK, or physical devices, so new Android code cannot be compiled or device-verified here.

The project uses Kotlin, Jetpack Compose, Gradle, `minSdk 26`, `compileSdk 36`, and `targetSdk 36`. Android Keystore identities and user-started NSD discovery are now implemented in source. The app declares only the normal `INTERNET` and `CHANGE_WIFI_MULTICAST_STATE` permissions for discovery. Direct local TCP is still absent; revisit Android's target-37 local-network permission and system-picker behavior before increasing the target or shipping socket access.

## Current implementation structure

- `app/`: Compose navigation/localization, device diagnostics, Android Keystore P-256 signing identity, manual fingerprint-confirmed peer enrollment and revocation, and user-started NSD discovery.
- `secure-protocol/`: Android-independent handshake and authenticated-record protocol plus passing JVM tests.
- `docs/`: architecture, threat model, transport roadmap, Android API references, compatibility matrix, and implementation report.
- `AGENTS.md`: continuing engineering, safety, and validation constraints.

The peer store contains public keys and revocation state only. Public-key exchange is by Base64 copy/paste; each user compares the full fingerprint in person on both phones. NSD results are untrusted metadata, not a trust or connection. The advertiser adapter requires a live bound TCP socket but is not invoked because a TCP listener has not been implemented.

## Completed source work for this phase

1. Add an Android Keystore P-256 ECDSA identity provider with non-exportable private-key handles and reported security-level metadata.
2. Add peer-key validation, full fingerprint confirmation, local persistence, peer listing, and revocation.
3. Add a localized foreground screen for identity management, manual enrollment, peer revocation, and explicit NSD discovery with stop-on-background handling.
4. Add an NSD adapter that resolves only the app service type and can advertise only a real bound TCP listener; declare only discovery-related normal permissions.
5. Record limitations, official Android references, and the next connection milestone.

These steps are complete in source only. They remain unbuilt and unverified on Android because of the sandbox toolchain/SDK blocker.

## Next implementation sequence

1. Compile on a supported Android SDK/JDK host; fix any Kotlin/API/resource issues; run instrumentation tests for Keystore persistence/signing, peer-store fingerprint mismatch/revocation, permission disclosure, and NSD lifecycle.
2. Add a bounded stream codec and foreground TCP listener/client. Use the socket's real bound port to activate NSD advertisement only while an invitation is valid.
3. Add a session coordinator that checks the exact pending invitation, loads both enrolled identities, runs the mutual protocol handshake, enforces host-side monotonic lifetime, closes on lifecycle/network loss, and exposes authenticated state only after both sides complete verification.
4. Test on two physical Android devices over shared Wi-Fi and hotspot, with network changes and all negative handshake/framing cases. Evaluate Android 16 and Android 17 local-network restrictions for both listening and connecting roles.
5. Only after a reviewed authenticated session exists, add a tightly scoped connectivity test; then plan encrypted backup/restore as a separate security-reviewed milestone with an independent recovery credential.

No rescue capability should be presented as available until its real implementation handles cancellation, permission denial, expiry, revocation, and failure safely.
