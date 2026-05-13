# macOS Reproducible Builds

macOS supports Java release evidence, `.dmg` installer evidence, and a manual
two-worktree release-builder workflow on the fixed `macos-15` GitHub runner.

## Status

Supported:

- Java release payload evidence through `verifyReleaseBuild`
- `.dmg` installer evidence through `verifyInstallerEvidenceBundle`
- manual CI installer evidence upload through `Installer Evidence`
- manual two-worktree CI comparison through `macOS Release Builder`
- installer diagnostics using macOS tools such as `hdiutil` and `pkgutil`

Remaining gaps:

- no pinned macOS release-builder image equivalent to the Linux Docker image
- `.dmg` internals are diagnostic evidence; the release-builder gate compares
  the installer manifest

## Local Java Evidence

From a clean checkout of the release tag or release commit:

```bash
git submodule update --init --recursive
shasum -a 256 -c gradle/wrapper/gradle-wrapper.sha256
./gradlew clean verifyReleaseBuild
```

Compare the resulting Java evidence with signed or CI-generated evidence:

```bash
./gradlew compareReleaseEvidenceBundles \
  -PleftReleaseEvidence=build/reports/release/release-evidence.zip \
  -PrightReleaseEvidence=/path/to/ci/release-evidence.zip
```

## Local Installer Evidence

Build and validate macOS installer evidence:

```bash
./gradlew verifyInstallerEvidenceBundle
```

Expected installer format:

- `.dmg`

Compare against signed or CI-generated macOS installer evidence:

```bash
./gradlew compareInstallerEvidenceBundles \
  -PleftInstallerEvidence=build/reports/release/installer-evidence.zip \
  -PrightInstallerEvidence=/path/to/macos/installer-evidence.zip
```

Only compare macOS installer evidence to macOS installer evidence for the same
release commit.

## CI Workflow

The standard `Build Bisq` workflow runs `verifyReleaseBuild --scan` on
`macos-15` for push and pull request builds.

The manual `Installer Evidence` workflow has a macOS job on `macos-15`. It
runs `verifyInstallerEvidenceBundle --scan` and uploads
`installer-evidence-macos-15-java-21.0.6`.

The manual `macOS Release Builder` workflow runs on `macos-15`, creates two
clean worktrees at the selected commit, runs
`clean verifyReleaseBuild verifyInstallerEvidenceBundle` in both, compares Java
and installer evidence bundles, and uploads the first worktree's evidence.

## macOS Installer Diagnostics

macOS installer structure reports include diagnostic output from tools
available on macOS, including:

- `file`
- `hdiutil`
- `pkgutil`
- `xar` when applicable

For `.dmg` differences, compare:

- `installer-build-info.json`
- `hdiutil imageinfo`
- partition map output
- `hdiutil verify`
- `hdiutil` SHA-256 checksum output

If a `.pkg` appears in future evidence, compare signature output, payload file
listings, and raw `xar` archive member diagnostics.

## Direction

The macOS target now follows the Linux two-worktree comparison model, but its
environment is the fixed GitHub-hosted `macos-15` runner instead of a pinned
container image. Future work should keep reducing unpinned runner inputs.
