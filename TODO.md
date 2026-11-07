# TODO

## Android product foundation

- [ ] Native Android implementation using Kotlin and Jetpack Compose.
- [ ] Clean modular architecture with documented interfaces.
- [ ] Offline operation without requiring cloud infrastructure.
- [ ] Professional dark and light themes.
- [ ] Swahili and English localization.
- [ ] Accessibility and battery-efficient operation.

## Prepared rescue and secure connectivity

- [ ] Bluetooth LE discovery and authenticated local communication.
- [ ] Wi-Fi Direct and local-network communication.
- [ ] USB-C transport research and implementation where supported.
- [ ] Secure device enrollment and trusted-peer management.
- [ ] Capability-based permissions and revocation.
- [ ] Implement replay protection, session expiration, peer revocation, and safe failure behavior.
- [ ] Require explicit enrollment, secure authentication, permission scoping, and audit logging.
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
- [ ] Use vetted cryptographic implementations and Android Keystore where appropriate.
- [ ] No fake functionality.
- [ ] No buttons that only display explanations instead of performing their advertised operation.
- [ ] No invented Android APIs.
- [ ] No mock data presented as actual device information.
- [ ] No unrestricted background monitoring.
- [ ] No unnecessary permissions.
- [ ] No misleading claims of password bypass or data recovery.
- [ ] No hidden remote-control functionality.
- [ ] No silent screen capture.

## Validation and delivery

- [ ] Run unit tests, instrumentation tests, and security checks.
- [ ] Build an installable debug APK when build tooling is available.
- [ ] Complete Android Studio/Gradle project with Kotlin source code, modular architecture, security design and threat model, AGENTS.md engineering instructions, README with installation and testing procedures, physical-device compatibility matrix, unit and instrumentation tests, and an honest implementation report listing completed, conditional, and unimplemented features.
- [ ] Prioritize a small number of genuinely functional rescue operations over an impressive-looking but nonfunctional prototype.
- [ ] Do not claim an APK has been built unless the build has actually completed successfully.
