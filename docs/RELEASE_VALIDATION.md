# ZamRescue release validation checklist

## Automated gate
GitHub Actions runs :secure-protocol:test, :app:testDebugUnitTest and
:app:assembleDebug. A successful run establishes compilation and JVM test
success only. Save the run URL, commit SHA and artifact digest for every preview.

## Two-device acceptance procedure
1. Install the *same* debug build on two test phones; keep both unlocked and
   connected to a trusted Wi-Fi network.
2. Create identities on both phones. Exchange public keys and compare all 64
   fingerprint characters out-of-band before enrolling.
3. On receiver, open Peers > Live peer verification > Listen for peer.
4. On sender, select the enrolled receiver, enter its LAN IP and port 42761,
   and press Verify encrypted connection.
5. Expect success on both screens. Repeat with an unenrolled or revoked key:
   connection must be rejected.
6. In Recovery Vault, back up a test document; restore to a new filename.
   Compare the exact bytes, not just the filenames.
7. Test empty files, files above 32 MiB, incorrect passwords, truncated
   archives, and altered archives. Invalid restores must not publish plaintext.
8. Confirm V1 archives created by an older build can still be restored.
9. Repeat after app termination, phone reboot and network interruption.
10. Confirm battery usage, Android accessibility, lifecycle cleanup, and
    absence of background listeners without explicit user action.

## Not yet production-certified
- The live peer UI currently exchanges a signed/authenticated diagnostic probe,
  not backups. No remote filesystem access is granted.
- The operation grant store is not yet enforced by the Android peer transport.
- There is no automated multi-file backup catalog or resumable transfer.
- Android enterprise token-based lock recovery is not provisioned.
- No real-device test report or independent security assessment is recorded.
- No release signing key or Play Store production artifact is configured.

## Production signing
Never commit private signing keys, keystore passwords or recovery secrets.
Provision a dedicated release key through a secure secret manager; add a
separate manually approved release workflow with signed artifact verification,
versioned changelog and rollback procedure. Debug APKs are for testing only.
