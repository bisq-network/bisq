# Bisq 1.10.9 Notable Changes

Bisq 1.10.9 hardens BSQ swap validation and P2P/DAO state hash handling, and updates bundled DAO resources, the Burning Man address list, the Tor dependency, and build and release tooling.

## Trading and Payment Accounts

- The API rejects payment-started confirmation until the buyer has received the seller's payment account details.
- Revolut account details use the current “Revtag” label.
- Bank form validation runs before country-dependent fields are reset.
- Offer republish retries wait until P2P bootstrap is complete; all open offers are republished after bootstrap.

Related commits: [d19bf16771](https://github.com/bisq-network/bisq/commit/d19bf16771), [02d5f908b8](https://github.com/bisq-network/bisq/commit/02d5f908b8), [8eca917a15](https://github.com/bisq-network/bisq/commit/8eca917a15), [5ea8afc452](https://github.com/bisq-network/bisq/commit/5ea8afc452).

## Trusted BSQ Block Providers

- Trusted BSQ block providers are initialized once, even when no providers are configured, and callers receive an immutable collection.

Related commit: [dcf9916f17](https://github.com/bisq-network/bisq/commit/dcf9916f17).

## BSQ Swap Validation

- Seller inputs are bound to their transaction outpoints; BSQ outputs and duplicate buyer inputs are rejected.
- Seller input and buyer signatures are verified during the swap flow.
- Regression tests cover invalid inputs and signatures, including sibling spends of legacy BSQ swap inputs.

Related commits: [e3bce7cf8a](https://github.com/bisq-network/bisq/commit/e3bce7cf8a), [6568d18225](https://github.com/bisq-network/bisq/commit/6568d18225), [953f185e05](https://github.com/bisq-network/bisq/commit/953f185e05), [8dc198a772](https://github.com/bisq-network/bisq/commit/8dc198a772), [0787ced549](https://github.com/bisq-network/bisq/commit/0787ced549).

## P2P and DAO State Hash Security

- Backdate offer expiry only after outbound disconnects, and bind the dialed peer address before reading.
- Trust DAO seed state hashes only from outbound connections.

Related commits: [e03ea869c2](https://github.com/bisq-network/bisq/commit/e03ea869c2), [b604b38ae6](https://github.com/bisq-network/bisq/commit/b604b38ae6), [7ca7adb676](https://github.com/bisq-network/bisq/commit/7ca7adb676).

## Bundled Resources

- Mainnet DAO state and block resources are updated through height 970000.
- DAO state hash checkpoints are refreshed.
- The Burning Man v0006 address list and BTC mainnet denylist are updated.

Related commits: [13f6915b47](https://github.com/bisq-network/bisq/commit/13f6915b47), [d0e2f1c269](https://github.com/bisq-network/bisq/commit/d0e2f1c269), [40f25a606a](https://github.com/bisq-network/bisq/commit/40f25a606a), [f88c8aa430](https://github.com/bisq-network/bisq/commit/f88c8aa430), [4e2e013558](https://github.com/bisq-network/bisq/commit/4e2e013558), [2b937972cd](https://github.com/bisq-network/bisq/commit/2b937972cd), [383aae0b76](https://github.com/bisq-network/bisq/commit/383aae0b76).

## Tor and Dependency Verification

- Update netlayer to v0.7.11 (revision `3dd905665ed6120eb441c59203b5ceecad797a31`), which includes Tor 0.4.9.13.
- Update Gradle dependency verification metadata and the dependency signature report.

Related commits: [050f1cc085](https://github.com/bisq-network/bisq/commit/050f1cc085), [68783482a9](https://github.com/bisq-network/bisq/commit/68783482a9).

## Build and Release Tooling

- Pin release builds to Gradle 9.0.0 and improve configuration-cache support in verification and release tasks.
- Enable Gradle build caching and improve the reproducible Debian Docker build.
- Update CI actions and build documentation.

Related commits: [ed94df26a3](https://github.com/bisq-network/bisq/commit/ed94df26a3), [8040b67ee3](https://github.com/bisq-network/bisq/commit/8040b67ee3), [2d7770db49](https://github.com/bisq-network/bisq/commit/2d7770db49), [e4758054ad](https://github.com/bisq-network/bisq/commit/e4758054ad), [d80a83727e](https://github.com/bisq-network/bisq/commit/d80a83727e), [a8f16139d4](https://github.com/bisq-network/bisq/commit/a8f16139d4), [f2b4aef427](https://github.com/bisq-network/bisq/commit/f2b4aef427).

## Version

The application and packaging version is `1.10.9`, set by [4cf915501b](https://github.com/bisq-network/bisq/commit/4cf915501b).
