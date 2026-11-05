# Bisq 1.10.10 Notable Changes

Bisq 1.10.10 updates bundled DAO resources, DAO state hash checkpoints, the Burning Man address list, the Tor dependency, and the release signing keys.

## Bundled Resources

- Mainnet DAO state and BSQ block resources are updated through height 971800.
- DAO state hash checkpoints are added for heights 970000 and 971000. A node with a diverging DAO state at height 969537 fails the checkpoint at 970000 and resyncs from the bundled resources.
- The Burning Man address list v0007 is added.
- The temporary proposal store is no longer shipped; temporary proposals are short-lived and come from the P2P network.

Related commits: [cde48bdda6](https://github.com/bisq-network/bisq/commit/cde48bdda6), [07d5aa1145](https://github.com/bisq-network/bisq/commit/07d5aa1145), [3b5d4553d4](https://github.com/bisq-network/bisq/commit/3b5d4553d4), [45ac94327b](https://github.com/bisq-network/bisq/commit/45ac94327b), [23482d020a](https://github.com/bisq-network/bisq/commit/23482d020a).

## DAO Resource Audit

- The bundled DAO resource audit and the audit evidence in the DAO specifications are refreshed for height 971800.

Related commit: [c697f0d791](https://github.com/bisq-network/bisq/commit/c697f0d791).

## Tor and Dependency Verification

- Update netlayer to v0.7.12 (revision `74230355ff2aa65596fff0f25138045e36b6014e`), which includes Tor 0.4.9.14.
- Update Gradle dependency verification metadata and the dependency signature report.

Related commit: [78f54d1ef0](https://github.com/bisq-network/bisq/commit/78f54d1ef0).

## Release Signing Keys

- The keys `387C8307` and `E222AA02` are updated with new validity dates.
- The unused and expired key `4A133008` is removed from the release assets and from in-app update verification.

Related commits: [7f349d585c](https://github.com/bisq-network/bisq/commit/7f349d585c), [609b17d233](https://github.com/bisq-network/bisq/commit/609b17d233), [5c45909b15](https://github.com/bisq-network/bisq/commit/5c45909b15), [e0742550cd](https://github.com/bisq-network/bisq/commit/e0742550cd).

## Build Tooling

- Update the CI actions `actions/upload-artifact` to 7.0.2 and `actions/setup-java` to 6.1.0.

Related commits: [fb1bd7abae](https://github.com/bisq-network/bisq/commit/fb1bd7abae), [ee8570cdf5](https://github.com/bisq-network/bisq/commit/ee8570cdf5).

## Version

The application and packaging version is `1.10.10`, set by [059dd84cab](https://github.com/bisq-network/bisq/commit/059dd84cab).
