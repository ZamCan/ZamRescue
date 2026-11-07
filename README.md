# ZamCan Rescue

ZamCan Rescue is an offline-first Android rescue-assistance project. The initial codebase is a safe foundation, not a finished recovery product. It contains a Jetpack Compose Android shell and a pure Kotlin/JVM authenticated-handshake and encrypted-record protocol module.

## Current status

The app source provides three screens: an overview, live read-only Android build diagnostics, and security boundaries. The interface is localized in English and Swahili and follows the system light/dark theme. It requests no network, Bluetooth, camera, file, accessibility, or boot permissions.

The standalone `secure-protocol` module defines enrolled-peer mutual authentication, ephemeral P-256 ECDH, HKDF-SHA-256 via Google Tink, and sequenced AES-GCM records. It is not yet integrated with Android Keystore, peer enrollment, a transport, or any backup/restore flow. Its nine JVM tests passed on 2026-11-07, including the RFC 5869 known-answer vector. No two-device communication is available in this build, so the first physical-device milestone is not complete.

See [architecture](docs/ARCHITECTURE.md), [threat model](docs/THREAT_MODEL.md), [transport roadmap](docs/TRANSPORT_ROADMAP.md), [compatibility matrix](docs/DEVICE_COMPATIBILITY.md), and [implementation report](docs/IMPLEMENTATION_REPORT.md).

## Requirements

- A full JDK 17 or newer, including `javac`.
- Android SDK Platform 36 and compatible build tools for building the app.
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

The expected output after a successful build is `app/build/outputs/apk/debug/app-debug.apk`. This repository does not currently include a built APK. The Android build was attempted in the current sandbox, but failed before compilation because a Java compiler is missing. Do not report an APK as available unless that task completes successfully.

To install after an actual successful build, connect a test device with USB debugging authorized by its owner and run:

```bash
./gradlew :app:installDebug
```

No device is provisioned in the current development environment, so instrumentation and physical-device tests remain unrun.

## Platform and security notes

The app currently targets SDK 36, has a minimum SDK of 26, and does not open LAN sockets. Android's current local-network guidance says protections become mandatory for apps targeting SDK 37 or higher; a future direct TCP/UDP transport must implement the required runtime permission or use an appropriate system-mediated picker, and must test denial and revocation. See [Android local-network permission](https://developer.android.com/privacy-and-security/local-network-permission).

The protocol tests use software-generated keys. Production peer identity must use Android Keystore and a user-approved enrollment lifecycle. Android Keystore key material can be non-exportable while hardware backing still varies by device; query actual security level rather than assuming StrongBox. See [Android Keystore](https://developer.android.com/privacy-and-security/keystore).

The app does not bypass lock-screen authentication or recover encrypted files from a device the user cannot unlock. It does not perform hidden remote control or silently collect data.
