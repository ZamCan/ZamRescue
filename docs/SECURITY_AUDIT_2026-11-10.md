# ZamRescue security audit — internal technical review

**Date:** 2026-11-10
**Scope:** main branch source through commit 0226a4a; Android application,
secure-protocol module, backup vault, peer enrollment, transfer, CI and signing.
**Assessor:** project engineering review, NOT an independent third party.
**Decision:** FAIL / production release blocked.

## Evidence verified
- GitHub Actions Android preview run 42568348363: success for 0226a4a.
- Previous authorization build run 42562802976: success for d850dac.
- Source inspection: StreamingRecoveryVault.kt, PeerEnrollmentRepository.kt,
  RecoveryGrantStore.kt, LivePeerSession.kt, AuthenticatedRescueSocket.kt,
  EncryptedBackupTransfer.kt, AndroidManifest.xml, and release workflow.
- No physical-device or penetration-test evidence provided.
- No signed production release artifact or external audit report provided.

## Findings

### ZR-SEC-001 — HIGH — Sender identity not bound to transfer approval
LivePeerSession.receiveArchive accepts any enrolled public key from a snapshot.
The six-digit code proves knowledge of a receiver-displayed secret but the
receiver does not choose or display the exact sending fingerprint at approval
time. A compromised enrolled peer knowing the code can send an archive.
**Required:** receiver chooses a specific peer fingerprint; handshake proves
that peer; authorize exactly one operation and dataset with a short-lived
grant. Re-check revocation before transfer acceptance.

### ZR-SEC-002 — HIGH — Grants are not enforced
RecoveryGrantStore is implemented and unit-tested but neither
LivePeerSession.receiveArchive nor sendArchive uses it.
**Required:** issue and consume scoped grants in the live transfer flow,
including cancellation, expiry, and negative tests.

### ZR-SEC-003 — HIGH — Security assessment not independent
No external cryptographic audit, mobile penetration test, signed attestation,
or remediation retest is recorded. An internal engineering review cannot
certify independence.

### ZR-SEC-004 — HIGH — Physical device acceptance absent
Automated JVM tests do not establish successful real Android Keystore,
SAF, lifecycle, network interruption, process death, and restore behavior.
**Required:** recorded device models, OS versions, APK hash, test cases,
logs and signed tester acceptance.

### ZR-SEC-005 — MEDIUM — Receiving storage exhaustion
The protocol caps transfers at 512 MiB but receiveArchive stages into
app cache without preflight free-space check or reserved per-app quota.
A valid authorized peer can consume remaining free storage.
**Required:** per-session quota, available-space check, cleanup of abandoned
cache archives and out-of-space tests.

### ZR-SEC-006 — MEDIUM — Code guessing and repeated sessions
A random six-digit approval code is used per receiver session. One wrong
attempt ends that listener, but there is no persistent failed-attempt
throttling across repeated sessions and no explicit user-visible identity
confirmation for the incoming sender.
**Required:** peer-bound approval, attempt budgets and rate-limiting.

### ZR-SEC-007 — MEDIUM — No signed release verification evidence
The production workflow requires secrets and an environment named
production. A workflow environment name alone does not prove repository
approval protections are enabled. No release APK signed with an owner-
controlled key has been verified.
**Required:** configure secrets and required reviewers; verify signed APK,
certificate fingerprint and SHA-256 digest; record release provenance.

### ZR-SEC-008 — MEDIUM — Vault decryption publication contract
StreamingRecoveryVault.decrypt writes plaintext incrementally and only
authenticates completion after the final frame. RecoveryScreen stages to a
temporary file, but any future caller writing directly to a published
destination could expose unauthenticated partial plaintext.
**Required:** enforce staged-output API or add explicit guardrail tests
and document no direct publication.

### ZR-SEC-009 — MEDIUM — No release dependency/SBOM assessment
No recorded software bill of materials, dependency vulnerability scan,
Android static-analysis report, or vulnerability triage is present.
**Required:** automated dependency review, SBOM and issue remediation.

## Positive controls
- Android private signing identity uses Android Keystore.
- Enrollment checks the entire peer fingerprint; revoked peers are omitted
  from fresh-session allowlists.
- The secure protocol authenticates enrolled peer keys and encrypts records.
- Backup vault uses password-based AES-GCM with authenticated final frame.
- Incoming files are not automatically decrypted or restored.
- AndroidManifest.xml disables Android OS backup for app-private data.
- A dedicated secret-injected signing workflow exists.

## Certification rule
Do not issue a production security certificate until HIGH findings are
closed with retest evidence, MEDIUM findings are dispositioned, a signed
release is reproducibly verified, and an independent assessor issues a
dated, signed report naming the exact release hash and assessment scope.

**This record is a completed internal source review, not a security
certification.**
