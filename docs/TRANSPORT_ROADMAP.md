# Android transport roadmap

## Review outcome

The current codebase has a JVM-tested session-protocol core plus source implementations for Android Keystore identity, explicit manual peer enrollment/revocation, and user-started NSD discovery. Those Android changes have not been compiled or exercised on device. There is still no authenticated TCP transport, session consent coordinator, or on-device verification. Discovery remains untrusted metadata; enrollment does not grant rescue capabilities.

This roadmap completes a first transport path only. It does not claim to complete encrypted backup or restoration. The preferred first path is foreground TCP over an existing shared Wi-Fi LAN with Android Network Service Discovery (NSD) for opt-in discovery. NSD is an Android-supported service discovery mechanism for local peer applications [2]. Keep Wi-Fi Direct, BLE, USB-C, and background listening out of the first transport milestone; add another adapter only after the first path is secure and tested.

## Stage 0 — Define the session boundary before opening sockets

Create a small Android transport contract in the app/platform layer and keep `secure-protocol` Android-independent. A `Transport` should expose bounded byte frames, connection state, cancellation, and close; it should not decide who is trusted. A `RescueSessionCoordinator` should own the state sequence `Idle → Advertising/Discovering → Connecting → Authenticating → Awaiting user confirmation → Active → Closing → Closed`. Every transition must be explicit, observable in the foreground UI, and fail closed.

Audit the wire boundary before connecting it to a socket. Use length-prefixed frames with a fixed-width length, reject zero, oversized, truncated, unknown-version, and trailing data, and cap concurrent peers and queued bytes. Ensure the received `ClientHello` carries the exact `ServerHello` for the pending invitation before calling `ResponderPending.accept`; add a safe helper and regression tests so this comparison is not left to a future transport caller. Add socket timeouts, invitation-level timeouts, cancellation, and cleanup on activity stop, service loss, network change, or user cancellation.

Review invitation expiry semantics before relying on the current 120-second field. A wall-clock timestamp can be skewed between devices or changed by the user. The host should enforce its local invitation lifetime with a monotonic Android clock; any cross-device timestamp should be treated as advisory or redesigned and covered by clock-skew tests. The host must also rate-limit unauthenticated attempts and avoid allowing a failed untrusted peer to consume a valid invitation.

## Stage 1 — Establish identity and enrollment first (implemented in source; validation pending)

`AndroidKeystoreIdentityProvider` now creates/loads an app-scoped P-256 ECDSA signing key in Android Keystore, exposes the private key only as a Keystore-backed signing handle, and reports the security level the provider returns. `PeerEnrollmentRepository` persists peer public keys, labels, and enrollment/revocation timestamps separately from the private identity key. It validates P-256 keys, requires full 64-character fingerprint re-entry after an in-person comparison, and excludes revoked records from the active allowlist. The Compose Peers screen exposes those real local operations. Key rotation, deletion/recovery, and revocation audit events remain open; the Android code has not been compiled or device-tested.

The current bootstrap is manual Base64 public-key exchange plus full fingerprint comparison on both phones; it is not QR-based or session-bound. The protocol still requires the client to know the expected server identity and the responder to have the client identity in its allowlist. No network discovery result, familiar service name, successful future TCP connection, or prior pairing may silently create trust.

Persist a scoped consent grant separately from peer enrollment. For the transport proof, scope it narrowly to a one-time authenticated connectivity test, include an expiry, and show a clear foreground indication while the session exists. No backup, file access, screen sharing, or remote-control capability should be attached to this grant.

## Stage 2 — Implement a foreground same-LAN TCP transport

The NSD discovery layer is now implemented in source and is explicitly started/stopped by the Peers screen. Add a `TcpPeerTransport` that listens only after the user explicitly starts a session and binds the server to the selected Wi-Fi `Network` or interface rather than indiscriminately exposing a wildcard listener. The NSD adapter can advertise only a live, bound server socket; wire it to the listener only while the invitation is live, and stop accepting immediately after cancellation, expiry, completion, or revocation. Use `Network`-scoped socket creation where needed so the transport stays on the selected network.

The adapter uses the app-specific `_zamrescue._tcp.` type and resolves service name, host, port, and optional protocol version as untrusted metadata. The advertiser publishes an ephemeral non-identifying service label and a version attribute only; it is not yet activated because no TCP listener exists. Before connecting, add same-network selection, self-discovery handling, transcript/identity verification, callback lifecycle tests, and cleanup for service loss and resolution/listener errors [2]. The current source uses the legacy `resolveService` callback path for `minSdk 26`; before release, prefer `registerServiceInfoCallback` on supported Android versions to avoid stale/incomplete resolved service data, while retaining and testing the compatible fallback [5].

