# ZamRescue — Recovery authority and product architecture

## Product promise
ZamRescue is a consent-based Android data-continuity and trusted-companion system.
It helps users preserve and restore accessible information before an incident, and
offers guided official recovery paths afterward. Never advertise bypass of Android
credentials or access to another app's private data.

## Authority model
- **Owner consent:** The owner chooses each source and destination with Android's
  Storage Access Framework; no broad storage permissions or silent collection.
- **Peer identity:** An enrolled public key proves *who* is connecting, not *what*
  that peer may do. Network discovery never implies trust.
- **Capabilities:** A distinct, revocable authorization must specify operation,
  dataset, expiration, target peer, and optional one-time use. Default deny.
- **Platform privilege:** Android device-owner/profile-owner authority is a
  separate OS-managed privilege. No app-level peer pairing confers it.
- **Locked state:** Do not assume background operation or access to
  credential-encrypted storage before the user unlocks after reboot. Document
  Android Direct Boot and key availability limitations.
- **Emergency handling:** No unlock bypass, credential harvesting, accessibility
  automation, silent installation, exploit chain, or misleading recovery promise.

## Official managed-device path
For supported Android Enterprise deployments, investigate Android
DevicePolicyManager APIs including resetPasswordWithToken and associated
pre-provisioning, activation, affiliation, API-level, and policy constraints.
Only expose such actions when the OS reports the app is an eligible device
owner/profile owner and the capability was established while the owner had
authorized access. Never promise this works on ordinary consumer devices.
Test on managed test hardware, not users' primary phones.

## Engine roadmap and acceptance gates
1. **Local Vault**: authenticated archive format, versioning, robust errors,
   chunked streaming for large datasets, atomic restoration, manifest integrity,
   encrypted metadata, and authenticated per-file content.
2. **Companion channel**: Android lifecycle-managed foreground socket sessions,
   authenticated NSD peer verification, short-lived operation-scoped grants,
   denial and revocation, replay prevention, session audit, disconnect controls.
3. **Backup orchestration**: user-selected collections, durable job state,
   resumable transfers, bounded disk use, retention, duplicate detection,
   verification after backup, and restoration dry runs.
4. **Readiness dashboard**: real state only, no simulated successful backups.
   Show last verified archive, last restore drill, companion reachability,
   key availability, and unresolved risks.
5. **Production gates**: protocol and vault JVM tests, malicious-input fuzzing,
   Android instrumented tests, two-device end-to-end trials, process-death and
   reboot recovery, battery/thermal checks, accessibility, data-loss scenarios,
   independent security review, and reproducible release signing.

## Honest current status (2026-11-08)
- Local single-file encrypted vault, 32 MiB cap: implemented as a preview.
- Cryptographic protocol and JVM TCP adapter: implemented; Android peer-session
  integration and permission grants: not implemented.
- UI: Compose shell with vault navigation; full premium redesign pending.
- Full-device restore, automatic backups, lock-screen unlock: not implemented.
- The passing GitHub Actions preview validates configured JVM tests and debug
  assembly, not production readiness.
