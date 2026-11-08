# ZamCan Rescue

ZamCan Rescue is an offline-first Android rescue-assistance project. It is a development foundation, not a finished recovery product. It contains a Jetpack Compose Android shell, a pure Kotlin/JVM authenticated-handshake protocol module, and the first Android identity/enrollment and local service-discovery adapters.

## Current status

The app provides Overview, This device, Peers, and Security screens in English and Swahili. On the Peers screen, a user can explicitly create an app-scoped P-256 signing identity in Android Keystore, copy its public key, import a peer public key, compare and re-enter the complete SHA-256 fingerprint out of band, save or revoke that peer, and start/stop local NSD service discovery.

The private identity key is not exported. Android's reported key security level is displayed; hardware backing is not assumed. Peer records contain public keys, labels, and enrollment/revocation timestamps. Both phones must enroll one another separately. Discovery shows untrusted service metadata only. It does not connect, authenticate, create consent, or transfer data. The NSD advertiser helper requires an already-bound TCP listener; this app does not yet create or advertise a live listener.

The standalone `secure-protocol` module defines enrolled-peer mutual authentication, ephemeral P-256 ECDH, HKDF-SHA-256 via Google Tink, and sequenced AES-GCM records. It is not yet wired to the Android identity store or to any TCP session, so no two-device authenticated communication, backup, or restore is available. Nine JVM protocol tests passed on 2026-11-07. The Android identity, NSD, UI, and resource changes have not been compiled or run on a device.

See [architecture](docs/ARCHITECTURE.md), [threat model](docs/THREAT_MODEL.md), [transport roadmap](docs/TRANSPORT_ROADMAP.md), [Android transport references](docs/ANDROID_TRANSPORT_REFERENCES.md), [compatibility matrix](docs/DEVICE_COMPATIBILITY.md), and [implementation report](docs/IMPLEMENTATION_REPORT.md).

## Requirements

- A full JDK 17 or newer, including `javac`.
- Android SDK Platform 36 and compatible build tools.
- A pair of physical Android devices for the first communication milestone. An emulator-only test does not satisfy that milestone.

The current sandbox has a Java 21 runtime but no `javac`, Android SDK, `adb`, or physical test phone.

## Build and test

Run the protocol tests:

```bash
./gradlew :secure-protocol:test
```

Build a debug APK only on a machine with a full JDK and Android SDK Platform 36 installed:

```bash
./gradlew :app:assembleDebug
```

The expected output after a successful build is `app/build/outputs/apk/debug/app-debug.apk`. No APK is included. The Android build was previously attempted in the current sandbox, but failed before compilation because the Java compiler and Android SDK are missing. Do not report an APK as available unless the build completes successfully.

## Platform and security notes

The app targets SDK 36 and has a minimum SDK of 26. It declares normal `INTERNET` and `CHANGE_WIFI_MULTICAST_STATE` permissions for explicit local NSD discovery. It does not request Bluetooth, camera, storage, accessibility, or boot permissions. Discovery stops when the app leaves the foreground. The current implementation does not open local TCP sockets. Android's local-network guidance changes for apps targeting SDK 37 or higher; the future TCP host and client flows must implement and test the applicable runtime permission or suitable system-mediated service selection, including denial and revocation. See [Android local-network permission](https://developer.android.com/privacy-and-security/local-network-permission).

Android Keystore key material can be non-exportable while hardware backing varies by device; this app reports the security level it can query instead of claiming uniform StrongBox protection. Peer discovery is not identity authentication. Manual peer enrollment requires a full in-person fingerprint comparison on both devices, and even enrolled peers receive no rescue capability until a separately consented authenticated session is implemented. See [Android transport references](docs/ANDROID_TRANSPORT_REFERENCES.md).

The app does not bypass lock-screen authentication or recover encrypted files from a device the user cannot unlock. It does not perform hidden remote control or silently collect data.
