# Bisq 1.10.9 Release Notes

These release notes cover the changes after commit `f32c8523f13260611a348b54311fc335a20e806e` through `HEAD` (`46c4e4c5aa`).

- Total commits: 96
- Non-merge commits: 62
- Merge commits: 34
- Files changed: 113
- Generated on: 2026-09-30

## Compatibility and Operator Notes

- The application and packaging version is `1.10.9`.
- Bundled mainnet DAO data is updated through height 970000; state hash checkpoints and the Burning Man address list are refreshed. New installations and nodes using bundled data start from the updated resources.
- The bundled netlayer dependency is updated to revision `3dd905665ed6120eb441c59203b5ceecad797a31` (v0.7.11), which includes Tor 0.4.9.13.
- P2P, local database, and trade protocol version numbers remain unchanged.
- The Gradle wrapper is pinned to Gradle 9.0.0. Release and reproducible-build tasks received configuration-cache and build-cache updates.

## Notable Changes

### Bundled DAO and Network Resources

Bundled mainnet DAO state and block resources are refreshed through height 970000. The DAO state hash checkpoints are updated, the Burning Man v0006 address list is refreshed, and the bundled BTC mainnet denylist is updated. The DAO resource audit and its documented refresh procedure are also updated.

### Tor and Dependency Verification

The netlayer dependency is updated to v0.7.11, a revision containing Tor 0.4.9.13. Gradle dependency verification metadata and the dependency signature report were updated for the new artifacts. A test checks that bundled Tor binaries report their declared version, and the Gradle distribution is verified against its expected key fingerprint.

### BSQ Swap Validation

BSQ swap validation now binds seller inputs to their outpoints, rejects BSQ outputs and duplicate buyer inputs, and verifies seller input and buyer signatures. Tests cover invalid inputs, signatures, and sibling spends of legacy BSQ swap inputs. Documentation describes the remaining fee-rate risk.

### P2P and DAO State Hash Security

Offer expiry is backdated only after outbound disconnects. The dialed peer address is bound before peer data is read. DAO seed state hashes are accepted only from outbound connections.

### Trading and Payment Accounts

The API rejects a payment-started confirmation until the buyer has received the seller's payment account details. Revolut account details are labeled as a Revtag, matching the identifier required by Revolut. Bank form input is validated before dependent fields are reset when the country changes.

### Offer Republish and DAO Provider Initialization

Open offers are not retried while the P2P network is still bootstrapping; all open offers are republished after bootstrap completes. Trusted BSQ block providers are initialized once, including when the resulting provider list is empty, and the returned collection is immutable.

### Build and Release Tooling

Gradle 9.0.0 is pinned for release builds. Release verification, dependency verification, reproducible archive checks, and release manifest tasks were adjusted to work with Gradle's configuration cache where applicable. Dependency resolution tasks that require access to all project configurations explicitly remain incompatible with that cache.

The reproducible build Docker workflow uses a multi-stage build, invokes the reproducible Debian packaging task, and enables Gradle's build cache. CI actions were updated. The Gradle documentation and README command examples were also revised.

## Tests and Documentation

The range adds regression coverage for API payment-start validation, trusted-provider initialization, offer republishing before bootstrap, BSQ swap transaction validation, peer disconnect handling, and seed state hash trust. It updates Gradle task behavior and documentation, release and dependency verification metadata, CI workflows, and bundled resource files. This release note summarizes source and resource changes; it does not claim that release builds or tests were run for this note.

## Commit Summary

The range contains 62 non-merge commits and 34 merge commits. Principal changes include:

