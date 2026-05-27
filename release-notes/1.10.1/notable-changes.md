# Bisq 1.10.1 Notable Changes

This file filters the full release notes down to the most relevant engineering and product changes. Each section gives a short summary first and then lists representative related commits.

## BSQ Swap, Fees, And Trade Limits

This release fixes a BSQ swap take-offer crash by clamping the BTC miner contribution at zero. Fee updates are now clamped to the network minimum before they are used, and tests cover the low-fee BSQ swap verification path. Trade statistics validation was updated after the previous trade-limit reductions, and fiat trade limits were increased within the current `0.250 BTC` hard network cap. A new `docs/trade-limits.md` document explains how payment-method limits, local user limits, account signing, and maker/taker roles combine.

Related commits:
- [5a75a48785](https://github.com/bisq-network/bisq/commit/5a75a48785a2c97ff51593f5de60db906c20c3a0) - Fix BSQ swap take-offer crash by clamping BTC miner contribution at zero.
- [66cc2d85fe](https://github.com/bisq-network/bisq/commit/66cc2d85fe47a1a3326a6a0f03157a57a06609f6) - Clamp fee-service rates at the network minimum.
- [a13472b879](https://github.com/bisq-network/bisq/commit/a13472b879c133a8d1e91db224f785d9f372c37b) - Fix trade statistics validation after limit reductions.
- [75a6e9d996](https://github.com/bisq-network/bisq/commit/75a6e9d99624b58520c94ac2edf5e20aa7a729b5) - Increase fiat trade limits and add trade-limit documentation.
- [6fe6c9d645](https://github.com/bisq-network/bisq/commit/6fe6c9d645bf6cb793b5190d8d1b3e0b8dcf51bf) - Add BSQ swap fee-handling documentation.

## User Interface, Terms, And Preferences

Users can now configure the cold-storage reminder threshold in preferences. The wallet warning popup is suppressed in dev mode, trade rules and terms and conditions are linked from Settings/About, and the terms and conditions windows were refreshed. The date picker was migrated from `JFXDatePicker` to JavaFX `DatePicker`.

Related commits:
- [41c90a8ce0](https://github.com/bisq-network/bisq/commit/41c90a8ce00a6de99e6e9972f92f35a0d52f39fa) - Add `coldStorageReminderThreshold` to settings and update the text.
- [3805f229d7](https://github.com/bisq-network/bisq/commit/3805f229d7190cd2f6c0f3b38cfa2d71de5e480b) - Do not show the wallet warning popup in dev mode.
- [7fde548076](https://github.com/bisq-network/bisq/commit/7fde5480767071044fb7520d5aa92899023f96ad) - Add trade rules.
- [48e10731de](https://github.com/bisq-network/bisq/commit/48e10731de057443a62cfaa64c6c460b6ed72765) - Add trade rules and terms and conditions to Settings/About.
- [f64dbece84](https://github.com/bisq-network/bisq/commit/f64dbece849b2583a7527af99f7afaa809ffd0a6) - Update terms and conditions.
- [b112febcbf](https://github.com/bisq-network/bisq/commit/b112febcbf0bed06915e51fded6857058d51ca64) - Use JavaFX `DatePicker` instead of `JFXDatePicker`.

## API, Tests, And Packaging

Async offer and wallet failures are now reported through the gRPC observer, improving client-visible error handling. The API test suite continues moving onto the Docker stack, with legacy disabled tests ported and BSQ balance polling improved in trade scenarios. Debian package generation now handles `t64` dependencies more flexibly while preserving the original dependency constraint or qualifier on the unsuffixed alternative.

Related commits:
- [5a23970079](https://github.com/bisq-network/bisq/commit/5a23970079e0b4f35a937446131781f5bc66e613) - Surface async offer and wallet failures through the gRPC observer.
- [d56e8d2e4e](https://github.com/bisq-network/bisq/commit/d56e8d2e4e83f971cde5e8cb4a6af7a0e22211be) - Port legacy disabled API tests to the Docker stack.
- [37663453ed](https://github.com/bisq-network/bisq/commit/37663453ed61188b5056abe4b5f5db332263aae3) - Poll for BSQ balance changes in `TradeScenarioTest`.
- [1c3773adbd](https://github.com/bisq-network/bisq/commit/1c3773adbd07ffd5a7d52e59af4ad9fcdcc2a652) - Relax `t64` dependencies in generated Debian packages.
- [ae2c7a30cd](https://github.com/bisq-network/bisq/commit/ae2c7a30cd13b42993b84fc3eaa00ecc1a9744cc) - Preserve the original constraint or qualifier on the unsuffixed Debian dependency alternative.

## Release Process, CI, And Documentation

The release process gained Gradle release artifact signing tasks, guarded artifact directory listing, a stricter macOS `finalize.sh`, and clearer release-process command formatting. Reproducible build attestation documentation was added. GitHub Actions dependencies were updated for cache, upload-artifact, setup-buildx, and stale. The release is finalized by setting the app version to `1.10.1`.

Related commits:
- [b5283defd6](https://github.com/bisq-network/bisq/commit/b5283defd68ae3e9ad16e7252b5682c982a8630e) - Document the reproducible build attestation plan.
- [84ebd0c10d](https://github.com/bisq-network/bisq/commit/84ebd0c10d611bd1d634758503a508df3c022bd8) - Add Gradle release artifact signing tasks.
- [9f8d9a14d2](https://github.com/bisq-network/bisq/commit/9f8d9a14d2053947d0e20fb788538ee9651acc39) - Guard release artifact directory listing.
- [46cb115064](https://github.com/bisq-network/bisq/commit/46cb115064903377f51410ed188917238eac28e2) - Use an indented release-process command block.
- [94bfa327b1](https://github.com/bisq-network/bisq/commit/94bfa327b106b2f9a426fc779ffe0cae5155f4a9) - Fail `finalize.sh` on release signing errors.
- [f3d54a4311](https://github.com/bisq-network/bisq/commit/f3d54a431156ed550cb3402bad37103bf66911b5) - Bump `actions/cache` to `5.0.5`.
- [c02db280c2](https://github.com/bisq-network/bisq/commit/c02db280c2edf8e1df542104e38c40bc07157adf) - Bump `actions/upload-artifact` to `7.0.1`.
- [3da2168948](https://github.com/bisq-network/bisq/commit/3da2168948525e80193e9c376cf36c2d0c78ffa6) - Bump `docker/setup-buildx-action` to `4.0.0`.
- [ff489683a4](https://github.com/bisq-network/bisq/commit/ff489683a4a5c8be411179f7f8969552d371e984) - Bump `docker/setup-buildx-action` to `4.1.0`.
- [453a752748](https://github.com/bisq-network/bisq/commit/453a752748492c6ab1fbda002aa4cbc78e94ab4c) - Bump `actions/stale` to `10.3.0`.
- [75e8ca2d4a](https://github.com/bisq-network/bisq/commit/75e8ca2d4a98fbea606bf0a4ffdb45f9a4d04373) - Set version `1.10.1`.
