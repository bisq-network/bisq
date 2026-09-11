# Bisq 1.10.8 Release Notes

These release notes cover the changes from commit `a692bfd584116221c71d8f7d3fff6ddb367a72d6` through `38db9110a6`. The later commit `4c2c01f02a` is intentionally excluded.

- Total commits: 162
- Non-merge commits: 115
- Merge commits: 47
- Files changed: 306
- Generated on: 2026-09-11

## Compatibility and Operator Notes

- The application and packaging version is `1.10.8`.
- This is a security-hardening release. Upgrade before relying on the updated refund, trade-validation, DAO, Burning Man, or shutdown paths.
- Refund payouts now depend on validated escrow evidence, authenticated claimant/agent authorization, durable payout reservations, and replay protection. Existing refund cases should be reviewed during upgrade and release validation.
- Altcoin trade amounts must respect the registered asset precision and must not round to zero.
- Unknown BTC fee receivers are rejected regardless of offer age.
- The release includes Gradle 9.0.0, reproducible Debian packaging, and configuration-cache/reproducible-build changes. Re-run the project’s release and packaging checks before publication.

## Notable Changes

### Refund Dispute and Payout Security

Refund delayed-payout transactions must spend the escrow output and all receiver outputs are validated. Refund transaction provenance and finality are checked, missing evidence fails closed, and non-canonical transaction IDs are rejected at dispute intake.

Refund payout reservations are durable and isolated by funding-chain evidence. Replayed receipts cannot create repeat payouts, reservations are rechecked before completion, and malformed historical receipt rows are handled safely. Refund claims are authenticated with escrow-key proofs, refund-agent authorization is required before case closure, and payout limits are bounded by validated escrow rather than untrusted delayed-payout output sums.

### Trade, Offer, and Payment Validation

Altcoin offer and trade amounts are rounded and validated against the asset registry precision; amounts that round to zero are rejected. Security deposits remain editable down to the configured minimum floor.

Deposit liveness checks can detect transactions unknown to all configured block explorers and require broadcast evidence before declaring a dead deposit. Explorer messages are scoped to configured providers, and stale trade-state listeners and verdicts are rechecked safely.

Withdrawals complete only after the transaction is committed, post-commit steps are guarded independently, and late broadcast failures are surfaced as notifications. Destructive offer flows abort when offer removal fails.

Mediation results that have been replaced can no longer trigger obsolete actions. Mempool request accounting is thread-safe, and unknown BTC fee receivers are rejected regardless of offer age.

### DAO and Burning Man Integrity

Burning Man proposal inputs are authenticated and obsolete or version-zero address-list resources fail closed. Address-list entries are no longer exposed as mutable state, reimbursement proposals are validated once per burn-target calculation, and the related proposal/resource tests were strengthened.

DAO financial readiness is revoked after checkpoint failure; failed DAO snapshots and delayed state-hash broadcasts are suppressed. BSQ swap signature release requires the exact expected publication handoffs.

### Reliability and Shutdown

External shutdown no longer depends on JavaFX initialization. Network and Tor shutdown is serialized, the broadcaster remains alive until shutdown work completes, desktop callbacks stop after shutdown handover, and shutdown completion is reported only once. User-thread configuration remains effective during shutdown and ignored configuration is logged.

Bitcoinj listener executors are resolved at dispatch time, and broadcast requests after broadcaster shutdown are ignored. Store-membership checks no longer copy the entire store, reducing memory and processing overhead.

### Build, Packaging, Dependencies, and Maintenance

A reproducible Debian packager and reproducible-build Docker workflow were added. Gradle configuration cache support was enabled, Gradle was updated to 9.0.0, and obsolete or broken coverage/reproducibility configuration was removed or disabled.

Netlayer/Tor dependency checksums and verification metadata were updated, deprecation migrations were completed, binary asset extensions were declared in `.gitattributes`, and CI action versions were refreshed.

## Tests and Documentation

The range adds or updates regression coverage for refund authorization and payout lifecycle, escrow and transaction validation, trade withdrawal and offer removal, deposit liveness, mempool accounting, DAO checkpoints and Burning Man resources, shutdown behavior, store membership, and reproducible/build tooling. Domain specifications were added or updated for refund validation, deposit liveness, withdrawal completion, offer edit/removal, mediation-result confirmation, DAO checkpoints, Burning Man behavior, and altcoin precision.

## Complete Commit Inventory

Rows are ordered by repository history over the requested range.

