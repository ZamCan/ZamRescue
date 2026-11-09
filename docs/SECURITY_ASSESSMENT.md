# ZamRescue security assessment record

Assessment type: **internal engineering review only**.
This document is not an independent security certification or production approval.

## Threat model and controls
- Network attackers: authenticated P-256 peer handshake and encrypted, replay-protected records.
- Unknown peers: enrolled identity-key allowlist; revoked identities excluded from new sessions.
- Unwanted transfers: receiver must explicitly start listening and disclose a fresh
  six-digit approval code; sender must supply it within the authenticated session.
- Modified transfers: encrypted records and SHA-256 end-to-end digest.
- Unauthorized restore: incoming archives remain encrypted and private; user must
  deliberately export or restore them with the vault password.
- Corrupted backups: restoration stages output privately before publishing.
- Excessive memory: bounded transfer records, streaming vault encryption.
- Network stalls: socket read and connection timeouts.

## Open security findings
1. HIGH: six-digit transfer approval is single-session, but there is no dedicated
   persistent operation grant, rate-limiting across repeated listener launches,
   or cryptographic binding to an explicitly displayed sender fingerprint.
2. HIGH: no independent penetration test, cryptographic audit, or signed report.
3. HIGH: no physical-device security testing across supported Android versions.
4. MEDIUM: incoming archives can occupy up to 4 GiB of private storage; no
   preflight free-space reservation, disk quota or durable cleanup policy.
5. MEDIUM: no resumable transfer, signed transfer manifest or application-level
   audit log; a network failure requires restarting.
6. MEDIUM: Android managed credential recovery is informational only, not provisioned.
7. MEDIUM: the production signing environment, private key, and approval protections
   are not configured or validated by a signed release run.
8. LOW: manual fingerprint comparison is required during enrollment; users can
   mistakenly trust the wrong peer.

## Independent certification acceptance criteria
An assessor organizationally independent of the implementation must review
the tagged source and reproducible build, examine the cryptographic protocol,
test negative authentication and transfer cases on physical Android devices,
review Android storage and backup semantics, assess key management and supply
chain dependencies, and issue a dated signed report with scope, findings,
remediations, and retest evidence. A CI pass or this document does not replace it.

## Release decision
**NOT APPROVED FOR PRODUCTION.** Do not advertise third-party certification,
lock-screen bypass, or universal device recovery. Reassess only after all HIGH
findings are resolved and a signed external assessment is received.
