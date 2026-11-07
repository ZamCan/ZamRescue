# Android transport roadmap

## Review outcome

The current codebase has a useful JVM-tested session-protocol core but no Android transport, peer identity lifecycle, consent flow, or on-device verification. The threat model correctly treats discovery and connectivity as untrusted, requires explicit peer enrollment and scoped consent, and calls out timeout, cancellation, parser fuzzing, permission handling, and real-device testing. The implementation report confirms that none of those Android integrations has been built, and no APK or physical-device test has been produced.

This roadmap completes a first transport path only. It does not claim to complete encrypted backup or restoration. The preferred first path is foreground TCP over an existing shared Wi-Fi LAN with Android Network Service Discovery (NSD) for opt-in discovery. NSD is an Android-supported service discovery mechanism for local peer applications [2]. Keep Wi-Fi Direct, BLE, USB-C, and background listening out of the first transport milestone; add another adapter only after the first path is secure and tested.

## Stage 0 — Define the session boundary before opening sockets

Create a small Android transport contract in the app/platform layer and keep `secure-protocol` Android-independent. A `Transport` should expose bounded byte frames, connection state, cancellation, and close; it should not decide who is trusted. A `RescueSessionCoordinator` should own the state sequence `Idle → Advertising/Discovering → Connecting → Authenticating → Awaiting user confirmation → Active → Closing → Closed`. Every transition must be explicit, observable in the foreground UI, and fail closed.

Audit the wire boundary before connecting it to a socket. Use length-prefixed frames with a fixed-width length, reject zero, oversized, truncated, unknown-version, and trailing data, and cap concurrent peers and queued bytes. Ensure the received `ClientHello` carries the exact `ServerHello` for the pending invitation before calling `ResponderPending.accept`; add a safe helper and regression tests so this comparison is not left to a future transport caller. Add socket timeouts, invitation-level timeouts, cancellation, and cleanup on activity stop, service loss, network change, or user cancellation.

Review invitation expiry semantics before relying on the current 120-second field. A wall-clock timestamp can be skewed between devices or changed by the user. The host should enforce its local invitation lifetime with a monotonic Android clock; any cross-device timestamp should be treated as advisory or redesigned and covered by clock-skew tests. The host must also rate-limit unauthenticated attempts and avoid allowing a failed untrusted peer to consume a valid invitation.

## Stage 1 — Establish identity and enrollment first

Implement a `PeerIdentityStore` using Android Keystore for the long-lived P-256 signing identity. Store public peer records and revocation state separately from private key material. Do not assume hardware backing; query actual key security characteristics. Add key rotation, deletion, and lost-device/revocation behavior before treating an old pairing as authorization.

The existing protocol requires the client to know the expected server identity and the responder to have the client identity in its allowlist. Build an explicit first-pairing ceremony that verifies both public-key fingerprints on both phones before saving either peer. A short authentication string or QR exchange may be evaluated, but the design must defend against an active nearby impostor and must require confirmation on both devices. Network discovery, a familiar device name, a successful TCP connection, or a previously paired peer must never silently create trust.

Persist a scoped consent grant separately from peer enrollment. For the transport proof, scope it narrowly to a one-time authenticated connectivity test, include an expiry, and show a clear foreground indication while the session exists. No backup, file access, screen sharing, or remote-control capability should be attached to this grant.

## Stage 2 — Implement a foreground same-LAN TCP transport

Add a `TcpPeerTransport` that listens only after the user explicitly starts a session and binds the server to the selected Wi-Fi `Network` or interface rather than indiscriminately exposing a wildcard listener. Use an ephemeral port, advertise only while the invitation is live, and stop accepting immediately after cancellation, expiry, completion, or revocation. Use `Network`-scoped socket creation where needed so the transport stays on the selected network.

Use an NSD service type specific to the app and protocol version. Advertise only the service type, ephemeral port, and non-identifying session metadata; do not place public identity keys, invitation secrets, names, or user data in discovery attributes. Treat every discovered entry as attacker-controlled and show enough information for the user to choose it, but authenticate only with the enrolled identity and signed handshake. NSD registration and discovery are asynchronous; handle duplicate names, self-discovery, service loss, resolution failures, listener errors, and deterministic callback cleanup [2].

Run the transport on a bounded IO dispatcher with one reader and one writer per connection, structured cancellation, strict read/write deadlines, capped buffers, and a small limit on simultaneous unauthenticated sockets. Never log frame contents, keys, invitation nonces, or user payloads. Do not add a long-running foreground service for the initial demo; keep the interaction foreground and stop on lifecycle loss unless a later product requirement justifies a user-visible service.

## Stage 3 — Add platform permission handling deliberately

The current app targets SDK 36 and has no networking permissions. Add only the normal `INTERNET` permission and any other permission proven necessary when the LAN transport is implemented; explain the connection at the point where the user starts it. Android's current guidance says the broad `ACCESS_LOCAL_NETWORK` runtime permission is enforced for apps targeting Android 17 / SDK 37 and above. It also describes an NSD system picker path using a user-selected service, which can avoid broad permission for that selected connection [1]. Evaluate that path first, but test both the discovering/client and listening/host roles rather than assuming the picker covers every incoming-connection case.

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