Run the transport on a bounded IO dispatcher with one reader and one writer per connection, structured cancellation, strict read/write deadlines, capped buffers, and a small limit on simultaneous unauthenticated sockets. Never log frame contents, keys, invitation nonces, or user payloads. Do not add a long-running foreground service for the initial demo; keep the interaction foreground and stop on lifecycle loss unless a later product requirement justifies a user-visible service.

## Stage 3 — Add platform permission handling deliberately

The current app targets SDK 36 and declares `INTERNET` plus `CHANGE_WIFI_MULTICAST_STATE` for user-started NSD discovery; discovery does not open TCP sockets. The adapter holds the multicast lock only where older Android/T-extension behavior requires it and only for the active discovery window. Android's current guidance says the broad `ACCESS_LOCAL_NETWORK` runtime permission is enforced for apps targeting Android 17 / SDK 37 and above. It also describes an NSD system picker path using a user-selected service, which can avoid broad permission for that selected connection [1]. This code does not yet use the picker or handle target-37 permission prompts. Evaluate that path first, but test both discovering/client and listening/host roles rather than assuming the picker covers every incoming-connection case.

Keep the current target SDK unchanged for the first implementation unless a separately reviewed target update is needed. Test Android 16's opt-in local-network restriction as well as Android 17 behavior before raising the target. If the user-selected picker cannot support the complete transport flow, implement `ACCESS_LOCAL_NETWORK` runtime permission handling, rationale, denial, revocation, and retry behavior before shipping a broad LAN discovery feature [1]. Do not request Bluetooth or Wi-Fi Direct permissions for a TCP/NSD-only first release.

## Stage 4 — Integrate the authenticated protocol and consent state

After a socket is established, exchange only the bounded invitation and client handshake frames. The client must compare the advertised key with the enrolled peer before answering. The responder must compare the presented client key with its enrolled allowlist before deriving keys. Both sides must verify the server-authentication transcript and confirm the same session transcript hash before marking the session authenticated. Discovery success or socket connection alone must never show an authenticated state.

Use the existing `SecureChannel` only after both peers authenticate. Deliver a fixed, harmless test payload and acknowledgement, then close cleanly. Do not add backup transfer until this transport-only test demonstrates key agreement, encrypted record delivery, and rejection of tampering, replay, out-of-order frames, oversized data, stale invitations, identity mismatch, and simultaneous/repeated attempts. Make user confirmation visible before any future operation beyond the test exchange.

## Stage 5 — Verify on physical devices and permission states

Add JVM tests for stream framing, maximum lengths, truncated frames, transcript/invitation mismatch, wrong-peer keys, clock skew, expiry, races, duplicate callbacks, and cancellation. Fuzz the frame decoder and handshake codec. Add Android instrumentation tests for NSD lifecycle, socket cleanup, network changes, permission denial/revocation, and foreground consent state. Keep protocol tests independent of Android.

Then demonstrate the session on two physical Android devices with the same app build. At minimum, test a shared home Wi-Fi network and a phone hotspot, reconnect after a network change, test a peer disappearing during handshake, and repeat with the applicable Android 16 and 17 local-network controls. Record device model, OS/API level, app version, permission state, and actual result. Test a representative non-Pixel device before making broad compatibility claims. No success should be reported until the protocol completes in both directions on real devices and negative cases fail closed.

## Later transport adapters

Only after the same session coordinator and framing tests pass over LAN should Wi-Fi Direct be considered for nearby devices without an existing LAN. Its documented flow needs Wi-Fi P2P lifecycle handling and additional permissions, and it still uses sockets; it must reuse the same peer authentication and consent model [3]. BLE should be evaluated separately for discovery and control messages, not assumed to be an efficient bulk-data transport. USB-C requires a separate accessory/USB permission and cable-compatibility investigation. None of these transports should introduce a second trust model or bypass the signed protocol.

## Exit criteria for the transport layer

The transport layer is ready for the next milestone only when two physical Android devices can explicitly discover/select one another, verify or use previously enrolled identities, complete the mutual handshake, exchange and acknowledge an encrypted test record, and cleanly cancel/revoke the session. Permission denial, identity mismatch, replay, malformed frames, expiry, and network loss must fail closed. The app must have no hidden listener after the user ends the session, and the implementation report must state the devices and permission states actually tested.

## References

[1]: https://developer.android.com/privacy-and-security/local-network-permission "Android local network permission guidance"
[2]: https://developer.android.com/develop/connectivity/wifi/use-nsd "Android Network Service Discovery guide"
[3]: https://developer.android.com/develop/connectivity/wifi/wifi-direct "Android Wi-Fi Direct guide"
[4]: https://developer.android.com/privacy-and-security/keystore "Android Keystore guidance"
[5]: https://developer.android.com/reference/android/net/nsd/NsdManager "Android NsdManager API reference"
