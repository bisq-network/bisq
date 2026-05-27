# Bisq 1.10.1 Release Notes

These notes are written in the same practical style used by Bitcoin Core release notes: user and operator impact first, followed by a complete auditable commit inventory.

Commit range: after `a33ece9a915dc1eadb45e50960b8dc608a0f09a9` through `75e8ca2d4a98fbea606bf0a4ffdb45f9a4d04373`, generated with `git log --reverse a33ece9a915dc1eadb45e50960b8dc608a0f09a9..HEAD`. This excludes commit `a33ece9a91` and includes the release-version commit `75e8ca2d4a`. The inventory includes merge commits as separate entries because they are part of the release history.

- Total commits: 51
- Non-merge commits: 30
- Merge commits: 21
- Generated on: 2026-05-28

## Compatibility Notes

- Bisq version is set to `1.10.1`.
- Fiat trade limits were increased within the current hard network cap of `0.250 BTC`.
- BSQ swap fee handling clamps negative BTC miner contribution cases to zero and clamps fee updates to the network minimum.
- The cold-storage reminder threshold is now persisted as a preference and defaults to `0.2 BTC`, clamped up to `2 BTC`.
- Generated Debian package dependencies relax `t64` alternatives while preserving the original constraint or qualifier on the unsuffixed alternative.
- The release includes updated GitHub Actions versions for cache, upload-artifact, setup-buildx, and stale.

## Notable Changes

### Trading, Fees, And Limits

BSQ swap take-offer handling now avoids a crash by clamping the BTC miner contribution at zero. Fee-service updates are clamped to the Bitcoin network minimum, with new tests around low-fee BSQ swap verification. Trade statistics validation was adjusted after the previous limit reductions. Fiat trade limits were increased under the existing `0.250 BTC` hard cap, and the resulting limit behavior is documented in `docs/trade-limits.md`.

### User Interface And Settings

Users can configure the cold-storage reminder threshold in preferences. The wallet warning popup is skipped in dev mode. Trade rules and terms and conditions are available from Settings/About, with refreshed trade-rule and TAC windows. The date picker now uses JavaFX `DatePicker`.

### API, Test, And Packaging Fixes

Async offer and wallet failures now surface through the gRPC observer. Legacy disabled API tests were ported to the Docker stack, and BSQ balance polling in trade scenario tests was improved. Debian package generation now handles `t64` dependency variants more flexibly.

### Release Process And CI

Release artifact signing tasks were added, artifact directory listing is guarded, and macOS `finalize.sh` now fails on release signing errors. Reproducible build attestation documentation was added. GitHub Actions dependencies were updated, and the version was set to `1.10.1`.

## Complete Commit Inventory

Rows are ordered by `git log --reverse` over the release range.

