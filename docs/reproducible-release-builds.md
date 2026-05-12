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

### Linux Release-Builder Container

The Linux Java payload can be verified inside the release-builder image defined
in `docker/release-builder/linux/Dockerfile`. The image pins the linux/amd64
Azul Zulu OpenJDK 21.0.6 base image by digest and sets the release-sensitive
defaults `SOURCE_DATE_EPOCH=0`, `TZ=UTC`, `LANG=C.UTF-8`, and `LC_ALL=C.UTF-8`.
It also installs the Linux package tools needed for Java payload verification
and later installer investigation from a pinned Ubuntu package repository
snapshot. The Dockerfile enables Ubuntu snapshot resolution for older Ubuntu
base images before setting the apt snapshot value. The default snapshot is
`20260501T000000Z`; update the `UBUNTU_APT_SNAPSHOT` build argument in a
separate reviewed change when the release-builder package set needs refreshing.

Build the image from its own small context:

```bash
docker build --pull=false -t bisq-release-builder-linux:java-21.0.6 docker/release-builder/linux
```

To test a newer Ubuntu package snapshot before changing the Dockerfile default:

```bash
docker build --pull=false \
  --build-arg UBUNTU_APT_SNAPSHOT=20260501T000000Z \
  -t bisq-release-builder-linux:java-21.0.6 \
  docker/release-builder/linux
```

Run it from a clean checkout of the release tag or release commit:

```bash
docker run --rm --platform linux/amd64 \
  --user "$(id -u):$(id -g)" \
  -v "$PWD:/workspace" \
  -w /workspace \
  bisq-release-builder-linux:java-21.0.6 \
  ./gradlew verifyReleaseBuild
```

This container is an independent Linux verification environment for the Java
payload. The manual GitHub Actions workflow `Linux Release Builder` builds this
image, runs `verifyReleaseBuild` inside it, and uploads the Java payload
evidence artifact `release-builder-linux-java-21.0.6`. The workflow is
`workflow_dispatch` only, so it is not part of normal push or pull-request CI.
The container does not make installer internals deterministic by itself.

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
It records the Gradle and Java runtime, installer-relevant tools such as
`jpackage`, Linux package and archive tools, macOS `hdiutil` and `pkgutil`,
Windows PowerShell, and Windows WiX tools when available. It also records OS
release data such as `uname`, `/etc/os-release`, and `sw_vers` when available,
plus `SOURCE_DATE_EPOCH`, timezone, and locale data.
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

`-PreleaseManifest` can also point at an extracted CI artifact, an evidence
directory, or a `release-evidence.zip` file if it contains exactly one
`release-manifest.tsv`.

For stronger evidence, compare against every CI manifest that should represent
the same Java payload. If one OS differs, inspect both `release-manifest.tsv`
files first, then use `build-info.json` only to explain the environment.

### Manifest Match Policy

Before signing release artifacts, the release manager must compare their local
Java payload manifest against the Linux GitHub Actions Java payload manifest
for the same release tag or commit and the pinned Java version. The manifests
must match exactly.

The macOS and Windows Java payload manifests are additional verification
signals. Compare them as well when they are available for the same release tag
or commit. Any mismatch must be explained before signing; if the mismatch is in
a shared Java artifact rather than a platform-specific runtime dependency,
treat it as a release blocker.

Installer manifests are compared per OS. A Linux `.deb` or `.rpm` manifest is
not expected to match a macOS `.dmg` manifest or a Windows `.exe` manifest.
For each OS installer that will be published, the release manager should verify
a local rebuild against the signed or CI-provided installer manifest for that
same OS.

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
- `build/reports/release/installer-build-info.json`

`installer-manifest.tsv` is the canonical comparison file for installer
artifacts. `INSTALLER-SHA256SUMS` is compatible with common checksum tooling.
`installer-build-info.json` records the OS, JDK, Gradle, locale, timezone, and
installer-relevant tool diagnostics such as Linux package and archive tools,
macOS `hdiutil` and `pkgutil`, Windows PowerShell, and Windows WiX tools for
explaining per-OS installer differences.

To inspect installer package internals with the tools available on the current
OS, run:

```bash
./gradlew generateInstallerStructureReport
```

This writes:

- `build/reports/release/installer-structure-report.tsv`
- `build/reports/release/installer-structure/`

`installer-structure-report.tsv` and the `installer-structure/` reports are
investigation aids for package internals. They use local tools such as `ar`,
`file`, `dpkg-deb`, `rpm`, `hdiutil`, `pkgutil`, and Windows PowerShell when
available to record package metadata, archive member metadata, payload listings,
file type metadata, and signature details. Debian reports include listings for
the outer archive, package fields, package payload, and the inner `control.tar`
and `data.tar` archives. Unsupported installer formats are listed as skipped
rather than failing the evidence task.

The `status` column in `installer-structure-report.tsv` is diagnostic:

- `generated` means at least one configured structure inspector ran and all
  required inspector commands for that artifact exited successfully.
- `failed` means a configured required inspector command exited unsuccessfully;
  inspect the matching report file for stdout and stderr.
- `skipped` means no required structure inspector is configured for that
  artifact on the current OS. The artifact can still be compared through
  `installer-manifest.tsv`.

To package the installer evidence files into one reproducible ZIP, run:

