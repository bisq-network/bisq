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

The current checks do not prove that OS installer packages are reproducible.
`dmg`, `deb`, `rpm`, and `exe` outputs should still be signed and checked as
release artifacts, but installer reproducibility needs separate package-specific
work.

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
- `build/reports/checksums/jars.sha256`

`release-manifest.tsv` is the canonical reproducibility comparison file. It
contains SHA-256, file size, and canonical repo-relative path. `SHA256SUMS` is a
compatibility format for `shasum -c` and `sha256sum -c`. `build-info.json` is
diagnostic metadata and is not itself part of the reproducibility comparison.

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

`-ReleaseManifest` can also point at an extracted CI artifact or evidence
directory if it contains exactly one `release-manifest.tsv`.

For stronger evidence, compare against every CI manifest that should represent
the same Java payload. If one OS differs, inspect both `release-manifest.tsv`
files first, then use `build-info.json` only to explain the environment.

## Signing And Publishing Evidence

After the release manager has rebuilt locally and verified the CI manifests,
publish the release evidence next to the signed release artifacts:

- `release-manifest.tsv`
- `release-manifest.tsv.asc`
- `SHA256SUMS`
- `SHA256SUMS.asc`
- `build-info.json`

Sign the canonical manifest and checksum file with the release signing key.

```bash
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/release-manifest.tsv
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/SHA256SUMS
```

Verify the signatures before upload.

```bash
gpg --verify build/reports/release/release-manifest.tsv.asc build/reports/release/release-manifest.tsv
gpg --verify build/reports/release/SHA256SUMS.asc build/reports/release/SHA256SUMS
```

The signed manifest is the main artifact for reproducible-build verification.
`SHA256SUMS` is useful for common checksum tooling. Keep `build-info.json`
published but unsigned unless the release process explicitly wants signed
diagnostic metadata.

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

- deterministic OS installer outputs for `dmg`, `deb`, `rpm`, and `exe`
- a pinned release build image or dedicated release builder for each OS
- a documented policy for which CI OS manifests must match before signing
- optional signing of a complete release evidence bundle

Those can be added incrementally without changing the current Java payload
manifest format.
