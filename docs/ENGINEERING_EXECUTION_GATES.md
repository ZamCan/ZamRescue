# Engineering execution gates — ZamRescue

## Baseline (2026-11-08)

Existing: Compose shell, Keystore identity, manual peer enrollment, NSD discovery, isolated secure-protocol module and JVM tests. Not verified: Android compile, instrumentation, real two-phone session. Missing: TCP listener/client integration, consent-scoped rescue operations, backup/restore, USB and screen assistance.

## Gate 0: Reproducible Android build

- Run `./gradlew :secure-protocol:test :app:assembleDebug lintDebug` with complete JDK and Android SDK 36.
- Fix all compile/resource/manifest issues without weakening authentication.
- Record exact command, environment, failures, and artifact hashes in implementation report.

## Gate 1: Real authenticated transport

- Bind local TCP server to an available interface and advertise only its actual port through NSD.
- Resolve peers as untrusted candidates; connect and frame messages with strict length limits.
- Wire Keystore signing and enrolled peer public keys to secure-protocol handshake.
- Enforce peer revocation, handshake expiry, operation consent, and connection teardown.
- Test on two real devices; negative tests for rogue peer, replay, interruption, and denial.

## Gate 2: Data recovery MVP

- User-selected SAF files only; no arbitrary private app data.
- Independent recovery secret and encrypted backup manifest with authenticated chunk streaming.
- Transfer and resume, integrity verification, atomic commit, explicit restore destination.
- Test byte-identical restoration and failures (storage full, corrupt chunk, wrong key, disconnect).

## Gate 3: Premium UX and localization

- Implement accessible Compose design system: typography, spacing, semantic icons, contrast, light/dark palettes, loading/error/empty states.
- Home, Rescue, Devices, Vault, Settings; every button backed by a real operation or explicitly disabled.
- English and Swahili parity; RTL-ready structure. Avoid excessive cards, redundant descriptions, animation, and false progress.

## Gate 4: Conditional features

- Wi-Fi Direct and BLE permission-aware discovery; device compatibility tests.
- USB-C host/accessory proof-of-concept only for validated pairs.
- Screen assistance only with fresh MediaProjection consent and required foreground service policy.
- Unprepared recovery advisor: official account/repair/backup paths, no bypass claims.

## Gate 5: Release candidate

- Unit, instrumentation, UI accessibility, performance, security and two-device end-to-end tests.
- Verify signing, app ID, version code, reproducible release build and artifact hash.
- Produce a signed test APK and mark unsupported features clearly.

**Execution discipline:** Complete gates sequentially, commit reviewable changes, update TODO/IMPLEMENTATION_REPORT with verified evidence, and never claim hardware tests passed without hardware access.
