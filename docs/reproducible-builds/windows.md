# Windows Reproducible Builds

Windows supports Java release evidence, `.exe` installer evidence, and a manual
two-worktree release-builder workflow on the fixed `windows-2025` GitHub
runner.

## Status

Supported:

- Java release payload evidence through `verifyReleaseBuild`
- `.exe` installer evidence through `verifyInstallerEvidenceBundle`
- manual CI installer evidence upload through `Installer Evidence`
- manual two-worktree CI comparison through `Windows Release Builder`
- installer diagnostics using PowerShell and WiX-related tools when available

Remaining gaps:

- no pinned Windows release-builder image equivalent to the Linux Docker image
- `.exe` installer internals are diagnostic evidence; the release-builder gate
  compares the installer manifest

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

Windows installer generation must run on Windows because `jpackage` builds
native packages only for the host OS. It also expects WiX Toolset on `PATH`.

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
It installs pinned WiX Toolset with Chocolatey, runs
`verifyInstallerEvidenceBundle --scan`, and uploads
`installer-evidence-windows-2025-java-21.0.6`.

The manual `Windows Release Builder` workflow runs on `windows-2025`, installs
pinned WiX Toolset with Chocolatey, creates two clean worktrees at the selected
commit, runs `clean verifyReleaseBuild verifyInstallerEvidenceBundle` in both,
compares Java and installer evidence bundles, and uploads the first worktree's
evidence.

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

The Windows target now follows the Linux two-worktree comparison model, but its
environment is the fixed GitHub-hosted `windows-2025` runner instead of a
pinned container image. Future work should keep reducing unpinned runner inputs.
