# Bisq 1.10.5 Notable Changes

Bisq 1.10.5 is a security-focused integration release covering DAO and P2P integrity, witness and bridge authentication, bonded-role validation, trade settlement, dispute messaging, and operational reliability.

## DAO and Hash Integrity

- Canonical encoding is used for DAO, P2P, protected-storage, offer, payment-account, mailbox, dispute, filter, blind-vote, and merit hash/signature paths.
- DAO block signatures, trusted providers, lite-node verification, state checkpoints, and payload/OP_RETURN hash checks are strengthened.
- Bonded-role lockups and proposal bindings are validated against the correct lifecycle and collateral spend type.

Related commits: [53094c5d56](https://github.com/bisq-network/bisq/commit/53094c5d56), [b814f3743d](https://github.com/bisq-network/bisq/commit/b814f3743d), [df5c181159](https://github.com/bisq-network/bisq/commit/df5c181159), [2938343f13](https://github.com/bisq-network/bisq/commit/2938343f13).

## DAO Voting Safety

- A malformed decrypted vote with a duplicate proposal ID now drops only that voter instead of aborting the cycle.
- Majority matching retains every committed blind vote; malformed merit receives zero weight without erasing ballots or stake, and equal transaction IDs are ordered deterministically in reveal and result processing.
- Proposal fields are validated consistently during startup, live admission, ballot reconstruction, and proposal-state hashing, using one RESULT-height rule for the whole cycle.
- Persisted duplicate ballot objects are tolerated only for identical canonical proposals; conflicting proposals surface as local DAO-state corruption.
- The bundled release audit reproduces 947 stored blind votes and 935 successful reveals through height `963_120`. Later proposal and vote data must be audited through activation.

Related commits: [d5b07f628f](https://github.com/bisq-network/bisq/commit/d5b07f628f), [c4f72d633f](https://github.com/bisq-network/bisq/commit/c4f72d633f), [e4c9bcb626](https://github.com/bisq-network/bisq/commit/e4c9bcb626), [0b9b0732f8](https://github.com/bisq-network/bisq/commit/0b9b0732f8).

## Witness and Bridge Safety

- Account-age and signed-witness requests are bound to ownership proofs and qualifying trust chains.
- Witness signatures, dates, trade associations, and bridge ownership are validated before reputation is accepted.
- Seed-only data requests and BSQ block delivery enforce the expected seed origin and contiguous block history.

Related commits: [1a29f64353](https://github.com/bisq-network/bisq/commit/1a29f64353), [a14d69bbe6](https://github.com/bisq-network/bisq/commit/a14d69bbe6), [81ae22e7e5](https://github.com/bisq-network/bisq/commit/81ae22e7e5), [645087e247](https://github.com/bisq-network/bisq/commit/645087e247).

## Trade and Dispute Authentication

- Fiat settlement requires authoritative buyer payment-account validation and supports safe recovery across upgrades and restarts.
- Deposit and delayed-payout transactions are bound to the expected trade and inputs.
- Support and dispute senders are authenticated against the expected peer, trader, or dispute-agent keys before state mutation.
- Malformed zero-price offers and invalid network payload hashes are rejected.

Related commits: [e7bbfa7d9f](https://github.com/bisq-network/bisq/commit/e7bbfa7d9f), [925e6c5e68](https://github.com/bisq-network/bisq/commit/925e6c5e68), [4a26f5a37e](https://github.com/bisq-network/bisq/commit/4a26f5a37e), [a57db0307a](https://github.com/bisq-network/bisq/commit/a57db0307a).

## Reliability and Release Data

- Shutdown, RPC, memory, single-instance, mempool, and trade-statistics handling is more robust.
- JavaFX packaging, gRPC separation, QR generation, regtest tooling, CI actions, and Windows backup handling were updated.
- The application and packaging version is `1.10.5`; DAO and bundled resource files were refreshed for the integration release.

Related commits: [62cb6fcfc7](https://github.com/bisq-network/bisq/commit/62cb6fcfc7), [db7e7e5306](https://github.com/bisq-network/bisq/commit/db7e7e5306), [dd0561e186](https://github.com/bisq-network/bisq/commit/dd0561e186), [9942e5b056](https://github.com/bisq-network/bisq/commit/9942e5b056).

## Release Gate

Run the Bisq 1 release checklist, resource audits, builds, focused security tests, and signature verification before publication. Record explicit approval for each independently scheduled DAO rule and audit mainnet proposal and vote data after bundled height `963_120` through activation.
