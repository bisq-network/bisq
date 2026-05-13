# macOS Reproducible Builds

macOS currently supports Java release evidence and `.dmg` installer evidence.
It does not yet have a pinned release-builder environment equivalent to Linux.

## Status

Supported:

- Java release payload evidence through `verifyReleaseBuild`
- `.dmg` installer evidence through `verifyInstallerEvidenceBundle`
- manual CI installer evidence upload through `Installer Evidence`
- installer diagnostics using macOS tools such as `hdiutil` and `pkgutil`

Remaining gaps:

- no pinned macOS release-builder image
- no two-worktree macOS reproducibility workflow equivalent to Linux
- `.dmg` internals are diagnostic evidence today, not a completed deterministic
  release-builder guarantee

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

There is no dedicated macOS release-builder A/B workflow yet.

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

The macOS target should follow the Linux model once a suitable pinned or
otherwise reviewable macOS builder strategy exists:

1. Build two clean worktrees of the same commit.
2. Generate Java and macOS installer evidence in both.
3. Compare release evidence and installer evidence.
4. Upload the first worktree's signed evidence candidates.
