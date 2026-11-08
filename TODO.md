# TODO

## Android product foundation

- [x] Native Android implementation using Kotlin and Jetpack Compose.
- [x] Clean modular architecture with documented interfaces.
- [x] Offline operation without requiring cloud infrastructure (the current source uses no cloud service).
- [x] Professional dark and light themes.
- [x] Swahili and English localization for current screens.
- [ ] Accessibility and battery-efficient operation review.

## Prepared rescue and secure connectivity

- [ ] Bluetooth LE discovery and authenticated local communication.
- [ ] Wi-Fi Direct and local-network TCP communication.
- [x] Foreground NSD service discovery with explicit start/stop; results remain untrusted metadata.
- [ ] USB-C transport research and implementation where supported.
- [x] Android Keystore P-256 identity, full-fingerprint-confirmed manual peer enrollment, local peer records, and revocation (source-level; Android build/device validation still pending).
- [ ] Capability-based permissions and operation-specific consent.
- [ ] Integrate identity allowlists with a live authenticated session; preserve replay protection, expiry, revocation, and safe failure behavior end to end.
- [ ] Require explicit enrollment, secure authentication, permission scoping, and audit logging for real rescue operations.
- [ ] Previously paired devices must not receive unrestricted access.
- [ ] The first milestone must demonstrate authenticated communication between two physical Android devices, followed by encrypted backup transfer and successful restoration.

## Backup, transfer, and restoration

- [ ] Encrypted backup and recovery vault.
- [ ] Protect sensitive recovery data with a separately established recovery key or credential.
- [ ] Data integrity checks and resumable file transfers.
- [ ] No collection of private files without explicit authorization.
- [ ] No destructive recovery actions without informed user confirmation.

## Diagnostics and assisted recovery

- [ ] App-accessible device diagnostics.
- [ ] Consent-based screen sharing and permitted accessibility assistance.
- [ ] Minimal Direct Boot-aware functionality.
- [ ] Device-specific unprepared recovery guidance.
- [ ] Optional enterprise-managed-device research module.
- [ ] Do not claim that a device can be remotely unlocked unless an explicitly supported, correctly provisioned enterprise-management API provides that functionality.

## Security and implementation integrity

- [ ] Do not attempt to bypass Android lock-screen authentication, extract protected encryption keys, exploit USB debugging, silently install applications, or circumvent user consent.
- [x] Use vetted cryptographic implementations and Android Keystore where appropriate.
- [x] No fake functionality in implemented controls; NSD is explicitly described as discovery only.
- [x] No buttons that only display explanations instead of performing their advertised operation in the implemented peer/discovery flows.
- [x] No invented Android APIs.
- [x] No mock data presented as actual device information.
- [x] No unrestricted background monitoring.
- [x] No unnecessary permissions identified for the implemented NSD feature.
- [x] No misleading claims of password bypass or data recovery.
- [x] No hidden remote-control functionality.
- [x] No silent screen capture.

## Validation and delivery

- [ ] Compile Android app and run unit tests, instrumentation tests, and security checks for the new Android code.
- [ ] Build an installable debug APK when build tooling is available.
- [ ] Complete Android Studio/Gradle project with Kotlin source code, modular architecture, security design and threat model, AGENTS.md engineering instructions, README with installation and testing procedures, physical-device compatibility matrix, unit and instrumentation tests, and an honest implementation report listing completed, conditional, and unimplemented features.
- [ ] Prioritize a small number of genuinely functional rescue operations over an impressive-looking but nonfunctional prototype.
- [ ] Do not claim an APK has been built unless the build has actually completed successfully.
