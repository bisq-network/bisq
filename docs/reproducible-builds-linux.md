# Linux Reproducible Builds

Linux is the reference implementation for Bisq reproducible release builds.
It has a pinned release-builder Docker image and a manual CI workflow that
performs a two-worktree A/B comparison.

## Status

Supported:

- Java release payload evidence
- `.deb` installer evidence
- `.rpm` installer evidence
- deterministic Linux RPM repacking
- two clean worktree comparison inside the pinned Linux release-builder image
- manual CI workflow uploads for Java and Linux installer evidence

Remaining gaps:

- Linux release-builder is manual CI, not automatic push or pull-request CI.
- Evidence must still be signed and published by the release manager.

## Build The Release-Builder Image

```bash
docker build --pull=false \
  -t bisq-release-builder-linux:java-21.0.6 \
  docker/release-builder/linux
```

To test a newer Ubuntu package snapshot before changing the Dockerfile default:

```bash
docker build --pull=false \
  --build-arg UBUNTU_APT_SNAPSHOT=20260501T000000Z \
  -t bisq-release-builder-linux:java-21.0.6 \
  docker/release-builder/linux
```

## Run A Single Linux Verification

From a clean checkout of the release tag or release commit:

```bash
docker run --rm --platform linux/amd64 \
  --user "$(id -u):$(id -g)" \
  -v "$PWD:/workspace" \
  -w /workspace \
  bisq-release-builder-linux:java-21.0.6 \
  ./gradlew clean verifyReleaseBuild verifyInstallerEvidenceBundle
```

This writes Java release evidence and Linux installer evidence under
`build/reports/release/`.

## Run The Full A/B Comparison Locally

Use two isolated worktrees of the same release tag or commit:

```bash
RELEASE_REF=HEAD
git worktree add --detach ../bisq-release-a "$RELEASE_REF"
git worktree add --detach ../bisq-release-b "$RELEASE_REF"
git -C ../bisq-release-a submodule update --init --recursive
git -C ../bisq-release-b submodule update --init --recursive

REPO_PARENT="$(cd .. && pwd)"

docker run --rm --platform linux/amd64 \
  --user "$(id -u):$(id -g)" \
  -v "$REPO_PARENT:$REPO_PARENT" \
  -w "$REPO_PARENT/bisq-release-a" \
  bisq-release-builder-linux:java-21.0.6 \
  ./gradlew clean verifyReleaseBuild verifyInstallerEvidenceBundle

docker run --rm --platform linux/amd64 \
  --user "$(id -u):$(id -g)" \
  -v "$REPO_PARENT:$REPO_PARENT" \
  -w "$REPO_PARENT/bisq-release-b" \
  bisq-release-builder-linux:java-21.0.6 \
  ./gradlew clean verifyReleaseBuild verifyInstallerEvidenceBundle
```

Compare the outputs:

```bash
(
  cd ../bisq-release-a
  ./gradlew compareReleaseEvidenceBundles compareInstallerEvidenceBundles \
    -PleftReleaseEvidence=build/reports/release/release-evidence.zip \
    -PrightReleaseEvidence=../bisq-release-b/build/reports/release/release-evidence.zip \
    -PleftInstallerEvidence=build/reports/release/installer-evidence.zip \
    -PrightInstallerEvidence=../bisq-release-b/build/reports/release/installer-evidence.zip
)
```

## CI Workflow

The GitHub Actions workflow is `Linux Release Builder`. It is
`workflow_dispatch` only.

The workflow:

1. Checks out the selected commit with submodules.
2. Builds `bisq-release-builder-linux:java-21.0.6`.
3. Creates clean worktrees `a` and `b` at `$GITHUB_SHA`.
4. Runs `./gradlew clean verifyReleaseBuild verifyInstallerEvidenceBundle` in
   both worktrees inside the Docker image.
5. Runs `compareReleaseEvidenceBundles`.
6. Runs `compareInstallerEvidenceBundles`.
7. Uploads `release-builder-linux-java-21.0.6`.
8. Uploads `release-builder-linux-installers-java-21.0.6`.

This workflow is the current strongest CI signal for Linux reproducibility.

## Linux Installer Diagnostics

Linux installer evidence includes package structure reports for `.deb` and
`.rpm` artifacts.

For `.deb` differences, inspect:

- outer `ar` member metadata
- hashes of `debian-binary`, `control.tar.*`, and `data.tar.*`
- `control.tar` listing
- `data.tar` listing

For `.rpm` differences, inspect:

- package checks and digest output
- package build date and build host
- payload archive listing
- RPM package dump

Start with `installer-build-info.json` to confirm the same Java, Gradle,
timezone, locale, Dockerfile, apt snapshot, and Linux package-tool versions.
