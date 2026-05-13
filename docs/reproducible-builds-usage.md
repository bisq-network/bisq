# Reproducible Builds: How To Use

Use this page when running or verifying release-build evidence. For
implementation details, see [technical details](reproducible-builds-technical.md).

## Prepare A Checkout

Use a clean checkout of the release tag or release commit.

```bash
git submodule update --init --recursive
shasum -a 256 -c gradle/wrapper/gradle-wrapper.sha256
```

On Linux, prefer the pinned release-builder image described in
[Linux reproducible builds](reproducible-builds-linux.md).

## Build Java Release Evidence

Run the release payload and policy gates:

```bash
./gradlew clean verifyReleaseBuild
```

The main outputs are:

- `build/reports/release/release-evidence.zip`
- `build/reports/release/release-manifest.tsv`
- `build/reports/release/SHA256SUMS`
- `build/reports/release/build-info.json`
- `build/reports/checksums/jars.sha256`

Verify the generated checksum file:

```bash
shasum -a 256 -c build/reports/release/SHA256SUMS
```

Compare a local rebuild against a published or CI-generated manifest:

```bash
./gradlew verifyReleaseManifest \
  -PreleaseManifest=/path/to/release-manifest.tsv
```

`-PreleaseManifest` may point at a manifest file, extracted evidence
directory, downloaded GitHub Actions artifact directory, or
`release-evidence.zip`.

Compare two evidence bundles without rebuilding:

```bash
./gradlew compareReleaseEvidenceBundles \
  -PleftReleaseEvidence=/path/to/local/release-evidence.zip \
  -PrightReleaseEvidence=/path/to/ci/release-evidence.zip
```

## Build Installer Evidence

Run the OS-specific installer evidence task:

```bash
./gradlew verifyInstallerEvidenceBundle
```

This builds the current OS installer artifacts and writes:

- `build/reports/release/installer-evidence.zip`
- `build/reports/release/installer-manifest.tsv`
- `build/reports/release/INSTALLER-SHA256SUMS`
- `build/reports/release/installer-build-info.json`
- `build/reports/release/installer-structure-summary.txt`
- `build/reports/release/installer-structure-report.tsv`
- `build/reports/release/installer-structure/`

Verify installer checksums:

```bash
shasum -a 256 -c build/reports/release/INSTALLER-SHA256SUMS
```

Compare a local installer rebuild against published or CI-generated evidence:

```bash
./gradlew verifyInstallerManifest \
  -PinstallerManifest=/path/to/installer-evidence.zip
```

Compare two installer evidence bundles:

```bash
./gradlew compareInstallerEvidenceBundles \
  -PleftInstallerEvidence=/path/to/local/installer-evidence.zip \
  -PrightInstallerEvidence=/path/to/ci/installer-evidence.zip
```

Installer evidence is OS-specific. Compare Linux installers to Linux
installers, macOS installers to macOS installers, and Windows installers to
Windows installers.

## Use GitHub Actions

`Build Bisq` runs automatically on push and pull request. It runs
`verifyReleaseBuild --scan` on Linux, macOS, and Windows and uploads Java
release evidence.

`Installer Evidence` is manual (`workflow_dispatch`). It runs
`verifyInstallerEvidenceBundle --scan` on Linux, macOS, and Windows and uploads
per-OS installer evidence.

`Linux Release Builder` is manual (`workflow_dispatch`). It is the strongest
current reproducibility check. It builds the pinned Linux release-builder
image, creates two clean worktrees of the selected commit, runs
`clean verifyReleaseBuild verifyInstallerEvidenceBundle` in both worktrees, and
compares the Java and installer evidence bundles.

To trigger a manual workflow, open the repository's GitHub Actions tab, choose
the workflow, click `Run workflow`, and select the branch or tag to verify.

## Release Manager Policy

Before signing release artifacts:

1. Build from the release tag or release commit.
2. Verify the Gradle wrapper checksum manifest.
3. Generate Java release evidence.
4. Compare Java release evidence against the Linux CI or release-builder
   evidence for the same commit and Java version.
5. Compare macOS and Windows Java evidence when available.
6. Generate or download installer evidence for each OS artifact that will be
   published.
7. Compare each installer only against evidence for the same OS and commit.
8. Explain every mismatch before signing.

An unexplained Java payload mismatch is a release blocker. An unexplained
installer mismatch blocks publication of that OS installer.

## Signing Evidence

Sign the evidence bundle and canonical manifest files with the release signing
key:

```bash
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/release-evidence.zip
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/release-manifest.tsv
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/SHA256SUMS

gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/installer-evidence.zip
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/installer-manifest.tsv
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/INSTALLER-SHA256SUMS
```

Publish the signed evidence next to the release artifacts.

## Independent Verifier Workflow

An independent verifier should:

1. Check out the release tag.
2. Initialize submodules.
3. Verify the Gradle wrapper checksum file.
4. Rebuild Java release evidence.
5. Verify the published evidence signatures.
6. Compare the local evidence against the signed published evidence.

```bash
git submodule update --init --recursive
shasum -a 256 -c gradle/wrapper/gradle-wrapper.sha256
./gradlew clean verifyReleaseBuild
gpg --verify /path/to/release-evidence.zip.asc /path/to/release-evidence.zip
./gradlew verifyReleaseManifest \
  -PreleaseManifest=/path/to/release-evidence.zip
```

To verify installers, also rebuild or download installer evidence for the same
OS and compare it with the signed installer evidence:

```bash
gpg --verify /path/to/installer-evidence.zip.asc /path/to/installer-evidence.zip
./gradlew verifyInstallerManifest \
  -PinstallerManifest=/path/to/installer-evidence.zip
```