| Date | Commit | Type | Summary | Author |
| --- | --- | --- | --- | --- |
| 2026-08-10 | [99f89f2253](https://github.com/bisq-network/bisq/commit/99f89f2253d1d228c70733c88fdfec879bc3a104) | Commit | Keep security deposit editable at the min floor | fenlark |
| 2026-08-10 | [a9dd3a9afa](https://github.com/bisq-network/bisq/commit/a9dd3a9afa2b8cd55cffbc4eb9f6f72a3b748f66) | Commit | Show update popup only once per session | fenlark |
| 2026-08-09 | [d96dc039f9](https://github.com/bisq-network/bisq/commit/d96dc039f9e944aaed933befc673a128634cfd92) | Commit | Complete trade once withdraw tx is committed | fenlark |
| 2026-07-10 | [ff88a2f15f](https://github.com/bisq-network/bisq/commit/ff88a2f15f487bd540c177dfe5e88011fbddb456) | Commit | Round altcoin trade amounts to coin precision | fenlark |
| 2026-08-24 | [8009e9a590](https://github.com/bisq-network/bisq/commit/8009e9a590030e37afa0d2f3f7b3bb54f3c0af27) | Commit | Bump docker/setup-buildx-action from 4.2.0 to 4.3.0 | dependabot[bot] |
| 2026-08-11 | [46e32a44a9](https://github.com/bisq-network/bisq/commit/46e32a44a91be8a6843df56d294c2d4aa5752481) | Commit | Report sync P2P failures via offer book handlers | fenlark |
| 2026-08-10 | [dc4bb26f54](https://github.com/bisq-network/bisq/commit/dc4bb26f5458df65f170aa82cd593c1f71a4301a) | Commit | Accept foreign mobile numbers for SBP | fenlark |
| 2026-08-25 | [c61eca5870](https://github.com/bisq-network/bisq/commit/c61eca58701e844d0fd71b5a535f6116d6420c9e) | Commit | Suppress deprecation warnings for ArbitrationManager | Alva Swanson |
| 2026-08-25 | [5dc6635583](https://github.com/bisq-network/bisq/commit/5dc66355837cfb12fb03fcc3d628e59b9a50cab8) | Commit | Replace deprecated Charsets.UTF_8 with StandardCharsets.UTF_8 | Alva Swanson |
| 2026-08-25 | [2c969dc1f8](https://github.com/bisq-network/bisq/commit/2c969dc1f89d1c49ad8636571d23f8e3089429fe) | Commit | Replace StringUtils with Strings.CI for contains | Alva Swanson |
| 2026-08-25 | [f98c5dc6a0](https://github.com/bisq-network/bisq/commit/f98c5dc6a0fca3cb1366f2a1bb5c43754513727a) | Commit | Replace deprecated URL constructor with URI.toURL() | Alva Swanson |
| 2026-08-25 | [d4263e9b02](https://github.com/bisq-network/bisq/commit/d4263e9b02ab516853956be3cf65488e3f4b8069) | Commit | Update deprecated TableView resize policy | Alva Swanson |
| 2026-08-25 | [1565ebdd14](https://github.com/bisq-network/bisq/commit/1565ebdd14fc3a2af1554b713ab8993bdf4db6ab) | Commit | Replace StringUtils with Strings.CS for replaceOnce | Alva Swanson |
| 2026-08-25 | [5a52a2f4e3](https://github.com/bisq-network/bisq/commit/5a52a2f4e39f1e5c577c6e7cd8cde6e9f2074a83) | Commit | Replace StringUtils with Strings.CS for remove | Alva Swanson |
| 2026-08-25 | [aae27ab759](https://github.com/bisq-network/bisq/commit/aae27ab7597930c57f5783f6312b2a73e9595d6e) | Commit | Suppress deprecation warnings for Verse | Alva Swanson |
| 2026-08-25 | [98225e7124](https://github.com/bisq-network/bisq/commit/98225e712407503015c4feb21920803f4e589722) | Commit | common: Migrate to secure() RandomStringUtils API | Alva Swanson |
| 2026-08-26 | [a692bfd584](https://github.com/bisq-network/bisq/commit/a692bfd584116221c71d8f7d3fff6ddb367a72d6) | Commit | Set binary file extensions | HenrikJannsen |
| 2026-08-27 | [8ed0eed1a1](https://github.com/bisq-network/bisq/commit/8ed0eed1a175607f7b138adf1bf08d4fe5153b75) | Merge | Merge pull request #8035 from bisq-network/dependabot/github_actions/docker/setup-buildx-action-4.3.0 | HenrikJannsen |
| 2026-08-27 | [ee44545b78](https://github.com/bisq-network/bisq/commit/ee44545b78adbf5440bc4ebf3263250467c0a8df) | Merge | Merge pull request #8014 from fenlark/accept-foreign-numbers-for-sbp | HenrikJannsen |
| 2026-08-27 | [61e8eb6752](https://github.com/bisq-network/bisq/commit/61e8eb67520e2bde7470ffa5e13059fc5e2f4bfe) | Merge | Merge pull request #8012 from fenlark/show-update-popup-once-per-session | HenrikJannsen |
| 2026-08-27 | [9495c0d882](https://github.com/bisq-network/bisq/commit/9495c0d882dacac893f3e1294295d364c097be5f) | Merge | Merge pull request #8042 from HenrikJannsen/cherry-pick-1.10.7-commits-to-master | HenrikJannsen |
| 2026-08-27 | [8352e57a2b](https://github.com/bisq-network/bisq/commit/8352e57a2bda3fc30b9c56f8f67d4431e0ed2872) | Merge | Merge pull request #8016 from fenlark/keep-security-deposit-percent-editable | HenrikJannsen |
| 2026-08-27 | [f8d83e9d7e](https://github.com/bisq-network/bisq/commit/f8d83e9d7e777d791fa9ca51efbb4bf6058d9eff) | Merge | Merge pull request #8043 from alvasw/Suppress_deprecation_warnings_for_ArbitrationManager | Alva Swanson |
| 2026-08-27 | [87fccd1a23](https://github.com/bisq-network/bisq/commit/87fccd1a23763955ee1b4920a63b6921723ede34) | Merge | Merge pull request #8044 from alvasw/Replace_deprecated_Charsets.UTF_8_with_StandardCharsets.UTF_8 | Alva Swanson |
| 2026-08-27 | [c1d38403d8](https://github.com/bisq-network/bisq/commit/c1d38403d87ed9473d6403dc18b309a2328f1d6d) | Merge | Merge pull request #8045 from alvasw/Replace_deprecated_URL_constructor_with_URI.toURL | Alva Swanson |
| 2026-08-27 | [109ab19d4d](https://github.com/bisq-network/bisq/commit/109ab19d4da51725a9912b67d2a661451a79f9e1) | Merge | Merge pull request #8046 from alvasw/Update_deprecated_TableView_resize_policy | Alva Swanson |
| 2026-08-27 | [5f70ea5cab](https://github.com/bisq-network/bisq/commit/5f70ea5cab2b57c24a0589135e432606cdaf7419) | Merge | Merge pull request #8047 from alvasw/Replace_StringUtils_with_Strings.XX_counterpart | Alva Swanson |
| 2026-08-27 | [e89f5bdfb8](https://github.com/bisq-network/bisq/commit/e89f5bdfb8ac59d1ce8bd343581ffe1f66515df5) | Merge | Merge pull request #8048 from alvasw/Suppress_deprecation_warnings_for_Verse | Alva Swanson |
| 2026-08-27 | [6fa84f842e](https://github.com/bisq-network/bisq/commit/6fa84f842e7e13486bc3619c7543e4e3cf96353b) | Merge | Merge pull request #8049 from alvasw/common_Migrate_to_secure_RandomStringUtils_API | Alva Swanson |
| 2026-08-27 | [12e50e7a91](https://github.com/bisq-network/bisq/commit/12e50e7a91b0b5ee474ff603c277bf9b8e488885) | Commit | Reject altcoin volume that rounds to zero | fenlark |
| 2026-08-27 | [6eee5d4a30](https://github.com/bisq-network/bisq/commit/6eee5d4a3097c34385e74652a6a5ece3fac8b785) | Commit | Assert declared asset precisions from the registry | fenlark |
| 2026-08-27 | [025c7f542a](https://github.com/bisq-network/bisq/commit/025c7f542a4d669af07e256095cc3cb4faab2cdb) | Commit | Describe altcoin volume rounding in gRPC docs | fenlark |
| 2026-08-27 | [1976ad0434](https://github.com/bisq-network/bisq/commit/1976ad04347a414ecf05c41b9cd69c7c61c3c0c4) | Commit | Specify altcoin volume precision rules | fenlark |
| 2026-08-28 | [bab48b8d9c](https://github.com/bisq-network/bisq/commit/bab48b8d9cae5996eee1759f2eaf9fd80f4c6232) | Commit | Report a late broadcast failure as a notification | fenlark |
| 2026-08-28 | [8a0eb19afc](https://github.com/bisq-network/bisq/commit/8a0eb19afcd0913c4c2ad2947973fc4db5b60a19) | Commit | Specify withdrawal completion rules | fenlark |
| 2026-08-28 | [a18ab7b1c8](https://github.com/bisq-network/bisq/commit/a18ab7b1c82fe6531d8782c472ac2d1932d2b402) | Commit | Guard the post-commit steps independently | fenlark |
| 2026-08-28 | [7851de0778](https://github.com/bisq-network/bisq/commit/7851de0778ca1b46f314ce77a1abde02b07bdd82) | Commit | Print the committed tx as a post-commit step | fenlark |
| 2026-08-28 | [9f83be1c1a](https://github.com/bisq-network/bisq/commit/9f83be1c1a05ab79b18b1cfa78d540bc79ffb741) | Commit | Attach the broadcast callback before the memo | fenlark |
| 2026-08-28 | [f58b74c81d](https://github.com/bisq-network/bisq/commit/f58b74c81d8e369e997c92cc9ce88a25c192203d) | Commit | Extract the manual test timer for reuse | fenlark |
| 2026-08-28 | [08048aeb80](https://github.com/bisq-network/bisq/commit/08048aeb800f69aec4950736c9a8f0b765cbb549) | Commit | Abort destructive flows when offer removal failed | fenlark |
| 2026-08-28 | [7e400b7880](https://github.com/bisq-network/bisq/commit/7e400b7880ccb50405593157d60a395c3dd32a40) | Commit | Specify offer edit and removal contracts | fenlark |
| 2026-08-29 | [814da93dc8](https://github.com/bisq-network/bisq/commit/814da93dc8c0d563ab541062e6ef18f9904fdc0a) | Merge | Merge pull request #7960 from fenlark/respect-altcoin-precision-in-trade-amount | HenrikJannsen |
| 2026-08-29 | [58036deb67](https://github.com/bisq-network/bisq/commit/58036deb671aa914a11557f96becf84d3e848734) | Merge | Merge pull request #8021 from fenlark/fix-stuck-edit-mode-on-sync-p2p-exception | HenrikJannsen |
| 2026-08-29 | [6ef39f9e51](https://github.com/bisq-network/bisq/commit/6ef39f9e5152c35ea66b18d64220679101df5d9b) | Merge | Merge pull request #8011 from fenlark/complete-trade-on-committed-withdraw-tx | HenrikJannsen |
| 2026-08-09 | [cf9c4a5254](https://github.com/bisq-network/bisq/commit/cf9c4a5254b9ea015fe4ee9086ab541f53c60ae4) | Commit | Detect deposit tx unknown to all block explorers | fenlark |
| 2026-08-27 | [9b7ab55e11](https://github.com/bisq-network/bisq/commit/9b7ab55e11278ef87c4cd712e299f23095a9fefd) | Commit | Gate the dead-deposit verdict on broadcast evidence | fenlark |
| 2026-08-27 | [83b3561439](https://github.com/bisq-network/bisq/commit/83b3561439d4fc1651d2e09832b72b9a518bedf6) | Commit | Specify deposit transaction liveness rules | fenlark |
| 2026-08-28 | [adbd9c5566](https://github.com/bisq-network/bisq/commit/adbd9c55667f97ba6c70b93fb3f2d05dd3b34de8) | Commit | Scope explorer messages to configured providers | fenlark |
| 2026-08-28 | [22604731a8](https://github.com/bisq-network/bisq/commit/22604731a8a6f6de47f86fad7d6086c3423cdeee) | Commit | Describe the popup hours as trade age | fenlark |
| 2026-08-28 | [4cb15f9ce1](https://github.com/bisq-network/bisq/commit/4cb15f9ce17909be1c1ad7e04cae6c1ae00c560e) | Commit | Distinguish reserved inputs from locked funds | fenlark |
| 2026-08-29 | [5e671b5bba](https://github.com/bisq-network/bisq/commit/5e671b5bba5eaba766456824a03ab492a9ebbadc) | Commit | Drop the previous trade state listener on cell update | fenlark |
| 2026-08-29 | [b65b646017](https://github.com/bisq-network/bisq/commit/b65b6460175235b7c30dafdf35259278ed2cea20) | Commit | Re-check the verdict when the move is confirmed | fenlark |
| 2026-08-29 | [6975a3bf6f](https://github.com/bisq-network/bisq/commit/6975a3bf6f26c4dfc438aae76388b5e36606d240) | Commit | Set version 1.10.8 | HenrikJannsen |
| 2026-08-29 | [755f149c3b](https://github.com/bisq-network/bisq/commit/755f149c3bd545a2622f5faf180d3f4c6a0af09e) | Merge | Merge pull request #8051 from HenrikJannsen/set-version-1.10.8 | HenrikJannsen |
| 2026-08-29 | [d14bdaaed7](https://github.com/bisq-network/bisq/commit/d14bdaaed7713908aa165fcdcccb94950f8124bc) | Merge | Merge pull request #8009 from fenlark/detect-dead-deposit-tx | HenrikJannsen |
| 2026-08-29 | [6196d8942c](https://github.com/bisq-network/bisq/commit/6196d8942c9cf69f10a41f5628e7200d4371a349) | Commit | Replace deprecated Charsets.UTF_8 with StandardCharsets.UTF_8 | Alva Swanson |
| 2026-08-29 | [f31765b9d5](https://github.com/bisq-network/bisq/commit/f31765b9d5557754ba229bf4f363394772e0599d) | Commit | Suppress deprecation warnings for ArbitrationManager | Alva Swanson |
| 2026-08-29 | [f552b0f36a](https://github.com/bisq-network/bisq/commit/f552b0f36a4207f713804d987ccad1c95d095f99) | Commit | Suppress legacy DaoState serialization warnings | Alva Swanson |
| 2026-08-29 | [f6e294972d](https://github.com/bisq-network/bisq/commit/f6e294972d1ad62881b7a1b31b0a3f5c9dcdf9a3) | Commit | Suppress deprecation warnings for Venmo | Alva Swanson |
| 2026-08-29 | [314e8d34e6](https://github.com/bisq-network/bisq/commit/314e8d34e64050a0e2cfec785eb2f48da132f54e) | Commit | Replace Locale constructor call with Locale.of | Alva Swanson |
| 2026-08-29 | [dc89aa5c0a](https://github.com/bisq-network/bisq/commit/dc89aa5c0a88401b6883967e831f6458a7014aab) | Merge | Merge pull request #8052 from alvasw/Replace_deprecated_Charsets.UTF_8_with_StandardCharsets.UTF_8 | Alva Swanson |
| 2026-08-29 | [131746a3f6](https://github.com/bisq-network/bisq/commit/131746a3f60848085c3dc089fa2ebd5f3969ca40) | Merge | Merge pull request #8053 from alvasw/Suppress_deprecation_warnings_for_ArbitrationManager | Alva Swanson |
| 2026-08-29 | [23a5d93715](https://github.com/bisq-network/bisq/commit/23a5d93715f4c8c7f34ce731788a0cf13e951390) | Merge | Merge pull request #8054 from alvasw/Suppress_legacy_DaoState_serialization_warnings | Alva Swanson |
| 2026-08-29 | [d7a25bcf2b](https://github.com/bisq-network/bisq/commit/d7a25bcf2bad15a1dd7dc24a43d6d32040e1a384) | Merge | Merge pull request #8055 from alvasw/Suppress_deprecation_warnings_for_Venmo | Alva Swanson |
| 2026-08-29 | [f27eda4a60](https://github.com/bisq-network/bisq/commit/f27eda4a60b412060d62034521c8eed78ec7f697) | Merge | Merge pull request #8056 from alvasw/Replace_Locale_constructor_call_with_Locale.of | Alva Swanson |
| 2026-08-31 | [01f509001c](https://github.com/bisq-network/bisq/commit/01f509001c2268b853a5e46d462d36b5e9da56bd) | Commit | core: Fix Charsets to StandardCharsets migration | Alva Swanson |
| 2026-08-31 | [3875ed43a9](https://github.com/bisq-network/bisq/commit/3875ed43a9a6b35982b9042d22501b4fab194e5e) | Merge | Merge pull request #8058 from alvasw/core_Fix_Charsets_to_StandardCharsets_migration | Alva Swanson |
| 2026-08-27 | [2400f036fd](https://github.com/bisq-network/bisq/commit/2400f036fd094a60aef869679d8e8f5e504f4215) | Commit | Prevent replayed refund receipts from creating repeat payouts | HenrikJannsen |
| 2026-08-29 | [4d5d5bad22](https://github.com/bisq-network/bisq/commit/4d5d5bad22f78eb24a514dec9f77b47b892f48b1) | Commit | Reject non-canonical transaction IDs at dispute intake | HenrikJannsen |
| 2026-08-29 | [906233c649](https://github.com/bisq-network/bisq/commit/906233c6493fa241e653d71510e60caa8eeb3db3) | Commit | Handle malformed historical refund receipt rows safely | HenrikJannsen |
| 2026-08-29 | [457a3fd2d1](https://github.com/bisq-network/bisq/commit/457a3fd2d18d73264681188db69345c75c0cabb3) | Commit | Allow zero-payout closure of malformed refund tickets | HenrikJannsen |
| 2026-08-29 | [f5848f040a](https://github.com/bisq-network/bisq/commit/f5848f040a54255ebaf49c2599f72c658e401e35) | Commit | Prevent refund payout markers from crossing funding chains | HenrikJannsen |
| 2026-08-29 | [c2fbf01f40](https://github.com/bisq-network/bisq/commit/c2fbf01f407859a4f54066de4dee9ff1153dff8f) | Commit | Make refund payout reservations durable and isolated | HenrikJannsen |
| 2026-08-27 | [3cde93b6ab](https://github.com/bisq-network/bisq/commit/3cde93b6ab7c605fc1290a6d833c3616f3e99264) | Commit | Require refund DPT to spend the escrow output | HenrikJannsen |
| 2026-08-29 | [2d377a01be](https://github.com/bisq-network/bisq/commit/2d377a01be3225f4cefdbbdde6a2b9b58ff24943) | Commit | Refactoring: Construct RefundManager tests with dependencies | HenrikJannsen |
| 2026-08-29 | [d9bb378d60](https://github.com/bisq-network/bisq/commit/d9bb378d60e3ec0f3b72bd301a41afd06f5580e1) | Commit | Validate all refund DPT receiver outputs | HenrikJannsen |
| 2026-08-29 | [c9d78bce29](https://github.com/bisq-network/bisq/commit/c9d78bce298693dc7b914c8f3d6761b2c24f5e28) | Commit | Bind refund authorization to validated escrow state | HenrikJannsen |
| 2026-08-29 | [b20e90e50f](https://github.com/bisq-network/bisq/commit/b20e90e50f3c9b5409f78a06d75b704c99d531f6) | Commit | Verify refund transaction provenance and finality | HenrikJannsen |
| 2026-08-29 | [6f31226f16](https://github.com/bisq-network/bisq/commit/6f31226f16d62f49d07685bc6d6cd54c755382cb) | Commit | Fail closed when refund evidence is unavailable | HenrikJannsen |
| 2026-08-31 | [5d43fad2a0](https://github.com/bisq-network/bisq/commit/5d43fad2a0f32a970874b3b1ccf2b93ef9acee25) | Merge | Merge security/17 refund payout hardening | HenrikJannsen |
| 2026-09-02 | [2613c5c7c3](https://github.com/bisq-network/bisq/commit/2613c5c7c3a33ea78b4ec29fb7d447e3cf696b16) | Commit | Update to netlayer version 4433f8f4a9d4b86ade908bf7ca627ccc1036c1db (v0.7.8) which uses tor binary version 0.4.9.11 | HenrikJannsen |
| 2026-09-02 | [45c715d001](https://github.com/bisq-network/bisq/commit/45c715d001b3eb5eaf8e19e26cd16590db2dd364) | Merge | Merge pull request #103 from bisq-network/Update-tor-to-v0.4.9.11 | HenrikJannsen |
| 2026-09-02 | [e6d50c8cf1](https://github.com/bisq-network/bisq/commit/e6d50c8cf181b1ae587a273b81366c427eace23d) | Commit | Update BundledDaoStateAuditTest | HenrikJannsen |
| 2026-09-02 | [516cbe0f95](https://github.com/bisq-network/bisq/commit/516cbe0f95a9ca46c976a8c207998114704fca6e) | Merge | Merge pull request #104 from bisq-network/Update-BundledDaoStateAuditTest | HenrikJannsen |
| 2026-09-02 | [590503cf9f](https://github.com/bisq-network/bisq/commit/590503cf9f82bd2eb5e6140206be85563edb6927) | Commit | Approve checksum fallbacks for Tor dependencies | HenrikJannsen |
| 2026-09-02 | [af8f6a2f7f](https://github.com/bisq-network/bisq/commit/af8f6a2f7f89d1395b6560abf222e83839c61e4f) | Merge | Merge pull request #106 from bisq-network/Approve-checksum-fallbacks-for-Tor-dependencies | HenrikJannsen |
| 2026-09-02 | [a9dbe89705](https://github.com/bisq-network/bisq/commit/a9dbe89705c26d08ca4eb93a10ae3417b2a5f909) | Commit | Suppress deprecation for RawTransactionInput constructor | Alva Swanson |
| 2026-09-02 | [7d55021439](https://github.com/bisq-network/bisq/commit/7d550214390190bcbb68e020a5b09b312afff1e4) | Commit | Suppress deprecation warnings for Arbitrator | Alva Swanson |
| 2026-09-02 | [ce429c9b1c](https://github.com/bisq-network/bisq/commit/ce429c9b1c1777f7839809012db816958ec03ef8) | Commit | Replace Gradle property setters with assignment operator | Alva Swanson |
| 2026-09-02 | [83fa2625e6](https://github.com/bisq-network/bisq/commit/83fa2625e63c78fef68efafef2a4cde7b0585d75) | Commit | Remove broken JaCoCo coverage aggregator | Alva Swanson |
| 2026-09-02 | [c37df496db](https://github.com/bisq-network/bisq/commit/c37df496db520d872e39c7fb39963f1deda664e0) | Merge | Merge pull request #107 from bisq-network/Suppress_deprecation_for_RawTransactionInput_constructor | Alva Swanson |
| 2026-09-02 | [e8bedeec39](https://github.com/bisq-network/bisq/commit/e8bedeec39f47df35e4e08aaf325be61bea59426) | Merge | Merge pull request #108 from bisq-network/Suppress_deprecation_warnings_for_Arbitrator | Alva Swanson |
| 2026-09-02 | [28036461a5](https://github.com/bisq-network/bisq/commit/28036461a55498a3808f34a082d92acd2c8226f1) | Merge | Merge pull request #109 from bisq-network/Replace_Gradle_property_setters_with_assignment_operator | Alva Swanson |
| 2026-09-02 | [914849d344](https://github.com/bisq-network/bisq/commit/914849d3447b9f09c643cd51b19f2eafb1aae3e0) | Merge | Merge pull request #110 from bisq-network/Remove_broken_JaCoCo_coverage_aggregator | Alva Swanson |
| 2026-09-03 | [935eaca4d1](https://github.com/bisq-network/bisq/commit/935eaca4d1322fed9c0324c2abe917c266158599) | Commit | Authenticate Burning Man proposal inputs | HenrikJannsen |
| 2026-09-03 | [1e7fe3618f](https://github.com/bisq-network/bisq/commit/1e7fe3618f0b3fb4168747b37952f553e5d9d4c7) | Commit | Fail closed on obsolete Burning Man address lists | HenrikJannsen |
| 2026-09-03 | [09adac4c32](https://github.com/bisq-network/bisq/commit/09adac4c3252967465560f0d37a1f886c0dd42a3) | Commit | Fix refund address-list regression test | HenrikJannsen |
| 2026-09-03 | [06844d69ea](https://github.com/bisq-network/bisq/commit/06844d69ea1e0ef166ef711d9b50abbb1fe5ba36) | Commit | Make external shutdown independent from JavaFX | HenrikJannsen |
| 2026-09-03 | [df16148d93](https://github.com/bisq-network/bisq/commit/df16148d9348442eb860bf5a7a97b111fe17c740) | Commit | Serialize network and Tor shutdown | HenrikJannsen |
| 2026-09-03 | [343ddd6ea4](https://github.com/bisq-network/bisq/commit/343ddd6ea492055e04ee053b018e2c7ba0264ba4) | Commit | Keep broadcaster alive through shutdown | HenrikJannsen |
| 2026-09-03 | [95a906c591](https://github.com/bisq-network/bisq/commit/95a906c591da35ebb8c6e25a6d7ef278db98dd96) | Commit | Stop desktop presentation callbacks after shutdown handover | HenrikJannsen |
| 2026-09-03 | [7245a2e342](https://github.com/bisq-network/bisq/commit/7245a2e3429409c1c8a546116b60396d77d4f49f) | Commit | Fix typo | HenrikJannsen |
| 2026-09-03 | [ea5273a3fa](https://github.com/bisq-network/bisq/commit/ea5273a3fa2daf82d052247179d412737c508382) | Merge | Merge pull request #105 from bisq-network/fix-issue-at-shutdown | HenrikJannsen |
| 2026-09-03 | [c2ba30b2f4](https://github.com/bisq-network/bisq/commit/c2ba30b2f463fefd16c4f8d948a81921f11c61d6) | Merge | Merge pull request #102 from bisq-network/merged-8-with-17 | HenrikJannsen |
| 2026-09-04 | [33988ee468](https://github.com/bisq-network/bisq/commit/33988ee4680d989aef5ad27802883998256168e7) | Commit | Validate reimbursement proposals once per burn-target calculation | HenrikJannsen |
| 2026-09-04 | [a569c08658](https://github.com/bisq-network/bisq/commit/a569c08658fdfc2fd29fb41b15cce750681c479f) | Commit | Reject version-zero Burning Man address-list resources | HenrikJannsen |
| 2026-09-04 | [5fccc4c625](https://github.com/bisq-network/bisq/commit/5fccc4c62549693b4e6042cc2e89e2541c4a369c) | Commit | Stop exposing mutable Burning Man address-list entries | HenrikJannsen |
| 2026-09-04 | [e063c2b340](https://github.com/bisq-network/bisq/commit/e063c2b340eeaa49804a8f3c4143f8829dbb6a81) | Commit | Strengthen Burning Man proposal authentication tests | HenrikJannsen |
| 2026-09-04 | [d4213fa19c](https://github.com/bisq-network/bisq/commit/d4213fa19c34fdab40dc86efc5640cf88d967255) | Commit | Make mempool request accounting thread-safe | HenrikJannsen |
| 2026-09-04 | [27cf3c85db](https://github.com/bisq-network/bisq/commit/27cf3c85db9ef58ce0ac9c06f00d9bcc73ec510b) | Commit | Keep shutdown-safe UserThread configuration active | HenrikJannsen |
| 2026-09-04 | [3fc091e934](https://github.com/bisq-network/bisq/commit/3fc091e93456db3acf9f942e508916ed3dc5f81b) | Commit | Require verified trader contract signatures on refund disputes | HenrikJannsen |
| 2026-09-04 | [cc9ea0cfb1](https://github.com/bisq-network/bisq/commit/cc9ea0cfb11038d2693b90c7d25adf50aabb93c1) | Commit | Bound refund payouts by the validated escrow instead of the DPT output sum | HenrikJannsen |
| 2026-09-04 | [62a2f5b866](https://github.com/bisq-network/bisq/commit/62a2f5b866db3a4bc398c0e67ca20b0a1aaac0ab) | Commit | Resolve the bitcoinj listener executor at dispatch time | HenrikJannsen |
| 2026-09-04 | [b59ae0438f](https://github.com/bisq-network/bisq/commit/b59ae0438f42b4e67ee3decbe2216d5d958a0b18) | Commit | Report malformed refund transaction IDs as failed futures | HenrikJannsen |
| 2026-09-04 | [3f98091803](https://github.com/bisq-network/bisq/commit/3f98091803117bbb009bd6b91331877025ee545b) | Commit | Skip refund evidence validation on regtest | HenrikJannsen |
| 2026-09-05 | [6dc3a091b2](https://github.com/bisq-network/bisq/commit/6dc3a091b2277e1ac958cad82d9c1d6708019d61) | Commit | Test mempool request failure accounting directly | HenrikJannsen |
| 2026-09-05 | [8173cea45b](https://github.com/bisq-network/bisq/commit/8173cea45bfb3e53ce6654fda94d793d79a19a28) | Commit | Accept refund disputes without peer contract signatures | HenrikJannsen |
| 2026-09-05 | [103614ff2d](https://github.com/bisq-network/bisq/commit/103614ff2db76ec08c8b80ebfd5750694076a5f7) | Merge | Merge pull request #111 from bisq-network/various-improvements-and-hardening | HenrikJannsen |
| 2026-09-05 | [97fecc1115](https://github.com/bisq-network/bisq/commit/97fecc111560f936e5d3774a4077c31b4b598d3e) | Commit | Authenticate refund claimants with escrow-key proofs | HenrikJannsen |
| 2026-09-05 | [bb2a51ab30](https://github.com/bisq-network/bisq/commit/bb2a51ab3009b681f2d2d39213bfaa9b96bb20e1) | Commit | Expose authenticated refund claim submission to traders | HenrikJannsen |
| 2026-09-05 | [da9a105f01](https://github.com/bisq-network/bisq/commit/da9a105f017261460e46a443b0ec649e4daacbe6) | Commit | Enforce refund-agent authorization before closing cases | HenrikJannsen |
| 2026-09-05 | [39811228ca](https://github.com/bisq-network/bisq/commit/39811228ca5e82d207bfd5beaf3a5e5ddfbedf97) | Commit | Wait for the shutdown broadcast bundle before stopping its executor | HenrikJannsen |
| 2026-09-05 | [e075f1ff79](https://github.com/bisq-network/bisq/commit/e075f1ff7918b134ac33b152a90c367909c2439e) | Commit | Recheck refund authorization after durable payout reservation | HenrikJannsen |
| 2026-09-05 | [d8d22f3449](https://github.com/bisq-network/bisq/commit/d8d22f344960a28bfaca9e61d011461ddd80e5e3) | Commit | Report a consumed refund payout reservation when the recheck fails | HenrikJannsen |
| 2026-09-05 | [435da4805f](https://github.com/bisq-network/bisq/commit/435da4805f0172cb597ae6ada04d910a58d45618) | Commit | Clear a refund payout reservation whose persistence cannot be started | HenrikJannsen |
| 2026-09-05 | [7c63ca0b03](https://github.com/bisq-network/bisq/commit/7c63ca0b035c9851050d95aabbd09a24774a4671) | Commit | Offer no refund payout limit for rows without a positive trade fee | HenrikJannsen |
| 2026-09-05 | [b5ec6c9844](https://github.com/bisq-network/bisq/commit/b5ec6c9844f0bc19e6fd3b00d0d6f0a477d4698b) | Commit | Release the dead-deposit re-check marker when the lookup cannot start | HenrikJannsen |
| 2026-09-05 | [0101ab5af3](https://github.com/bisq-network/bisq/commit/0101ab5af39eb78b3ca4f983902058bcd98d6383) | Commit | Ignore broadcast requests after the broadcaster has shut down | HenrikJannsen |
| 2026-09-05 | [c9b9f9134d](https://github.com/bisq-network/bisq/commit/c9b9f9134d3d2edc24d39415efc0f58f3ed09f6c) | Commit | Report NetworkNode shutdown completion only once | HenrikJannsen |
| 2026-09-05 | [31039d3c74](https://github.com/bisq-network/bisq/commit/31039d3c74ddc606139b00ac49736e9148873660) | Commit | Log ignored UserThread configuration during JVM shutdown | HenrikJannsen |
| 2026-09-05 | [b220d46435](https://github.com/bisq-network/bisq/commit/b220d46435fd970d29a72da974ca6c20b08c3ca6) | Commit | Refactoring: remove unused Guava Charsets imports | HenrikJannsen |
| 2026-09-05 | [5b80bf8540](https://github.com/bisq-network/bisq/commit/5b80bf85407bc6894d5c3c65b1ac7c6bb0e0c303) | Commit | Align the URL validator regex with URI parsing | HenrikJannsen |
| 2026-09-05 | [bfc545aec4](https://github.com/bisq-network/bisq/commit/bfc545aec4b7c60edb6451dc39a8e923ce875455) | Commit | Clarify refund closing, admission and trade-flow specification wording | HenrikJannsen |
| 2026-09-05 | [be5d9f14a4](https://github.com/bisq-network/bisq/commit/be5d9f14a4f7008b4aa059270f6b80b8b91ddf03) | Commit | Comment out broken reproducible build code | Alva Swanson |
| 2026-09-05 | [8572d36de5](https://github.com/bisq-network/bisq/commit/8572d36de51b8ecda0250921b9caa9793cc908b4) | Commit | Enable Gradle's configuration cache to speed up builds | Alva Swanson |
| 2026-09-05 | [57f5b37373](https://github.com/bisq-network/bisq/commit/57f5b373737f4b6201188421e22e8e2fdbb8a902) | Commit | Apply org.gradlex.reproducible-builds to all modules | Alva Swanson |
| 2026-09-05 | [a41c12b91c](https://github.com/bisq-network/bisq/commit/a41c12b91cc40ab727b9ba1a0f1b7af83bb321af) | Commit | Discard refund manual approval when its binding changes | HenrikJannsen |
| 2026-09-05 | [58ddf0db3d](https://github.com/bisq-network/bisq/commit/58ddf0db3da9fe3b2693fdc74190793227861b53) | Commit | Report unknown status for refund payouts missing from the wallet | HenrikJannsen |
| 2026-09-05 | [b61868d8dc](https://github.com/bisq-network/bisq/commit/b61868d8dc7e9e76aeb639913162559b5d245718) | Merge | Merge pull request #113 from bisq-network/Comment_out_broken_reproducible_build_code | Alva Swanson |
| 2026-09-05 | [1d16ebba2c](https://github.com/bisq-network/bisq/commit/1d16ebba2c50b10e93b7b870376671b466d208af) | Merge | Merge pull request #114 from bisq-network/Enable_Gradle_s_configuration_cache_to_speed_up_builds | Alva Swanson |
| 2026-09-05 | [e00bba1d52](https://github.com/bisq-network/bisq/commit/e00bba1d52de1dddce4d56c008b6b8baf1d99eeb) | Merge | Merge pull request #112 from bisq-network/Authenticate-refund-claimants-with-escrow-key-proofs | HenrikJannsen |
| 2026-09-05 | [406a4cc8f5](https://github.com/bisq-network/bisq/commit/406a4cc8f5919b3a0920de7269ff6ada95e94449) | Commit | Update to Gradle 9.0.0 | Alva Swanson |
| 2026-09-05 | [c9b2e326ac](https://github.com/bisq-network/bisq/commit/c9b2e326ac200fca0717328d689f610dd2ea1c5a) | Merge | Merge pull request #115 from bisq-network/Update_to_Gradle_9.0.0 | Alva Swanson |
| 2026-09-05 | [8ce741761b](https://github.com/bisq-network/bisq/commit/8ce741761b044491903e233837028730c32742f7) | Merge | Merge pull request #116 from bisq-network/Apply_org.gradlex.reproducible-builds_to_all_modules | Alva Swanson |
| 2026-09-06 | [7861e08285](https://github.com/bisq-network/bisq/commit/7861e082851b345922f44ba6d4a8cae5eb536555) | Commit | Reject unknown BTC fee receivers regardless of offer age | HenrikJannsen |
| 2026-09-06 | [f12d8ab9c0](https://github.com/bisq-network/bisq/commit/f12d8ab9c0e2ca273c52cfbad336eb2f5b0fa5d5) | Merge | Merge pull request #123 from bisq-network/Reject-unknown-BTC-fee-receivers-regardless-of-offer-age | HenrikJannsen |
| 2026-09-06 | [393a5b7718](https://github.com/bisq-network/bisq/commit/393a5b7718e44b13b8a7273d6c2d1c07ab1b4c72) | Commit | Reject actions on replaced mediation results | HenrikJannsen |
| 2026-09-06 | [a6020c6e61](https://github.com/bisq-network/bisq/commit/a6020c6e619ed6ae920ce7b9fc624797af8aaa4c) | Commit | Revoke DAO financial readiness after checkpoint failure | HenrikJannsen |
| 2026-09-06 | [37415025fa](https://github.com/bisq-network/bisq/commit/37415025fa32912bf6a6dde1df6bce244aff162f) | Commit | Suppress failed DAO snapshots and delayed state-hash broadcasts | HenrikJannsen |
| 2026-09-06 | [41a8474763](https://github.com/bisq-network/bisq/commit/41a8474763fcc0a4695bf218ef9eb73a733736e2) | Commit | Guard swap signature release with exact publication handoffs | HenrikJannsen |
| 2026-09-06 | [65a3d3dea3](https://github.com/bisq-network/bisq/commit/65a3d3dea379b269bd98915abc6b3044f71ae809) | Merge | Merge pull request #127 from bisq-network/Apply-checkpoint-failure-to-DAO-readiness-state | HenrikJannsen |
| 2026-09-06 | [295dbd4b41](https://github.com/bisq-network/bisq/commit/295dbd4b41651eca8ec5314afcd84cbe969b2c49) | Merge | Merge pull request #126 from bisq-network/Reject-actions-on-replaced-mediation-results | HenrikJannsen |
| 2026-09-07 | [bd7eb01354](https://github.com/bisq-network/bisq/commit/bd7eb0135450b1d79725635fd49c34b682d7dc33) | Commit | Remove options.encoding from java-conventions | Alva Swanson |
| 2026-09-07 | [5c747891e5](https://github.com/bisq-network/bisq/commit/5c747891e507f1c7b3953cfc854aa68be73e9cdb) | Commit | Remove manual archive reproducibility from bisq-reproducible | Alva Swanson |
| 2026-09-07 | [f2b4f91aa2](https://github.com/bisq-network/bisq/commit/f2b4f91aa268f5d4a6bf906b4637787d59cca944) | Commit | Create Dockerfile for reproducible builds on all OSes | Alva Swanson |
| 2026-09-07 | [1bbdb45f5d](https://github.com/bisq-network/bisq/commit/1bbdb45f5d6c32465b390410d98fe52a99684320) | Commit | Implement reproducible deb packager | Alva Swanson |
| 2026-09-07 | [5d5f83f76e](https://github.com/bisq-network/bisq/commit/5d5f83f76eb05d0538c2d429c259f336bb62d5ec) | Merge | Merge pull request #128 from bisq-network/Remove_options.encoding_from_java-conventions | Alva Swanson |
| 2026-09-07 | [ac827018f0](https://github.com/bisq-network/bisq/commit/ac827018f0735b7fae842f3bc73bf4c4ab5d1b41) | Merge | Merge pull request #129 from bisq-network/Remove_manual_archive_reproducibility_from_bisq-reproducible | Alva Swanson |
| 2026-09-07 | [7713ac2af6](https://github.com/bisq-network/bisq/commit/7713ac2af6f1d5d48061589181aad01752406fc0) | Merge | Merge pull request #130 from bisq-network/Create_Dockerfile_for_reproducible_builds_on_all_OSes | Alva Swanson |
| 2026-09-07 | [5a1e7631fb](https://github.com/bisq-network/bisq/commit/5a1e7631fb3e432c3b042808456afa6ea2ad6d8e) | Merge | Merge pull request #131 from bisq-network/Implement_reproducible_deb_packager | Alva Swanson |
| 2026-09-10 | [10269f7485](https://github.com/bisq-network/bisq/commit/10269f7485e65c8189733474aaf5b8ca8ef4bc35) | Commit | Do not copy the whole store to test membership | fenlark |
| 2026-09-11 | [38db9110a6](https://github.com/bisq-network/bisq/commit/38db9110a635cdc220d65ec72643e134ca41ca04) | Merge | Merge pull request #145 from bisq-network/avoid-store-copy-on-payload-add | HenrikJannsen |

