# Engineering instructions

## Product and safety boundaries

- This is a legitimate Android rescue-assistance app. Never implement lock-screen bypass, protected-key extraction, USB-debugging exploitation, silent installation, hidden remote control, silent screen capture, or collection of files without informed authorization.
- Do not treat prior pairing as blanket authorization. Model trust, operation scope, user confirmation, expiry, revocation, and audit events independently.
- Do not expose controls for an operation until that operation is connected to a real implementation and handles failure safely. Do not present sample values as device facts.
- Do not describe a feature as tested on physical devices unless that test was actually run and recorded.
- Never claim an APK was produced unless `:app:assembleDebug` completed successfully. Never commit an APK without an explicit request.

## Architecture

- `app/` is the Android/Kotlin/Jetpack Compose shell and platform-adapter layer.
- `secure-protocol/` is pure Kotlin/JVM protocol logic with no Android dependency. Preserve that separation so its security tests can run without an Android SDK.
- Keep platform permissions capability-based and request each only when a real user-started feature needs it. Current NSD discovery declares normal `INTERNET` and `CHANGE_WIFI_MULTICAST_STATE` permissions; it does not request Bluetooth, camera, storage, accessibility, or Direct Boot permissions.
- Treat NSD results as untrusted discovery metadata only. Do not call discovery an authenticated transport or connect a result until peer identity, session consent, socket framing, and failure handling are integrated.
- Stop discovery and release multicast resources when the feature or app leaves the foreground. Do not add background monitoring for peer discovery.
- Reassess Android local-network permission and system-picker behavior before raising target SDK to 37 or opening direct LAN sockets.

## Security engineering

- Prefer Android Keystore and vetted platform/library primitives. Do not invent cryptographic algorithms, formats, or key-wrapping schemes.
- For the protocol, retain strict message-size bounds, transcript-bound signatures, short invitation expiry, one-use handshake state, independently derived directional keys, authenticated encryption, and monotonic replay checks.
- Treat serialization and error handling as attack surfaces. Reject malformed lengths, trailing bytes, unknown versions, stale sessions, untrusted keys, and authentication failures without exposing sensitive internals.
- Any changes to cryptographic code require positive tests for valid exchanges and negative tests for tampering, replay, expiry, and untrusted peers.
- Keep secrets out of logs, crash reports, clipboard history, and persistent storage. Do not store a recovery credential next to encrypted recovery data.

## Build and validation

- Preferred checks: `./gradlew :secure-protocol:test`, then `./gradlew :app:assembleDebug` on a machine with a full JDK and compatible Android SDK installed.
- Use `./gradlew test` for JVM tests and Android instrumentation tests only with a configured emulator or physical device.
- The current sandbox has a Java 21 runtime but no `javac`, no configured Android SDK, and no test phone. Label those checks as blocked/unrun, not passed.
- Review `docs/THREAT_MODEL.md`, `docs/ARCHITECTURE.md`, `docs/DEVICE_COMPATIBILITY.md`, and `TODO.md` when changing capability or security boundaries.
