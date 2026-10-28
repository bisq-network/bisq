# Tor upgrade in Bisq

This guide describes the steps necessary to upgrade the Tor which Bisq uses.

## Background

Bisq uses the Tor library of the Git submodule `tor/` ([tor][1]), which Bisq 2 uses as well. See
[Tor submodule](build.md#tor-submodule).

The library brings its own Tor on every operating system. Its build downloads the Tor expert bundle of the version
`tor.version` in `tor/gradle.properties` from archive.torproject.org, verifies the PGP signature against the Tor
Browser signing key, the SHA-256 hash which `gradle.properties` pins for the platform and the version of its tor, and
packs the bundle into the jar. See `tor/build-logic/tor-binary/README.md`.

The stable Tor Browser releases have no expert bundle for Linux ARM64. Until one has, Linux ARM64 takes the bundle of
the alpha release `tor.bundleVersion.linux_arm64`, pinned by `tor.bundleSha256.linux_arm64`, whose tor must be the
version of the stable bundle.

Therefore, upgrading Tor in Bisq comes down to recording a commit of the tor repository with the new `tor.version`.
The bundle is not a Gradle dependency, so the Gradle verification metadata of Bisq does not change.

## Upgrade steps

### 1. Decide if an upgrade is necessary

- `tor.binaryVersion` in `tor/gradle.properties` is the version of the tor daemon in the current bundle.
- Find the desired tor release in the [tor changelog][2].
- Find the Tor Browser version whose expert bundle contains that tor in the [Tor Browser changelog][3].

### 2. Update the tor repository

Change `tor.version` (the Tor Browser version of the bundle) and `tor.binaryVersion` (the version of the tor daemon in
it) in `gradle.properties` of the tor repository, pin the SHA-256 hashes of the new bundles in
`tor.bundleSha256.<platform>`, and raise `version` there, the version of the tor artifacts, as the README of the tor
repository describes.

Also check `tor.bundleVersion.linux_arm64` and `tor.bundleSha256.linux_arm64`: the build of Linux ARM64 fails unless
the tor of that alpha bundle is the new `tor.binaryVersion`. Take the newest alpha release whose Linux ARM64 bundle has
that tor, and pin its hash. Once a stable release has a bundle for Linux ARM64, remove `tor.bundleVersion.linux_arm64`
and pin the bundle of `tor.version` instead. The README of the tor repository and its
`build-logic/tor-binary/README.md` ("Pin the bundles of a release" and "The bundle of Linux ARM64") describe how.

Run `./gradlew :tor:verifyTorBinary` there, which downloads the bundle of the platform of the build and verifies it.
Merge the change.

### 3. Update the commit of `tor/` in Bisq

Check out the new commit in `tor/`, review the changes and commit `tor`:

```sh
git -C tor fetch origin
git -C tor checkout <commit>
git diff --submodule=log tor
git add tor
```

Build Bisq on Linux x86_64, Linux ARM64, macOS and Windows, where the build downloads and verifies the new bundle, and
check that Tor starts.

## Credits

Thanks to freimair, JesusMcCloud, mrosseel, sschuberth and cedricwalter for their work on the original
[tor-binary](https://github.com/JesusMcCloud/tor-binary) and [netlayer](https://github.com/JesusMcCloud/netlayer)
repos, which Bisq used before.

[1]: https://github.com/bisq-network/tor "tor"
[2]: https://gitweb.torproject.org/tor.git/plain/ChangeLog "tor changelog"
[3]: https://gitweb.torproject.org/builders/tor-browser-build.git/plain/projects/browser/Bundle-Data/Docs/ChangeLog.txt "tor browser changelog"
