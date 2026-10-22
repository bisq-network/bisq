# Bisq 1.10.10 Release Notes

These release notes cover the changes after commit `a01e2f92650a86f7a0cd467f54702ac93ef9b123` through `HEAD` (`ee8570cdf5`).

- Total commits: 14
- Non-merge commits: 14
- Merge commits: 0
- Files changed: 42
- Generated on: 2026-10-22

## Compatibility and Operator Notes

- The application and packaging version is `1.10.10`.
- Bundled mainnet DAO data is updated through height 971800; DAO state hash checkpoints for heights 970000 and 971000 are added and the Burning Man address list v0007 is added. New installations and nodes using bundled data start from the updated resources.
- A node whose DAO state at height 969537 contains an evaluated proposal for the reimbursement request `364e7f91...`, which no voter accepted or rejected, does not match the checkpoint at 970000. Such a node removes its DAO data and resyncs from the bundled resources on startup.
- `TempProposalStore_BTC_MAINNET` is no longer shipped as a bundled resource. Temporary proposals have a TTL of 60 days and are received from the P2P network.
- The bundled netlayer dependency is updated to revision `74230355ff2aa65596fff0f25138045e36b6014e` (v0.7.12), which includes Tor 0.4.9.14.
- The release signing keys `387C8307` and `E222AA02` carry new validity dates. The unused and expired key `4A133008` is removed from the release assets and from in-app update verification.
- P2P, local database, and trade protocol version numbers remain unchanged.

## Notable Changes

### Bundled DAO and Network Resources

Bundled mainnet DAO state and BSQ block resources are refreshed from height 969240 to 971800, and the Burning Man accounting store now ends at height 971833. No proposal or blind-vote transaction was mined in the new range, so the proposal and blind-vote stores are unchanged. The new history includes the RESULT block 969537 of the cycle that started at height 964867.

DAO state hash checkpoints are added for heights 970000 and 971000. Replaying the bundled blocks from the previous bundled snapshot reproduces both hashes. The Burning Man address list v0007 (27 entries, selection height 971820) is added.

The bundled temporary proposal store is removed, together with its line in `copy_dbs.sh`. The release checklist now describes the proposal, temporary proposal, and blind-vote stores as always copied, matching the script.

### Bundled DAO Resource Audit

The opt-in bundled DAO resource audit is refreshed for height 971800. The expected revealed blind-vote count rises from 948 to 951, and the audit evidence in the DAO specifications (vote result validation, merit, proposal validation, bonded roles, bonds, bond lockup spends, and bonded reputation) now records the audit at height 971800. The audit no longer reads the temporary proposal store.

### Tor and Dependency Verification

The netlayer dependency is updated to v0.7.12, which includes Tor 0.4.9.14. Gradle dependency verification metadata, the checksum fallback allowlist, and the dependency signature report are updated for the new artifacts.

### Release Signing Keys

The public keys `387C8307` (Henrik Jannsen) and `E222AA02` (Alejandro García) are updated with new expiry dates, both in the packaged release assets and in the keys bundled for in-app update verification. Key `4A133008` was never used to sign a release and has expired. It is removed from the packaged assets, the macOS finalize script, the in-app installer key list, the release readiness check, and the release process documentation.

### Build Tooling

The GitHub Actions `actions/upload-artifact` is updated to 7.0.2 and `actions/setup-java` to 6.1.0.

## Tests and Documentation

`BundledDaoStateAuditTest` no longer checks role terms of temporary role proposals, since the temporary proposal store is no longer shipped; append-only proposals are still checked. Its pinned constants are updated for height 971800. The release checklist and the DAO specifications are updated accordingly. This release note summarizes source and resource changes; it does not claim that release builds or tests were run for this note.

## Commit Summary

| Commit | Summary |
| --- | --- |
| [7f349d585c](https://github.com/bisq-network/bisq/commit/7f349d585c) | Update 387C8307 gpg key with new validity date |
| [609b17d233](https://github.com/bisq-network/bisq/commit/609b17d233) | Update E222AA02 gpg key with new validity date |
| [5c45909b15](https://github.com/bisq-network/bisq/commit/5c45909b15) | Remove 4A133008 key |
| [e0742550cd](https://github.com/bisq-network/bisq/commit/e0742550cd) | Remove usage of 4A133008 key |
| [78f54d1ef0](https://github.com/bisq-network/bisq/commit/78f54d1ef0) | Update to tor-binary v0.4.9.14 (netlayer 0.7.12) |
| [fb1bd7abae](https://github.com/bisq-network/bisq/commit/fb1bd7abae) | Bump actions/upload-artifact from 7.0.1 to 7.0.2 |
| [23482d020a](https://github.com/bisq-network/bisq/commit/23482d020a) | Describe the proposal and blind-vote stores as always copied |
| [45ac94327b](https://github.com/bisq-network/bisq/commit/45ac94327b) | Stop shipping TempProposalStore_BTC_MAINNET |
| [cde48bdda6](https://github.com/bisq-network/bisq/commit/cde48bdda6) | Update bundled DAO resources to height 971800 |
| [3b5d4553d4](https://github.com/bisq-network/bisq/commit/3b5d4553d4) | Add Burning Man address list v0007 |
| [07d5aa1145](https://github.com/bisq-network/bisq/commit/07d5aa1145) | Add DAO state hash checkpoints for heights 970000 and 971000 |
| [c697f0d791](https://github.com/bisq-network/bisq/commit/c697f0d791) | Refresh the bundled DAO resource audit for height 971800 |
| [059dd84cab](https://github.com/bisq-network/bisq/commit/059dd84cab) | Set version 1.10.10 |
| [ee8570cdf5](https://github.com/bisq-network/bisq/commit/ee8570cdf5) | Bump actions/setup-java from 6.0.1 to 6.1.0 |
