# Reproducible Release Builds

This page is the entry point for Bisq reproducible-release-build
documentation. It explains the project direction, the current trust model, and
where to find operational and technical details.

## Goal

The reproducible-build project should let a release manager, CI runner, or
independent verifier rebuild a release commit and answer three questions:

1. Did the Java release payload match the published evidence?
2. Did each OS installer match the published evidence for that same OS?
3. Was the build performed in a release environment whose important inputs are
   recorded and reviewable?

The canonical comparison artifacts are manifests. They record SHA-256, byte
size, and repo-relative path. Evidence ZIPs package those manifests with
diagnostic build information. Installer evidence also includes package
structure reports for investigation.

## Current Scope

The Java release payload check covers:

- enabled Gradle `Jar` task outputs
- desktop runtime libraries under `desktop/build/install/desktop/lib`
- Gradle wrapper files and wrapper distribution metadata
- pinned Gradle and Java runtime versions
- Gradle dependency verification metadata and the checksum-only allowlist
- reproducible ZIP/JAR archive metadata

Installer evidence covers the OS package files produced by
`:desktop:generateInstallers`:

- Linux: `.deb` and `.rpm`
- macOS: `.dmg`
- Windows: `.exe`

Installer manifests prove that the produced binary installer files match a
published or CI-generated target for the same OS. Installer structure reports
help explain internal package differences.

## Current Status

Linux has the strongest support. The pinned Linux release-builder Docker image
can build two clean worktrees of the same commit, verify Java release evidence,
verify Linux installer evidence, and compare both outputs. This is wired as the
manual GitHub Actions workflow `Linux Release Builder`.

The standard `Build Bisq` workflow runs `verifyReleaseBuild` on Linux, macOS,
and Windows for push and pull request builds. That verifies the Java release
payload and policy gates, but it is not the full two-worktree Linux
release-builder comparison.

The manual `Installer Evidence` workflow can generate installer evidence on
Linux, macOS, and Windows. It records useful per-OS installer diagnostics, but
macOS and Windows do not yet have pinned, two-worktree release-builder
environments equivalent to Linux.

## Limitations

- Reproducibility is proven by comparing manifests, not by trusting build logs.
- Java payload evidence is comparable across build environments, but
  platform-specific runtime dependency differences must still be explained.
  Installer evidence is compared only against the same OS installer format.
- Linux has a pinned release-builder image. macOS and Windows currently rely on
  GitHub-hosted runner environments or local machines.
- macOS `.dmg` and Windows `.exe` installer internals are diagnostic evidence
  today; deterministic platform release builders are still future work.
- Published evidence is only useful after the release manager signs it and
  verifiers check those signatures.

## Direction

The project direction is:

1. Keep Linux as the reference implementation for a pinned, isolated,
   two-worktree reproducible release-builder.
2. Publish signed Java release evidence and per-OS installer evidence next to
   release artifacts.
3. Treat unexplained Java payload mismatches as release blockers.
4. Treat installer mismatches as OS-specific blockers for the affected
   installer artifact.
5. Extend the Linux model to macOS and Windows by adding pinned or otherwise
   reviewable release-builder environments and A/B comparisons.
6. Keep dependency verification maintenance separate from ordinary code changes
   so new checksum-only artifacts receive explicit review.

## Documentation Map

- [How to use reproducible builds](usage.md)
- [Technical details](technical.md)
- [Linux reproducible builds](linux.md)
- [macOS reproducible builds](macos.md)
- [Windows reproducible builds](windows.md)