| Date | Commit | Type | Summary | Author |
| --- | --- | --- | --- | --- |
| 2026-05-15 | [7c93f2ab71](https://github.com/bisq-network/bisq/commit/7c93f2ab712f2ceeb9dc105b3cf7cbb8fc605b3e) | Commit | Update release notes | HenrikJannsen |
| 2026-05-15 | [f189edf86f](https://github.com/bisq-network/bisq/commit/f189edf86f914131dfb899e4334a25026c816a2f) | Merge | Merge pull request #7813 from HenrikJannsen/update-release-notes | HenrikJannsen |
| 2026-05-15 | [b5283defd6](https://github.com/bisq-network/bisq/commit/b5283defd68ae3e9ad16e7252b5682c982a8630e) | Commit | Document reproducible build attestation plan | HenrikJannsen |
| 2026-05-16 | [5a23970079](https://github.com/bisq-network/bisq/commit/5a23970079e0b4f35a937446131781f5bc66e613) | Commit | Surface async offer/wallet failures through the gRPC observer | wodoro |
| 2026-05-17 | [84ebd0c10d](https://github.com/bisq-network/bisq/commit/84ebd0c10d611bd1d634758503a508df3c022bd8) | Commit | Add Gradle release artifact signing tasks | HenrikJannsen |
| 2026-05-17 | [47ed5cc40f](https://github.com/bisq-network/bisq/commit/47ed5cc40f0e9b9966731e48f8b03fff65cc1bcb) | Merge | Merge pull request #7814 from HenrikJannsen/add-attestations.md | HenrikJannsen |
| 2026-05-17 | [9f8d9a14d2](https://github.com/bisq-network/bisq/commit/9f8d9a14d2053947d0e20fb788538ee9651acc39) | Commit | Guard release artifact directory listing | HenrikJannsen |
| 2026-05-17 | [46cb115064](https://github.com/bisq-network/bisq/commit/46cb115064903377f51410ed188917238eac28e2) | Commit | Use indented release-process command block | HenrikJannsen |
| 2026-05-17 | [94bfa327b1](https://github.com/bisq-network/bisq/commit/94bfa327b106b2f9a426fc779ffe0cae5155f4a9) | Commit | Fail finalize.sh on release signing errors | HenrikJannsen |
| 2026-05-17 | [b112febcbf](https://github.com/bisq-network/bisq/commit/b112febcbf0bed06915e51fded6857058d51ca64) | Commit | Uses DatePicker instead of JFXDatePicker | HenrikJannsen |
| 2026-05-17 | [f38573a386](https://github.com/bisq-network/bisq/commit/f38573a3863452961fcc903ed9a7d1e5482668dd) | Commit | The release notes have been created from the commits after 1.9.23 (797836cc9d3ef0891a1bd349e18127fe252eb860) but as the 1.10.0 includes the 1.9.23 scope that was missing and added now. | HenrikJannsen |
| 2026-05-17 | [7265716790](https://github.com/bisq-network/bisq/commit/7265716790b21aba6b482f38c72e5e858b649bc1) | Merge | Merge pull request #7820 from HenrikJannsen/improve-release-process-tasks | HenrikJannsen |
| 2026-05-17 | [c12c5a8836](https://github.com/bisq-network/bisq/commit/c12c5a8836dd53f06811207b7b063a0c402d22fa) | Merge | Merge pull request #7821 from HenrikJannsen/Uses-DatePicker-instead-of-JFXDatePicker | HenrikJannsen |
| 2026-05-17 | [aa9426f554](https://github.com/bisq-network/bisq/commit/aa9426f55428ad75df1dfb29209f1fe7b617264c) | Merge | Merge pull request #7817 from wodoro/validate-grpc-contract-fix | HenrikJannsen |
| 2026-05-17 | [382c945ca9](https://github.com/bisq-network/bisq/commit/382c945ca98baee1fad3876e1b11e9e0c6cfa315) | Merge | Merge pull request #7822 from HenrikJannsen/update-release-notes-with-missing-commits-of-1.9.23 | HenrikJannsen |
| 2026-05-19 | [f3d54a4311](https://github.com/bisq-network/bisq/commit/f3d54a431156ed550cb3402bad37103bf66911b5) | Commit | Bump actions/cache from 4.3.0 to 5.0.5 | dependabot[bot] |
| 2026-05-19 | [c02db280c2](https://github.com/bisq-network/bisq/commit/c02db280c2edf8e1df542104e38c40bc07157adf) | Commit | Bump actions/upload-artifact from 4.6.2 to 7.0.1 | dependabot[bot] |
| 2026-05-19 | [3da2168948](https://github.com/bisq-network/bisq/commit/3da2168948525e80193e9c376cf36c2d0c78ffa6) | Commit | Bump docker/setup-buildx-action from 3.12.0 to 4.0.0 | dependabot[bot] |
| 2026-05-19 | [3107c7601c](https://github.com/bisq-network/bisq/commit/3107c7601cc57dbbe599b89a0450f33ffbda6616) | Merge | Merge pull request #7824 from bisq-network/dependabot/github_actions/actions/cache-5.0.5 | HenrikJannsen |
| 2026-05-19 | [3d852906d9](https://github.com/bisq-network/bisq/commit/3d852906d90d66874df70473215870fa73cb35e1) | Merge | Merge pull request #7825 from bisq-network/dependabot/github_actions/actions/upload-artifact-7.0.1 | HenrikJannsen |
| 2026-05-19 | [9c072483e2](https://github.com/bisq-network/bisq/commit/9c072483e2436c6e8b220bc0cf027eb98d2fa3f2) | Merge | Merge pull request #7826 from bisq-network/dependabot/github_actions/docker/setup-buildx-action-4.0.0 | HenrikJannsen |
| 2026-05-19 | [d56e8d2e4e](https://github.com/bisq-network/bisq/commit/d56e8d2e4e83f971cde5e8cb4a6af7a0e22211be) | Commit | test(apitest): port legacy disabled tests to docker stack | wodoro |
| 2026-05-19 | [1c3773adbd](https://github.com/bisq-network/bisq/commit/1c3773adbd07ffd5a7d52e59af4ad9fcdcc2a652) | Commit | Relax t64 dependencies in generated .deb | David Carrington |
| 2026-05-20 | [febd6b541a](https://github.com/bisq-network/bisq/commit/febd6b541af27526bc769bcc643f7b4340268e6f) | Merge | Merge pull request #7828 from wodoro/enable_old_tests | HenrikJannsen |
| 2026-05-20 | [ae2c7a30cd](https://github.com/bisq-network/bisq/commit/ae2c7a30cd13b42993b84fc3eaa00ecc1a9744cc) | Commit | Preserve the original constraint/qualifier on the unsuffixed alternative | David Carrington |
| 2026-05-21 | [a1337ae1cb](https://github.com/bisq-network/bisq/commit/a1337ae1cb014f4f576760b8db1c12398cef3f26) | Commit | Add temp directory to gitignore | HenrikJannsen |
| 2026-05-21 | [30b5f92e14](https://github.com/bisq-network/bisq/commit/30b5f92e1415ba5c451de4a58d5310a0002b024c) | Merge | Merge pull request #7831 from HenrikJannsen/update-gitignore | HenrikJannsen |
| 2026-05-21 | [6fe6c9d645](https://github.com/bisq-network/bisq/commit/6fe6c9d645bf6cb793b5190d8d1b3e0b8dcf51bf) | Commit | Add doc about BSQ swap fee handling | HenrikJannsen |
| 2026-05-21 | [8c0ac2ade2](https://github.com/bisq-network/bisq/commit/8c0ac2ade2eada7c4ca2f096825935d650e65670) | Merge | Merge pull request #7832 from HenrikJannsen/add-doc-about-BSQ-fee-handling | HenrikJannsen |
| 2026-05-21 | [5a75a48785](https://github.com/bisq-network/bisq/commit/5a75a48785a2c97ff51593f5de60db906c20c3a0) | Commit | Fix BSQ swap take-offer crash by clamping BTC miner contribution at 0 | wodoro |
| 2026-05-21 | [66cc2d85fe](https://github.com/bisq-network/bisq/commit/66cc2d85fe47a1a3326a6a0f03157a57a06609f6) | Commit | Clamp FeeService rates at the network minimum on updateFeeInfo | wodoro |
| 2026-05-21 | [37663453ed](https://github.com/bisq-network/bisq/commit/37663453ed61188b5056abe4b5f5db332263aae3) | Commit | test(apitest): poll for BSQ balance changes in TradeScenarioTest | wodoro |
| 2026-05-22 | [f64dbece84](https://github.com/bisq-network/bisq/commit/f64dbece849b2583a7527af99f7afaa809ffd0a6) | Commit | Update tac | HenrikJannsen |
| 2026-05-23 | [3805f229d7](https://github.com/bisq-network/bisq/commit/3805f229d7190cd2f6c0f3b38cfa2d71de5e480b) | Commit | Dont show wallet warning popup if in dev mode | HenrikJannsen |
| 2026-05-23 | [a13472b879](https://github.com/bisq-network/bisq/commit/a13472b879c133a8d1e91db224f785d9f372c37b) | Commit | Fix trade statistics validation after limit reductions | HenrikJannsen |
| 2026-05-23 | [03e2279c27](https://github.com/bisq-network/bisq/commit/03e2279c2726fa0efc389389e539b71b4ff7b0d0) | Merge | Merge pull request #7830 from wodoro/fix-bsq-swap-take-offer-crash | HenrikJannsen |
| 2026-05-23 | [5b1848486d](https://github.com/bisq-network/bisq/commit/5b1848486dd9e42d1bea32b11234da1e9fe5428d) | Merge | Merge pull request #7836 from HenrikJannsen/dont-show-wallet-warning-popup-if-devmode | HenrikJannsen |
| 2026-05-23 | [7e33ff9593](https://github.com/bisq-network/bisq/commit/7e33ff9593f554c806f6975242ef5672f54072e7) | Merge | Merge pull request #7837 from HenrikJannsen/Fix-trade-statistics-validation-after-limit-reductions | HenrikJannsen |
| 2026-05-25 | [ff489683a4](https://github.com/bisq-network/bisq/commit/ff489683a4a5c8be411179f7f8969552d371e984) | Commit | Bump docker/setup-buildx-action from 4.0.0 to 4.1.0 | dependabot[bot] |
| 2026-05-25 | [453a752748](https://github.com/bisq-network/bisq/commit/453a752748492c6ab1fbda002aa4cbc78e94ab4c) | Commit | Bump actions/stale from 10.2.0 to 10.3.0 | dependabot[bot] |
| 2026-05-27 | [75a6e9d996](https://github.com/bisq-network/bisq/commit/75a6e9d99624b58520c94ac2edf5e20aa7a729b5) | Commit | Increase fiat trade limits | KimStrand |
| 2026-05-23 | [7fde548076](https://github.com/bisq-network/bisq/commit/7fde5480767071044fb7520d5aa92899023f96ad) | Commit | Add trade rules | HenrikJannsen |
| 2026-05-27 | [48e10731de](https://github.com/bisq-network/bisq/commit/48e10731de057443a62cfaa64c6c460b6ed72765) | Commit | Add trade rules and tac to settings/about | HenrikJannsen |
| 2026-05-27 | [3bceb8010c](https://github.com/bisq-network/bisq/commit/3bceb8010c9fdb72263ff89206a28cf0bca0d46f) | Merge | Merge pull request #7843 from HenrikJannsen/update-tac | HenrikJannsen |
| 2026-05-27 | [773ce29804](https://github.com/bisq-network/bisq/commit/773ce298045e0d142c850bb768fc121354cb4725) | Merge | Merge pull request #7839 from bisq-network/dependabot/github_actions/actions/stale-10.3.0 | HenrikJannsen |
| 2026-05-27 | [672007c26c](https://github.com/bisq-network/bisq/commit/672007c26cdae31d4251feba4c17445911052310) | Merge | Merge pull request #7838 from bisq-network/dependabot/github_actions/docker/setup-buildx-action-4.1.0 | HenrikJannsen |
| 2026-05-27 | [41c90a8ce0](https://github.com/bisq-network/bisq/commit/41c90a8ce00a6de99e6e9972f92f35a0d52f39fa) | Commit | Add coldStorageReminderThreshold to settings Update text | HenrikJannsen |
| 2026-05-27 | [c5368ba3b1](https://github.com/bisq-network/bisq/commit/c5368ba3b1b6abc1252ca43bd4ba997d43acaa66) | Merge | Merge pull request #7844 from HenrikJannsen/improve-hot-wallet-warning | HenrikJannsen |
| 2026-05-27 | [fdd078dcfa](https://github.com/bisq-network/bisq/commit/fdd078dcfaedb31a96d669f448438601f1883d4c) | Merge | Merge pull request #7842 from KimStrand/increase-fiat-trade-limits | HenrikJannsen |
| 2026-05-27 | [a2099b0a79](https://github.com/bisq-network/bisq/commit/a2099b0a794351616198eeb364d40db7f8ec21fb) | Merge | Merge pull request #7829 from dmcarrington/bugfix/7819-deb-dependency | HenrikJannsen |
| 2026-05-27 | [75e8ca2d4a](https://github.com/bisq-network/bisq/commit/75e8ca2d4a98fbea606bf0a4ffdb45f9a4d04373) | Commit | Set version 1.10.1 | HenrikJannsen |