| Commit | Summary |
| --- | --- |
| [d19bf16771](https://github.com/bisq-network/bisq/commit/d19bf16771) | Reject payment-started confirmation without seller payment account details |
| [dcf9916f17](https://github.com/bisq-network/bisq/commit/dcf9916f17) | Initialize trusted BSQ block providers once |
| [02d5f908b8](https://github.com/bisq-network/bisq/commit/02d5f908b8) | Use Revtag for Revolut account details |
| [8eca917a15](https://github.com/bisq-network/bisq/commit/8eca917a15) | Skip offer republish retries before P2P bootstrap |
| [5ea8afc452](https://github.com/bisq-network/bisq/commit/5ea8afc452) | Fix bank account validation order |
| [f88c8aa430](https://github.com/bisq-network/bisq/commit/f88c8aa430) | Update DAO resources |
| [4e2e013558](https://github.com/bisq-network/bisq/commit/4e2e013558) | Update denylist |
| [2b937972cd](https://github.com/bisq-network/bisq/commit/2b937972cd) | Update DAO state hash checkpoints |
| [383aae0b76](https://github.com/bisq-network/bisq/commit/383aae0b76) | Update Burning Man address list |
| [4cf915501b](https://github.com/bisq-network/bisq/commit/4cf915501b) | Set v1.10.9 |
| [050f1cc085](https://github.com/bisq-network/bisq/commit/050f1cc085) | Update netlayer and dependency verification metadata |
| [d80a83727e](https://github.com/bisq-network/bisq/commit/d80a83727e) | Enable Gradle build cache |
| [a8f16139d4](https://github.com/bisq-network/bisq/commit/a8f16139d4) | Use reproducible Debian packaging in the Docker build |
| [f2b4aef427](https://github.com/bisq-network/bisq/commit/f2b4aef427) | Optimize Docker build caching |
| [7a98e0fca1](https://github.com/bisq-network/bisq/commit/7a98e0fca1) | Read test report location before the test suite callback |
| [d95e36f2d6](https://github.com/bisq-network/bisq/commit/d95e36f2d6) | Keep manifest and evidence helpers off the project model |
| [c5c6ab9158](https://github.com/bisq-network/bisq/commit/c5c6ab9158) | Enable DAO setup tasks and make README commands copyable |
| [89506989c1](https://github.com/bisq-network/bisq/commit/89506989c1) | Avoid project model access during verification tasks |
| [6f851d8664](https://github.com/bisq-network/bisq/commit/6f851d8664) | Use the Gradle 9 Develocity plugin version |
| [e4758054ad](https://github.com/bisq-network/bisq/commit/e4758054ad) | Support configuration cache in reproducible archive verification |
| [2d7770db49](https://github.com/bisq-network/bisq/commit/2d7770db49) | Support configuration cache in release manifest tasks |
| [145e32fb36](https://github.com/bisq-network/bisq/commit/145e32fb36) | Read Gradle version during configuration |
| [8040b67ee3](https://github.com/bisq-network/bisq/commit/8040b67ee3) | Support configuration cache in dependency verification tasks |
| [ed94df26a3](https://github.com/bisq-network/bisq/commit/ed94df26a3) | Pin release build environment to Gradle 9.0.0 |
| [e3bce7cf8a](https://github.com/bisq-network/bisq/commit/e3bce7cf8a) | Bind BSQ swap seller inputs to their outpoints |
| [6568d18225](https://github.com/bisq-network/bisq/commit/6568d18225) | Reject BSQ outputs as BSQ swap seller inputs |
| [953f185e05](https://github.com/bisq-network/bisq/commit/953f185e05) | Verify BSQ swap seller input signatures |
| [8dc198a772](https://github.com/bisq-network/bisq/commit/8dc198a772) | Verify BSQ swap buyer signatures at the seller |
| [0787ced549](https://github.com/bisq-network/bisq/commit/0787ced549) | Reject duplicate BSQ swap buyer inputs |
| [e03ea869c2](https://github.com/bisq-network/bisq/commit/e03ea869c2) | Backdate offers only on outbound disconnects |
| [7ca7adb676](https://github.com/bisq-network/bisq/commit/7ca7adb676) | Require outbound connections for seed state hashes |
| [68783482a9](https://github.com/bisq-network/bisq/commit/68783482a9) | Update netlayer to v0.7.11 and dependency verification metadata |
| [13f6915b47](https://github.com/bisq-network/bisq/commit/13f6915b47) | Update DAO resources through height 970000 |
| [d0e2f1c269](https://github.com/bisq-network/bisq/commit/d0e2f1c269) | Update DAO state hash checkpoints |
| [40f25a606a](https://github.com/bisq-network/bisq/commit/40f25a606a) | Update Burning Man address list |
