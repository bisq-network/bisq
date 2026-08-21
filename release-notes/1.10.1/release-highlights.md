# Bisq 1.10.1 Highlights

Short text for GitHub release notes and the in-app update message.

## Trading And Wallet Safety

- BSQ swap take-offer handling now clamps the BTC miner contribution at zero, preventing a crash in edge cases where fee calculations would otherwise go negative.
- Fee updates are clamped to the Bitcoin network minimum, with added BSQ swap verification coverage around low-fee conditions.
- Trade statistics validation was adjusted for the reduced trade limits introduced in the previous release.
- Fiat trade limits were increased within the current `0.250 BTC` hard network cap, and the trade-limit rules are now documented in `docs/trade-limits.md`.

## UX And Settings

- The cold-storage reminder threshold is now configurable in preferences.
- The wallet warning popup is skipped in dev mode.
- Trade rules and terms and conditions are available from Settings/About.
- The terms and conditions flow was refreshed, and the date picker now uses JavaFX `DatePicker` instead of `JFXDatePicker`.

## API, Packaging, And Tests

- gRPC offer and wallet failures from async operations are now surfaced through the response observer instead of being lost in background handling.
- Legacy disabled API tests were ported to the Docker test stack, and BSQ balance polling in trade scenario tests was improved.
- Debian package dependency generation now relaxes `t64` dependencies while preserving the original constraint or qualifier on the unsuffixed alternative.

## Release Process And CI

- Release artifact signing tasks were added, release artifact directory listing is guarded, and `finalize.sh` now fails on signing errors.
- Reproducible build attestation documentation was added.
- GitHub Actions dependencies were updated for cache, upload-artifact, setup-buildx, and stale.
- The app version is `1.10.1`.
