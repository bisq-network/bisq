# Windows Reproducible Builds

Windows currently supports Java release evidence and `.exe` installer evidence.
It does not yet have a pinned release-builder environment equivalent to Linux.

## Status

Supported:

- Java release payload evidence through `verifyReleaseBuild`
- `.exe` installer evidence through `verifyInstallerEvidenceBundle`
- manual CI installer evidence upload through `Installer Evidence`
- installer diagnostics using PowerShell and WiX-related tools when available

Remaining gaps:

- no pinned Windows release-builder image
- no two-worktree Windows reproducibility workflow equivalent to Linux
- `.exe` installer internals are diagnostic evidence today, not a completed
  deterministic release-builder guarantee

## Local Java Evidence

From a clean checkout of the release tag or release commit:

Use Git Bash or another shell with `shasum` available to verify the wrapper
checksum file:

```bash
git submodule update --init --recursive
shasum -a 256 -c gradle/wrapper/gradle-wrapper.sha256
```

Then run the build:

```powershell
./gradlew clean verifyReleaseBuild
```

Compare the resulting Java evidence with signed or CI-generated evidence:

```powershell
./gradlew compareReleaseEvidenceBundles `
  -PleftReleaseEvidence=build/reports/release/release-evidence.zip `
  -PrightReleaseEvidence=C:\path\to\ci\release-evidence.zip
```

## Local Installer Evidence

Windows installer generation expects WiX Toolset on `PATH`.

Build and validate Windows installer evidence:

```powershell
./gradlew verifyInstallerEvidenceBundle
```

Expected installer format:

- `.exe`

Compare against signed or CI-generated Windows installer evidence:

```powershell
./gradlew compareInstallerEvidenceBundles `
  -PleftInstallerEvidence=build/reports/release/installer-evidence.zip `
  -PrightInstallerEvidence=C:\path\to\windows\installer-evidence.zip
```

Only compare Windows installer evidence to Windows installer evidence for the
same release commit.

## CI Workflow

The standard `Build Bisq` workflow runs `verifyReleaseBuild --scan` on
`windows-2025` for push and pull request builds.

The manual `Installer Evidence` workflow has a Windows job on `windows-2025`.
It installs WiX Toolset with Chocolatey, runs
`verifyInstallerEvidenceBundle --scan`, and uploads
`installer-evidence-windows-2025-java-21.0.6`.

There is no dedicated Windows release-builder A/B workflow yet.

## Windows Installer Diagnostics

Windows installer structure reports use Windows PowerShell first and fall back
to PowerShell Core (`pwsh`) when `powershell` is unavailable.

For `.exe` differences, compare:

- `installer-build-info.json`
- file metadata
- version info
- Authenticode signature output
- PE/COFF header timestamp metadata

If `.msi` artifacts are added in future evidence, compare MSI Property table
entries as well.

## Direction

The Windows target should follow the Linux model once a suitable pinned or
otherwise reviewable Windows builder strategy exists:

1. Build two clean worktrees of the same commit.
2. Generate Java and Windows installer evidence in both.
3. Compare release evidence and installer evidence.
4. Upload the first worktree's signed evidence candidates.
