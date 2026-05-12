# Reproducible Release Build Verification

This document describes the reproducible-build evidence Bisq currently
generates for release builds and how a release manager can compare a local build
against GitHub Actions before signing release artifacts.

## Current Scope

The current checks cover the Java release payload:

- all enabled Gradle `Jar` task outputs
- the desktop application runtime libraries generated under
  `desktop/build/install/desktop/lib`
- Gradle wrapper files and wrapper distribution metadata
- pinned Gradle and Java runtime versions
- Gradle dependency verification metadata, including the reviewed checksum-only
  dependency allowlist
- reproducible archive settings and ZIP/JAR entry metadata

The installer manifest task covers the OS package files produced by
`:desktop:generateInstallers`. It records SHA-256, byte size, and canonical
repo-relative path for `dmg`, `deb`, `rpm`, and `exe` outputs. This gives the
release manager and independent verifiers a signed comparison target for the
actual binary artifacts, but it does not by itself prove that the installer
formats are deterministic internally.

## Release Manager Workflow

Use a clean checkout of the release tag or release commit, with submodules
initialized. The pinned release build environment is recorded in
`gradle.properties`.

```bash
git submodule update --init --recursive
shasum -a 256 -c gradle/wrapper/gradle-wrapper.sha256
```

Build the release payload and run the policy gates.

```bash
./gradlew clean verifyReleaseBuild
```

The release evidence is written to:

- `build/reports/release/release-manifest.tsv`
- `build/reports/release/SHA256SUMS`
- `build/reports/release/build-info.json`
- `build/reports/release/release-evidence.zip`
- `build/reports/checksums/jars.sha256`

`release-manifest.tsv` is the canonical reproducibility comparison file. It
contains SHA-256, file size, and canonical repo-relative path. `SHA256SUMS` is a
compatibility format for `shasum -c` and `sha256sum -c`. `build-info.json` is
diagnostic metadata and is not itself part of the reproducibility comparison.
`release-evidence.zip` packages the manifest, checksums, build info, and jar
checksum report into one reproducible file for signing and publishing.

Verify the generated checksum file locally.

```bash
shasum -a 256 -c build/reports/release/SHA256SUMS
```

Download the matching GitHub Actions artifact named
`release-manifests-<os>-java-21.0.6` from the release commit or tag build. The
artifact contains the CI-generated `release-manifest.tsv`, `SHA256SUMS`, and
`build-info.json`.

Compare the local rebuild against a CI manifest.

```bash
./gradlew verifyReleaseManifest -PreleaseManifest=/path/to/ci/release-manifest.tsv
```

`-PreleaseManifest` can also point at an extracted CI artifact or evidence
directory if it contains exactly one `release-manifest.tsv`.

For stronger evidence, compare against every CI manifest that should represent
the same Java payload. If one OS differs, inspect both `release-manifest.tsv`
files first, then use `build-info.json` only to explain the environment.

## Signing And Publishing Evidence

After the release manager has rebuilt locally and verified the CI manifests,
publish the release evidence next to the signed release artifacts:

- `release-evidence.zip`
- `release-evidence.zip.asc`
- `release-manifest.tsv`
- `release-manifest.tsv.asc`
- `SHA256SUMS`
- `SHA256SUMS.asc`
- `build-info.json`

Sign the canonical manifest and checksum file with the release signing key.

```bash
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/release-evidence.zip
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/release-manifest.tsv
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/SHA256SUMS
```

Verify the signatures before upload.

```bash
gpg --verify build/reports/release/release-evidence.zip.asc build/reports/release/release-evidence.zip
gpg --verify build/reports/release/release-manifest.tsv.asc build/reports/release/release-manifest.tsv
gpg --verify build/reports/release/SHA256SUMS.asc build/reports/release/SHA256SUMS
```

The signed bundle is the preferred publication artifact. The signed manifest is
the main artifact for reproducible-build verification. `SHA256SUMS` is useful
for common checksum tooling. Keep the individual files published for direct
inspection and simple verifier workflows.

## Binary Installer Evidence

Build the OS-specific installer artifacts and write their manifest with:

```bash
./gradlew generateInstallerManifest
```

`generateInstallerManifest` invokes `:desktop:generateInstallers` and writes:

- `build/reports/release/installer-manifest.tsv`
- `build/reports/release/INSTALLER-SHA256SUMS`

`installer-manifest.tsv` is the canonical comparison file for installer
artifacts. `INSTALLER-SHA256SUMS` is compatible with common checksum tooling.
Verify the generated installer checksum file locally:

```bash
shasum -a 256 -c build/reports/release/INSTALLER-SHA256SUMS
```

Compare a local rebuild against a signed or CI-generated installer manifest:

```bash
./gradlew verifyInstallerManifest -PinstallerManifest=/path/to/installer-manifest.tsv
```

`-PinstallerManifest` can also point at an extracted evidence directory if it
contains exactly one `installer-manifest.tsv`.

Publish and sign the installer evidence next to the corresponding binary
artifacts:

- `installer-manifest.tsv`
- `installer-manifest.tsv.asc`
- `INSTALLER-SHA256SUMS`
- `INSTALLER-SHA256SUMS.asc`

```bash
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/installer-manifest.tsv
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/INSTALLER-SHA256SUMS
```

## Independent Verifier Workflow

An independent verifier should:

1. Check out the release tag.
2. Verify the Gradle wrapper checksum manifest.
3. Build with `verifyReleaseBuild`.
4. Verify the published manifest signature.
5. Run `verifyReleaseManifest` against the published `release-manifest.tsv`.

```bash
git submodule update --init --recursive
shasum -a 256 -c gradle/wrapper/gradle-wrapper.sha256
./gradlew clean verifyReleaseBuild
gpg --verify /path/to/release-manifest.tsv.asc /path/to/release-manifest.tsv
./gradlew verifyReleaseManifest -PreleaseManifest=/path/to/release-manifest.tsv
```

If the task succeeds, the local Java payload manifest matches the signed release
manifest. If it fails, inspect the reported missing, extra, and changed paths.

To verify binary installers, also verify the signed installer manifest and
compare a local installer rebuild:

```bash
gpg --verify /path/to/installer-manifest.tsv.asc /path/to/installer-manifest.tsv
./gradlew verifyInstallerManifest -PinstallerManifest=/path/to/installer-manifest.tsv
```

## Dependency Verification Maintenance

When dependencies change, refresh and review Gradle verification metadata
separately from ordinary code changes.

```bash
./gradlew refreshDependencyVerificationKeyring
./gradlew resolveAndVerifyDependencies --write-verification-metadata pgp,sha256
./gradlew verifyDependencySignaturePolicy
./gradlew dependencySignatureReport
```

New checksum-only artifacts must be reviewed before they are added to
`gradle/dependency-checksum-fallback-allowlist.tsv`. Prefer PGP signature
verification whenever upstream publishes signatures.

## Remaining Gaps

The most important remaining gaps are:

- deterministic OS installer internals for `dmg`, `deb`, `rpm`, and `exe`
- a pinned release build image or dedicated release builder for each OS
- a documented policy for which CI OS manifests must match before signing

Those can be added incrementally without changing the current Java payload
manifest format.
