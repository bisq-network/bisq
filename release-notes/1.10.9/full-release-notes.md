# Bisq 1.10.9 Release Notes

These release notes cover the changes from commit `a64dcc375cf81320ba29cedbc9fd41698721d265` through `HEAD` (`fa50587606`).

- Total commits: 36
- Non-merge commits: 23
- Merge commits: 13
- Files changed: 32
- Generated on: 2026-09-24

## Compatibility and Operator Notes

- The application and packaging version is `1.10.9`.
- Bundled mainnet DAO data, state hash checkpoints, and the Burning Man address list are updated. New installations and nodes using bundled data start from the updated resources.
- The bundled netlayer dependency is updated to `0d4dc4b6368f72bd90e517667cbb22b42a798754`, which includes Tor 0.4.9.13.
- P2P, local database, and trade protocol version numbers remain unchanged.
- The Gradle wrapper is pinned to Gradle 9.0.0. Release and reproducible-build tasks received configuration-cache and build-cache updates.

## Notable Changes

### Bundled DAO and Network Resources

Bundled mainnet DAO state and block resources are refreshed through height 969000. The DAO state hash checkpoints now include heights 967000 and 968000. The Burning Man v0006 address list and the BTC mainnet denylist are also updated.

### Tor and Dependency Verification

The netlayer dependency is updated to a revision containing Tor 0.4.9.13. Gradle dependency verification metadata and the dependency signature report were updated for the new artifacts.

### Build and Release Tooling

Gradle 9.0.0 is pinned for release builds. Release verification, dependency verification, reproducible archive checks, and release manifest tasks were adjusted to work with Gradle's configuration cache where applicable. Dependency resolution tasks that require access to all project configurations explicitly remain incompatible with that cache.

The reproducible build Docker workflow uses a multi-stage build, invokes the reproducible Debian packaging task, and enables Gradle's build cache. CI actions were updated. The Gradle documentation and README command examples were also revised.

## Tests and Documentation

The range updates Gradle task behavior and documentation, release and dependency verification metadata, CI workflows, and bundled resource files. This release note summarizes verified source and resource changes; it does not claim that release builds or tests were run for this note.

## Commit Summary

The range contains 23 non-merge commits and 13 merge commits. Principal changes include:

| Commit | Summary |
| --- | --- |
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

