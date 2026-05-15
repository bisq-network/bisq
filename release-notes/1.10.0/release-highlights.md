# Bisq 1.10.0 Highlights

Short text for GitHub release notes and the in-app update message.

## Security And Trade Safety

- Stronger checks before accepting trade messages, deposit transactions, payout transactions, and peer-provided wallet data.
- Safer offer taking: invalid peer data is rejected earlier, and failed take-offer attempts release the offer again.
- Additional protection around payment-account matching, payout addresses, trade fees, and trade limits.
- HTTP and P2P message handling now rejects more unsafe or unexpected network inputs.

## Trading And Payments

- Maximum trade amount is now `0.125 BTC`.
- Offers and trades are limited to a `25%` maximum price deviation.
- Swish account creation, payment-account loading, payout address checks, and BSQ wallet CSV export were fixed.
- HRK was removed from currency lists, and VERSE can no longer be added as a new payment method.

## UX And Support

- Disk-space warnings are shown only once.
- A cold-storage reminder is shown for high wallet balances.
- Chat scrolling, chat window sizing, update-text scrolling, and trader chat cleanup issues were fixed.
- Dispute chat attachments and dispute log file transfer were removed.

## Network And Runtime

- Tor/netlayer, bitcoinj, bitcoind, Java, JavaFX, and major libraries were updated.
- The app version is `1.10.0`; release builds use the Java 21 release build stack.
- Bundled DAO and network resource snapshots were refreshed for the release.
- macOS releases now support both Apple Silicon and Intel Macs.
- The in-app updater now selects the matching macOS Intel or Apple Silicon installer.
- macOS JavaFX/JFoenix packaging issues were fixed.

## Release Integrity

- Release builds now produce and verify payload and installer manifests.
- Dependency signatures, Gradle wrapper inputs, CI workflows, and release-builder images are more tightly verified.
- Docker-based DAO and trade end-to-end tests were added to GitHub Actions.
- A manual GitHub release-readiness task checks release assets, download URLs, and signer keys.
- CVE scanning and reproducible-build documentation were added.
