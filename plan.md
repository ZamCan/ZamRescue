# ZamCan Rescue initial engineering plan

## Scope and constraints

The supplied repository had no commits or project files. This pass establishes the native Android foundation and isolates the authenticated session protocol so its logic can be unit-tested without Android. The primary product milestone—authenticated communication between two physical Android devices followed by encrypted backup transfer and successful restoration—cannot be demonstrated in this environment because no full JDK (`javac`), Android SDK, or physical devices are provisioned. This pass must not claim that milestone, an APK, or backup functionality is complete.

The project uses Kotlin, Jetpack Compose, Gradle, `minSdk 26`, `compileSdk 36`, and `targetSdk 36`. The current app shell requests no permissions because it performs no transport or file operation. Before implementing direct local TCP for `targetSdk 37+`, follow Android's local-network permission rules. Production identity keys must be integrated with Android Keystore. Protocol code currently uses JCA software keys for testability, and is not a substitute for an external review.

## Structure

- `app/`: Android application entry point, Compose navigation, localization, themes, and read-only platform diagnostics.
- `secure-protocol/`: Android-independent handshake and authenticated-record protocol plus JVM tests.
- `docs/`: architecture, threat model, compatibility matrix, and implementation report.
- `AGENTS.md`: ongoing engineering and safety constraints.

Future transport, peer identity, vault, and diagnostics integrations should be separated only when they have a genuine implementation and testable responsibility; do not create nonfunctional shells.

## Design

Use a restrained, high-contrast Android-native layout with botanical green as the trust/safety accent. Status and consent are prominent; recovery expectations are direct. The app follows system dark mode and provides English/Swahili strings. Current screens navigate to actual status and live system build diagnostics; no disabled placeholder action is presented as working rescue functionality.

## Implementation sequence

1. Establish project and document its safety constraints.
2. Implement and test a standalone peer-authentication protocol with message bounds, expiry, key agreement, encrypted records, and replay checks.
3. Add a truthful Compose shell and actual read-only diagnostics.
4. Once full build tooling and physical-device access are available, compile the Android app and validate the protocol on physical devices.
5. Only after a security review and working session flow, add transport and encrypted backup/restore operations.

The remaining user requirements are itemized without weakening their acceptance clauses in `TODO.md`.