```bash
./gradlew generateInstallerEvidenceBundle
```

The bundle is written to:

- `build/reports/release/installer-evidence.zip`

`generateInstallerEvidenceBundle` checks that the installer manifest,
checksums, build info, structure summary, and referenced structure reports are
present before packaging. It also verifies the ZIP contains those required
entries before reporting success.

The manual GitHub Actions workflow `Installer Evidence` can generate Linux,
macOS, and Windows installer evidence for a release commit or tag without
making installer generation part of the normal push or pull-request release
build gate.

Verify the generated installer checksum file locally:

```bash
shasum -a 256 -c build/reports/release/INSTALLER-SHA256SUMS
```

Compare a local rebuild against a signed or CI-generated installer manifest:

```bash
./gradlew verifyInstallerManifest -PinstallerManifest=/path/to/installer-manifest.tsv
```

`-PinstallerManifest` can also point at an extracted evidence directory or an
`installer-evidence.zip` file if it contains exactly one
`installer-manifest.tsv`.

### Linux Installer Rebuild Comparison

Use two isolated worktrees of the same release tag or commit when investigating
whether Linux installers are deterministic. Build both inside the pinned Linux
release-builder image and keep each evidence directory for comparison. Replace
`HEAD` with the signed release tag or commit SHA when checking an actual
release.

```bash
RELEASE_REF=HEAD
git worktree add --detach ../bisq-installer-a "$RELEASE_REF"
git worktree add --detach ../bisq-installer-b "$RELEASE_REF"
git -C ../bisq-installer-a submodule update --init --recursive
git -C ../bisq-installer-b submodule update --init --recursive

docker run --rm --platform linux/amd64 \
  --user "$(id -u):$(id -g)" \
  -v "$PWD/../bisq-installer-a:/workspace" \
  -w /workspace \
  bisq-release-builder-linux:java-21.0.6 \
  ./gradlew clean generateInstallerEvidenceBundle

docker run --rm --platform linux/amd64 \
  --user "$(id -u):$(id -g)" \
  -v "$PWD/../bisq-installer-b:/workspace" \
  -w /workspace \
  bisq-release-builder-linux:java-21.0.6 \
  ./gradlew clean generateInstallerEvidenceBundle

diff -u \
  ../bisq-installer-a/build/reports/release/installer-manifest.tsv \
  ../bisq-installer-b/build/reports/release/installer-manifest.tsv
```

If the installer manifests differ, compare
`installer-structure-report.tsv`, the files under `installer-structure/`, and
`installer-build-info.json` from both worktrees before changing package
generation logic. For Debian packages, start with the outer `ar` member
metadata and the inner `control.tar` and `data.tar` listings because those
reports expose timestamps, ordering, ownership, and mode differences.

Publish and sign the installer evidence next to the corresponding binary
artifacts:

- `installer-evidence.zip`
- `installer-evidence.zip.asc`
- `installer-manifest.tsv`
- `installer-manifest.tsv.asc`
- `INSTALLER-SHA256SUMS`
- `INSTALLER-SHA256SUMS.asc`
- `installer-build-info.json`
- `installer-structure-report.tsv`
- `installer-structure/`

```bash
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/installer-evidence.zip
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/installer-manifest.tsv
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/INSTALLER-SHA256SUMS
```

## Independent Verifier Workflow

An independent verifier should:

1. Check out the release tag.
2. Verify the Gradle wrapper checksum manifest.
3. Build with `verifyReleaseBuild`.
4. Verify the published manifest signature.
5. Run `verifyReleaseManifest` against the published `release-manifest.tsv` or
   `release-evidence.zip`.

```bash
git submodule update --init --recursive
shasum -a 256 -c gradle/wrapper/gradle-wrapper.sha256
./gradlew clean verifyReleaseBuild
gpg --verify /path/to/release-manifest.tsv.asc /path/to/release-manifest.tsv
./gradlew verifyReleaseManifest -PreleaseManifest=/path/to/release-manifest.tsv
gpg --verify /path/to/release-evidence.zip.asc /path/to/release-evidence.zip
./gradlew verifyReleaseManifest -PreleaseManifest=/path/to/release-evidence.zip
```

If the task succeeds, the local Java payload manifest matches the signed release
manifest. If it fails, inspect the reported missing, extra, and changed paths.

To verify binary installers, also verify the signed installer manifest and
compare a local installer rebuild:

```bash
gpg --verify /path/to/installer-evidence.zip.asc /path/to/installer-evidence.zip
gpg --verify /path/to/installer-manifest.tsv.asc /path/to/installer-manifest.tsv
./gradlew verifyInstallerManifest -PinstallerManifest=/path/to/installer-manifest.tsv
./gradlew verifyInstallerManifest -PinstallerManifest=/path/to/installer-evidence.zip
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
`gradle/dependency-checksum-fallback-allowlist.tsv`. Each allowlist entry must
include the exact dependency, artifact file name, and review rationale. Prefer
PGP signature verification whenever upstream publishes signatures.

## Remaining Gaps

The most important remaining gaps are:

- deterministic OS installer internals for `dmg`, `deb`, `rpm`, and `exe`
- pinned release build images or dedicated release builders for macOS and
  Windows

Those can be added incrementally without changing the current Java payload
manifest format.
