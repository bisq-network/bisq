# Bisq 1.10.8 Notable Changes

Bisq 1.10.8 is a security-focused release covering refund payouts, trade and transaction validation, DAO integrity, shutdown safety, and reproducible packaging.

## Refund Dispute and Payout Security

- Refund DPTs must spend the escrow output, and every refund receiver output is validated.
- Refund transaction provenance, finality, funding-chain binding, and available evidence are checked before payout.
- Payout reservations are durable and isolated; replayed receipts cannot create repeat payouts.
- Refund claimants use escrow-key proofs, and refund-agent authorization is required before closure.
- Payout limits are bounded by validated escrow state.

Related commits: [2400f036fd](https://github.com/bisq-network/bisq/commit/2400f036fd), [3cde93b6ab](https://github.com/bisq-network/bisq/commit/3cde93b6ab), [97fecc1115](https://github.com/bisq-network/bisq/commit/97fecc1115), [da9a105f01](https://github.com/bisq-network/bisq/commit/da9a105f01), [cc9ea0cfb1](https://github.com/bisq-network/bisq/commit/cc9ea0cfb1).

## Trade and Offer Validation

- Altcoin amounts respect registered asset precision and amounts that round to zero are rejected.
- Deposit liveness checks distinguish missing explorer evidence from a dead deposit.
- Withdrawals complete after transaction commit, and destructive offer flows stop when removal fails.
- Replaced mediation results cannot trigger obsolete actions.
- Unknown BTC fee receivers are rejected regardless of offer age.

Related commits: [12e50e7a91](https://github.com/bisq-network/bisq/commit/12e50e7a91), [cf9c4a5254](https://github.com/bisq-network/bisq/commit/cf9c4a5254), [d96dc039f9](https://github.com/bisq-network/bisq/commit/d96dc039f9), [08048aeb80](https://github.com/bisq-network/bisq/commit/08048aeb80), [393a5b7718](https://github.com/bisq-network/bisq/commit/393a5b7718), [7861e08285](https://github.com/bisq-network/bisq/commit/7861e08285).

## DAO and Burning Man Integrity

- Burning Man proposal inputs and address-list resources are validated and fail closed when obsolete or invalid.
- DAO financial readiness is revoked after checkpoint failure.
- BSQ swap signature release requires exact publication handoffs.

Related commits: [935eaca4d1](https://github.com/bisq-network/bisq/commit/935eaca4d1), [1e7fe3618f](https://github.com/bisq-network/bisq/commit/1e7fe3618f), [a6020c6e61](https://github.com/bisq-network/bisq/commit/a6020c6e61), [41a8474763](https://github.com/bisq-network/bisq/commit/41a8474763).

## Reliability and Build Reproducibility

- Shutdown sequencing is safer across external, network, Tor, broadcaster, desktop, and user-thread components.
- Store-membership checks avoid copying the entire store.
- Reproducible Debian packaging, Docker build support, Gradle configuration cache, and Gradle 9.0.0 support were added.
- Netlayer/Tor verification metadata and CI dependencies were updated.

Related commits: [06844d69ea](https://github.com/bisq-network/bisq/commit/06844d69ea), [10269f7485](https://github.com/bisq-network/bisq/commit/10269f7485), [1bbdb45f5d](https://github.com/bisq-network/bisq/commit/1bbdb45f5d), [8572d36de5](https://github.com/bisq-network/bisq/commit/8572d36de5), [406a4cc8f5](https://github.com/bisq-network/bisq/commit/406a4cc8f5).

## Version

The application and packaging version is `1.10.8`, set by [6975a3bf6f](https://github.com/bisq-network/bisq/commit/6975a3bf6f).

