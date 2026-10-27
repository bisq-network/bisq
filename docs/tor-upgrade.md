# Tor upgrade in Bisq

This guide describes the steps necessary to upgrade the Tor which Bisq uses.

## Background

Bisq uses the Tor library of the Git submodule `tor/` ([tor][1]), which Bisq 2 uses as well. See
[Tor submodule](build.md#tor-submodule).

- On macOS and Windows, the library brings its own Tor. Its build downloads the Tor expert bundle of the version
  `tor.version` in `tor/gradle.properties` from archive.torproject.org, verifies the PGP signature against the Tor
  Browser signing key, and packs the bundle into the jar. See `tor/build-logic/tor-binary/README.md`.
- On Linux, Bisq runs the tor of the system. The Bisq packages depend on the `tor` package, so the package manager of
  the system updates it.

Therefore, upgrading Tor in Bisq comes down to recording a commit of the tor repository with the new `tor.version`.
The bundle is not a Gradle dependency, so the Gradle verification metadata of Bisq does not change.

## Upgrade steps

### 1. Decide if an upgrade is necessary

- `tor.binaryVersion` in `tor/gradle.properties` is the version of the tor daemon in the current bundle.
- Find the desired tor release in the [tor changelog][2].
- Find the Tor Browser version whose expert bundle contains that tor in the [Tor Browser changelog][3].

### 2. Update the tor repository

Change `tor.version` (the Tor Browser version of the bundle) and `tor.binaryVersion` (the version of the tor daemon in
it) in `gradle.properties` of the tor repository, and raise `version` there, the version of the tor artifacts, as the
README of the tor repository describes. Run `./gradlew :tor:verifyTorBinary` there, which downloads the bundle and
verifies its signature. Merge the change.

### 3. Update the commit of `tor/` in Bisq

Check out the new commit in `tor/`, review the changes and commit `tor`:

```sh
git -C tor fetch origin
git -C tor checkout <commit>
git diff --submodule=log tor
git add tor
```

Build Bisq on macOS and Windows, where the build downloads and verifies the new bundle, and check that Tor starts.

## Credits

Thanks to freimair, JesusMcCloud, mrosseel, sschuberth and cedricwalter for their work on the original
[tor-binary](https://github.com/JesusMcCloud/tor-binary) and [netlayer](https://github.com/JesusMcCloud/netlayer)
repos, which Bisq used before.

[1]: https://github.com/bisq-network/tor "tor"
[2]: https://gitweb.torproject.org/tor.git/plain/ChangeLog "tor changelog"
[3]: https://gitweb.torproject.org/builders/tor-browser-build.git/plain/projects/browser/Bundle-Data/Docs/ChangeLog.txt "tor browser changelog"
