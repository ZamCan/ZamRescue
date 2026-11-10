# ZamRescue external security certification handoff

Status: **NOT CERTIFIED**. This is an assessor handoff, not a certificate.

## 1. Freeze the candidate
Choose a release commit and tag it (for example, v1.0.0-rc1). Record the full
Git SHA, APK SHA-256, versionCode/versionName, Android SDK levels, and
the SHA-256 fingerprint of the release signing certificate.

## 2. Finish internal release gates
Require green protocol and Android unit tests, signed release build verification,
static analysis, dependency vulnerability triage, an SBOM, negative security
tests, and two-device Android instrumentation/manual acceptance evidence.
Close all HIGH issues in SECURITY_AUDIT_2026-11-10.md and disposition all
remaining issues with documented risk owners.

## 3. Arrange an independent assessor
Engage a mobile-app security testing laboratory or qualified third-party
security assessor independent of the development team. Ask for a scoped
OWASP MASVS/MASTG-based Android assessment and cryptographic protocol review.
Obtain a written statement of work, scope, methodology, test devices, dates,
deliverables and permission to test. An OWASP-based test report is not an
official OWASP certification.

## 4. Provide the evidence package
Supply the frozen source/tag, reproducible build instructions, signed APK,
hashes, architecture and data-flow diagrams, cryptographic protocol specification,
threat model, API/permission inventory, data retention and deletion policy,
CI results, dependency inventory/SBOM, and full manual acceptance results.
Never email private signing keys, production vault passwords, or user secrets.

## 5. Testing and remediation
Assessor tests authentication, peer enrollment and revocation, code guessing,
replay, transfer limits, disk exhaustion, cancellation, tampering, restore
failure, Android Keystore, storage, device lifecycle, permissions, network
interception and supply chain. Engineering remediates findings; assessor
retests the exact release candidate and documents residual risks.

## 6. Issue legitimate attestation
The independent assessor, not the developer or ChatGPT, signs and dates
a report naming the exact build hash, tested version, methodology, findings,
remediation status, limitations, and assessment organization. Publish only
claims the assessor expressly supports. If formal ISO/IEC 27001 organizational
certification is desired, use an accredited certification body and separately
audit the organization's ISMS; a mobile penetration test is not ISO certification.

## 7. Release approval
After external clearance, repository owner configures GitHub environment
required reviewers and protected branch/tag rules, securely injects a dedicated
release signing key, runs production-release.yml, verifies the signature and
checksum, archives the assessor report, and signs the internal go/no-go decision.

## Non-negotiable product boundary
A personal Android app cannot bypass or reset arbitrary lock-screen credentials.
Managed-device token recovery requires eligible prior enrollment, device policy
authority, OS support and explicit provisioning. Do not advertise universal
device unlocking.
