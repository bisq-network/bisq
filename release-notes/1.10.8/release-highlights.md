# Bisq 1.10.8 Highlights

## Security

- Refund payouts require validated escrow evidence and authenticated claimant/agent authorization.
- Replayed refund receipts and cross-chain payout reservations are blocked.
- Refund transaction provenance, finality, receiver outputs, and transaction IDs are validated.
- Burning Man proposal inputs and address-list resources fail closed when invalid or obsolete.
- DAO readiness is revoked after checkpoint failure, and BSQ swap signatures require exact publication handoffs.

## Trading

- Altcoin amounts honor registered precision and zero-rounded amounts are rejected.
- Deposit liveness handling uses broadcast evidence and configured explorer scope.
- Withdrawals complete after commit, obsolete mediation results are ignored, and unknown BTC fee receivers are rejected.
- Security deposits remain editable down to the minimum floor.

## Reliability and Builds

- Shutdown sequencing is safer across JavaFX, network, Tor, broadcaster, and desktop components.
- Store checks avoid copying the full store.
- Reproducible Debian packaging, Docker builds, Gradle configuration cache, and Gradle 9.0.0 support are included.
- The application and packaging version is `1.10.8`.

