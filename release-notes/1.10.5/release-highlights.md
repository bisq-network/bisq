# Bisq 1.10.5 Highlights

## DAO and Network Integrity

- Canonical hash/signature encodings reduce ambiguity across DAO and P2P payloads.
- DAO block signatures, trusted providers, lite-node checks, checkpoints, and payload hash commitments are strengthened.
- BSQ block bridge delivery requires contiguous state and recovers gaps.
- Malformed decrypted votes are isolated per voter, while malformed merit can no longer remove a majority-committed blind vote.
- Proposal validation is consistent across startup and live nodes and applies to the entire activating cycle.

## Reputation and Bonded Roles

- Account-age and signed-witness bridge validation is bound to ownership and trusted witness chains.
- Bonded-role registrations and lockups are checked against the correct proposal and collateral lifecycle.
- Merit issuance and Burning Man accounting receive additional integrity checks and refreshed release data.

## Trading and Messaging

- Fiat buyer-account validation, deposit/DPT binding, support-message authentication, and dispute-agent authentication are hardened.
- Malformed zero-price offers and invalid network payload hashes are rejected.
- Legacy arbitration handling is disabled.

## Reliability and Version

- Shutdown, single-instance, RPC, memory, mempool, and trade-statistics handling is more robust.
- Packaging, JavaFX runtime, QR, gRPC, regtest, and Windows backup tooling were updated.
- The application and packaging version is `1.10.5`.
