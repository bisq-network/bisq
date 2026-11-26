# GitHub Actions Workflows

This page describes every workflow in [`.github/workflows`](../.github/workflows): when it runs, what each job and
step does, which Gradle tasks and scripts it calls, what it uploads and when it fails. Action versions, image
digests and JDK patch versions change with Dependabot updates. Read them in the workflow files, not here.

## Overview

| File | Name in the Actions tab | Runs on | Purpose |
|---|---|---|---|
| [`build.yml`](../.github/workflows/build.yml) | Build Bisq | every push, every pull request | Builds, tests and runs the release gates on Linux, macOS and Windows; uploads Java release evidence |
| [`codeql.yml`](../.github/workflows/codeql.yml) | CodeQL | push and pull request to `master` and `release-candidate-*`, weekly, manual | CodeQL analysis of the Java code and of the workflow files |
| [`contribution-notice.yml`](../.github/workflows/contribution-notice.yml) | Contribution notice | new issue, new pull request | Posts the security and contribution notice as a comment |
| [`e2e-tests.yml`](../.github/workflows/e2e-tests.yml) | e2e-tests | pull requests that touch the listed paths, manual | DAO and trade end-to-end tests against a Docker regtest network |
| [`e2e-tests-compat.yml`](../.github/workflows/e2e-tests-compat.yml) | e2e-tests-compat | manual | The end-to-end tests with one peer on a release version |
| [`installer-evidence.yml`](../.github/workflows/installer-evidence.yml) | Installer Evidence | manual | Builds the installers of each platform and uploads installer evidence |
| [`installers.yml`](../.github/workflows/installers.yml) | Build Installers | manual, called by Build and Smoke Test Installers | Builds the release installers and uploads them with checksums |
| [`installer-smoke-test.yml`](../.github/workflows/installer-smoke-test.yml) | Smoke Test Installers | manual, called by Build and Smoke Test Installers | Installs each installer and starts Bisq until Tor, P2P network and wallet are ready |
| [`installers-build-and-smoke-test.yml`](../.github/workflows/installers-build-and-smoke-test.yml) | Build and Smoke Test Installers | manual | Runs Build Installers, then Smoke Test Installers on its output |
| [`release-builder-image.yml`](../.github/workflows/release-builder-image.yml) | Release Builder Image | push and pull request that change the release-builder image | Builds the Linux release-builder image and tests its apt snapshot guard |
| [`release-builder.yml`](../.github/workflows/release-builder.yml) | Linux Release Builder | manual | Builds the same commit twice in the release-builder image and compares the evidence |

To start a manual workflow, open the Actions tab, select the workflow, click `Run workflow` and select the branch or
tag. Only users with write access to the repository can start one.

The Actions tab also lists workflows that have no file on `master`. GitHub creates some of them from the repository
settings, for example `Copilot code review`, `Dependabot Updates`, `Automatic Dependency Submission`, `CodeQL` (the
default setup) and `CodeQL - Code Quality`. Others come from workflow files that are not on `master`. This page does not
cover them.

## Common Rules

These rules apply to the workflow files. `verifyGithubActionsSecurity` enforces only part of them; see
[below](#verifygithubactionssecurity).

- Every external action is pinned to a full commit SHA, with the version tag as a comment.
- `actions/checkout` runs with `persist-credentials: false`. The checkout does not store the `GITHUB_TOKEN` in
  `.git/config`, so the build and the tests cannot read it from there.
- `actions/setup-java` requests Java `21` from `distribution: corretto` with `check-latest: true`. Each runner gets
  the newest Corretto 21 build for its platform. Where a workflow runs `verifyReleaseBuild` or the installer
  evidence tasks, `verifyReleaseEnvironment` checks that build against the minimum in `gradle.properties` (see
  [the Gradle README](../gradle/README.md)). Build Installers, CodeQL and the end-to-end workflows do not run it.
- Runner labels are fixed versions such as `ubuntu-24.04`, never `*-latest`.
- Each workflow sets its token permissions explicitly. All except Contribution notice grant `contents: read`, plus
  only what a job needs.
- Workflows that run Gradle on the runner first check the Gradle wrapper against
  `gradle/wrapper/gradle-wrapper.sha256` (`sha256sum -c`, or `shasum -a 256 -c` where `sha256sum` is missing). The
  file lists `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` and
  `gradle/wrapper/gradle-wrapper.properties`. `e2e-tests.yml` and `e2e-tests-compat.yml` skip this step.
- `--scan` is passed in Build Bisq, Installer Evidence and Build Installers. No Build Scan is published. The logs
  of Build Bisq and Build Installers report "The Build Scan was not published due to a configuration problem. The
  Gradle Terms of Use have not been agreed to." `build.gradle` accepts the terms only when a `buildScan`
  extension exists.
- Artifacts without `retention-days` use the retention period of the repository settings. With
  `if-no-files-found: error` an upload fails only if none of the listed files exists. A single missing file is
  skipped. The Gradle tasks that write the evidence files check that each one exists.
- A `concurrency` group without cancellation lets one run per group execute and keeps one run pending. A newer run
  replaces the pending one, which GitHub cancels.

### verifyGithubActionsSecurity

`verifyReleaseBuild` runs `verifyGithubActionsSecurity`, so Build Bisq fails when a workflow breaks one of the rules
below. It does not check permissions, the wrapper checksum step or artifact settings. The task reads every `*.yml`
and `*.yaml` file in `.github/workflows` line by line and reports:

- a `uses:` reference other than a local `./` path without a 40-character commit SHA
- an `actions/checkout` step without `persist-credentials: false`
- an `actions/setup-java` step without `distribution: corretto` or without `check-latest: true`
- `ubuntu-latest`, `macos-latest` or `windows-latest` on any line, comments included, in any letter case
- a literal `java-version:` or `java: [...]` matrix value other than the feature version of
  `releaseBuild.javaVersion` (`21`); expressions such as `${{ matrix.java }}` are not checked
- a `release-builder.yml` that lacks the expected steps: its name, the manual trigger, the runner, the image build,
  the two worktrees with submodules, the mounts, both evidence runs, both comparisons and both uploads, or that
  mounts the whole checkout into the container
- a missing `concurrency` group with `cancel-in-progress: false` in `release-builder.yml` and
  `installer-evidence.yml`

## Build Bisq (`build.yml`)

**Triggers.** `push` to any branch or tag, and `pull_request` to any base branch. A pull request from a branch of
the same repository therefore runs twice: once for the push and once for the pull request. There is no concurrency
group, so a new push does not cancel a running build.

**Permissions.** `contents: read`.

**Job `build`.** A matrix of `ubuntu-24.04`, `macos-15` and `windows-2025` with Java `21`. The job name is
`Test Java 21, <os>`. The matrix uses the default `fail-fast`, so a failure on one platform cancels the jobs still
running on the others.

1. Checks out the commit with submodules.
2. Checks the Gradle wrapper checksums.
3. Installs Corretto 21.
4. Runs `gradle/actions/setup-gradle` with `cache-provider: external`. The action prepares Gradle but does not
   cache. The next step caches instead.
5. Restores `~/.gradle/caches` and `~/.gradle/wrapper`. The key is
   `build-gradle-<os>-<arch>-<hash of **/*.gradle*, **/gradle-wrapper.properties, **/gradle/*.versions.toml>`.
   Without an exact match, the latest cache with the prefix `build-gradle-<os>-<arch>-` is restored. A changed build
   file therefore does not start with an empty cache. That would download every dependency again and hit the
   Maven Central rate limit (HTTP 429).
6. Runs `./gradlew verifyReleaseBuild --scan`. See below.
7. Only for a push to the default branch without an exact cache hit: stops the Gradle daemon, then saves the cache
   under the exact key. Pull requests and other branches only read the cache.
8. Uploads the artifact `release-manifests-<os>-java-21` with `SHA256SUMS`, `release-manifest.tsv`,
   `build-info.json` and `release-evidence.zip` from `build/reports/release/`.

**What `verifyReleaseBuild` runs.** The task is defined in [`build.gradle`](../build.gradle). The
[reproducible builds documentation](reproducible-builds/technical.md) describes the evidence format.

- `verifyReleaseEnvironment`: the Gradle version must equal `releaseBuild.gradleVersion`. The Java vendor must equal
  `releaseBuild.javaVendor`. The Java version must have the same feature version as `releaseBuild.javaVersion` and
  be at or above it. All three values are in `gradle.properties`.
- `verifyGradleWrapperSecurity`: the same four wrapper checksums as step 2, plus a `distributionUrl` on
  `https://services.gradle.org/distributions/` and a pinned `distributionSha256Sum`.
- `verifyReleaseBuilderImage`: a static check of `docker/release-builder/linux/Dockerfile`. The base image must be
  pinned by digest and the Corretto archive by version and checksum. The apt snapshot must be configured with its
  guards, the required packages and environment variables must be set, and the default command must be
  `./gradlew verifyReleaseBuild`. It does not build the image. Release Builder Image does that.
- `verifyGithubActionsSecurity`: see [above](#verifygithubactionssecurity).
- `verifyDependencySignaturePolicy`: resolves every configuration under Gradle dependency verification
  (`gradle/verification-metadata.xml`). It fails if the metadata does not turn the key servers off, if an artifact
  verified only by checksum is missing from `gradle/dependency-checksum-fallback-allowlist.tsv`, or if an allowlist
  entry is no longer needed.
- `verifyReleaseEvidenceBundle`: writes the jar hashes, `release-manifest.tsv`, `SHA256SUMS`, `build-info.json` and
  `release-evidence.zip`, then checks that the ZIP holds the required entries.
- `build` of every project: compilation, unit tests and the other `check` tasks. `:apitest:test` is skipped,
  because it runs only with `-DrunApiTests=true`. The end-to-end workflows run it.
- `verifyReproducibleArchives` of every project: every archive task, TAR included, must disable file timestamps
  and use reproducible file order. Every JAR and ZIP must also have no duplicate entries and give all entries the
  same timestamp.

## CodeQL (`codeql.yml`)

**Triggers.** `push` to `master` and `release-candidate-*`, `pull_request` with one of these as base, Mondays at
03:27 UTC, and manual.

**Concurrency.** Group `codeql-<ref>` with `cancel-in-progress: true`. A new run on the same ref cancels the older
one.

**Permissions.** `contents: read` for the workflow. The job sets `actions: read`, `contents: read` and
`security-events: write` to upload results.

**Job `analyze`.** Runs on `ubuntu-24.04` with a 60-minute timeout. The matrix runs `java-kotlin` with
`build-mode: manual` and `actions` with `build-mode: none`. Neither matrix job cancels the other when it fails.

1. Checks out the commit. Submodules only for `java-kotlin`.
2. For `java-kotlin` only: checks the Gradle wrapper checksums, installs Corretto 21, and runs `setup-gradle` with
   `cache-provider: basic`. By default the basic cache is written only on the default branch.
3. Initializes CodeQL for the language.
4. For `java-kotlin` only: runs `./gradlew --no-daemon --no-build-cache classes`. CodeQL extracts only the code
   that the compiler sees. `--no-build-cache` makes sure every main source set is compiled, also when a
   developer setting turns the build cache on. Test sources are not compiled.
5. Runs the analysis and uploads the results to code scanning with the category `/language:<language>`.

The file replaces the CodeQL default setup, which cannot check out the submodules or select the Corretto JDK. The
default setup and this workflow cannot both upload results for the same language. The default setup is switched
on or off in the repository settings, not in this file.

## Contribution notice (`contribution-notice.yml`)

**Triggers.** `issues` with type `opened` and `pull_request_target` with type `opened`. `pull_request_target` runs
the workflow of the base repository with its token, which is what allows a comment on a pull request from a fork.
The job does not check out or run code from the pull request.

**Permissions.** `issues: write` and `pull-requests: write`.

**Job `comment`.** Runs only in `bisq-network/bisq`, so forks skip it. Runs on `ubuntu-24.04` with a 5-minute
timeout. The single step calls `gh api` to post the notice as a comment on the issue or pull request. The number
comes from the event through an environment variable. The notice tells the reader:

- to report security issues privately through the Bisq Security Advisories page and to keep security details out
  of public issues, pull requests and comments
- that paid development for new contributors and routine review of their pull requests are paused, that they should
  ask maintainers about priorities, review capacity and funding first, and that compensation needs DAO approval

The comment ends with the HTML marker `<!-- bisq-contribution-notice -->`. On a re-run (run attempt above 1) the step
looks for a comment by `github-actions[bot]` with that marker and posts nothing if one exists. The job does not filter
by author, so it does not skip bot pull requests either.

## e2e-tests (`e2e-tests.yml`)

**Triggers.**

- `pull_request` when it changes a path below `apitest/`, `core/`, `daemon/`, `cli/`, `seednode/`, `proto/`,
  `common/`, `p2p/`, `persistence/`, `assets/`, `bridge/`, `bitcoind/` or `build-logic/`, or one of the files
  `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/libs.versions.toml` and
  `.github/workflows/e2e-tests.yml`. It does not run on `push`.
- Manual, with the boolean input `run_long_running_tests` (default `false`).

**Permissions.** `contents: read`.

**Job `e2e-tests`.** Runs on `ubuntu-22.04` without a job timeout. The test script has its own limit of one hour.

1. Checks out the commit with submodules. The Gradle wrapper checksums are not checked.
2. Installs Corretto 21.
3. Caches `~/.gradle/caches` and `~/.gradle/wrapper` with `actions/cache`. The key is
   `<os>-gradle-<hash of **/*.gradle*, **/gradle-wrapper.properties>`, with a prefix fallback. The cache is saved
   at the end of a successful job when there was no exact hit, also for pull requests. GitHub scopes such a cache
   to the pull request. The key differs from the one of Build Bisq, so the two never share a cache.
4. Runs `./gradlew --no-daemon :seednode:installDist :daemon:installDist :cli:installDist :apitest:installDaoSetup`.
   `installDaoSetup` (in [`apitest/dao-setup.gradle`](../apitest/dao-setup.gradle)) downloads `dao-setup.zip`
   from `https://github.com/bisq-network/bisq/raw/master/docs/dao-setup.zip` when
   `apitest/src/main/resources/dao-setup.zip` does not exist. On CI it never exists, so the job always tests the
   `dao-setup.zip` of `master` and not the copy in the checked-out commit. It then unzips the regtest chain and the
   app data directories of Alice and Bob into `apitest/src/main/resources/`.
5. Sets up Docker Buildx and caches its layers in `/tmp/.buildx-cache`. The key is the hash of
   `apitest/docker/Dockerfile.*` and `apitest/docker/*entrypoint.sh`.
6. Builds two images:
   - `bisq-bitcoind:ci` from [`Dockerfile.bitcoind`](../apitest/docker/Dockerfile.bitcoind): a pinned bitcoind image
     with the regtest chain of `dao-setup.zip`.
   - `bisq-daemon:ci` from [`Dockerfile.bisq`](../apitest/docker/Dockerfile.bisq): a pinned Amazon Corretto 21
     image. It holds the install distributions of seed node, daemon and CLI, the Linux JavaFX jars downloaded from
     Maven Central, and the app data directories of Alice and Bob. The seed node starts through
     `RegtestSeedNode.java`.
7. Runs `apitest/docker/run-e2e-tests.sh --skip-build --skip-docker-build` with `RUN_LONG_RUNNING_TESTS` set from
   the input. On a pull request the input is empty and the script uses `false`. See below.
8. Always uploads `apitest/docker/logs` as `e2e-tests-logs` and `apitest/build/reports/tests` as
   `e2e-tests-reports`, both kept for 7 days.

**What `run-e2e-tests.sh` does.** The script is described in [`apitest/docker/README.md`](../apitest/docker/README.md).
In CI it:

1. Checks for Docker and the Compose v2 plugin. It fails if a port it needs is in use by something other than an
   old stack: bitcoind RPC `18443`, bitcoind P2P `18444`, seed node blind-vote counts `5124`, and gRPC
   `9997`-`9999`.
2. Removes containers and volumes of an earlier stack and checks that both images exist.
3. Starts five containers with [`dao-compose.yml`](../apitest/docker/dao-compose.yml): `bitcoind`, `seednode`, `arb`
   (mediator and refund agent), `alice` and `bob`. It waits up to 240 s until `bitcoind` and `seednode` pass their
   health checks and the other containers run, and until each daemon answers `getversion` on gRPC (up to 300 s each).
4. Shared-stack phase: one Gradle run of `:apitest:test` with `--tests bisq.apitest.method.*` and
   `--tests bisq.apitest.dao.*`. It excludes the classes tagged `freshstack`.
5. Fresh-stack phase: each class tagged `@Tag("freshstack")`, except the ones also tagged `longrunning`, gets a new
   stack (containers and volumes removed and started again) and its own Gradle run.
6. Policy phase: two runs on a new stack each. With `--denyListResource=denylist/btc_regtest_e2e.denylist` on all Bisq
   containers, the seed node included, it runs `DenyListBlocksCreateOfferTest`. With `--ignoreDenyList=true` added it
   runs `IgnoreDenyListAllowsCreateOfferTest`.
7. Collects the container logs and removes the stack.

If `RUN_LONG_RUNNING_TESTS` is `true`, only the classes tagged `longrunning` run, each in a new stack. The
shared-stack and policy phases are skipped. A run that exceeds 3600 s is stopped and fails with exit code 124. A
failed test run does not stop the later phases. A stack that fails to start stops the script in the policy phase
but only skips the class in the fresh-stack phase. The job fails if any phase failed.

Every Gradle run writes its HTML report to `apitest/build/reports/tests/test`, so the uploaded copy there holds only
the last run. The script keeps a copy per fresh-stack class in `apitest/build/reports/tests/freshstack`, which is
uploaded. The policy reports are copied to `apitest/build/reports/policy`, which is not uploaded. When a phase fails,
the script saves the container logs of that phase under `apitest/docker/logs`, which is uploaded.

## e2e-tests-compat (`e2e-tests-compat.yml`)

Runs the end-to-end tests with one peer on a release build and the other peers on the dispatched commit. It checks
P2P, DAO and trade compatibility between the two versions.

**Trigger.** Manual only, with these inputs:

- `release_ref`: tag or ref of the release peer. When it is empty, the workflow uses `git describe --tags --abbrev=0`,
  the most recent tag reachable from the dispatched commit. If the dispatched commit itself is tagged, both sides
  run the same code.
- `release_peer`: `bob` (taker, default) or `alice` (maker).
- `run_long_running_tests`: run only the classes tagged `longrunning`. It is rejected with `release_peer=alice`.

**Permissions.** `contents: read`.

**Job `e2e-tests-compat`.** Runs on `ubuntu-22.04`.

1. Checks out the commit with submodules, full history and tags. The Gradle wrapper checksums are not checked,
   neither for this tree nor for the release tree.
2. Installs Corretto 21 and caches Gradle like e2e-tests.
3. Builds the install distributions and `dao-setup` of the dispatched commit, as in e2e-tests.
4. Resolves the release ref. A given `release_ref` is passed through an environment variable, never placed into
   the script, and must resolve to a commit. The step adds a detached worktree of the ref under `$RUNNER_TEMP`,
   initializes its submodules and builds its install distributions with the wrapper of that tree. It copies them
   to `apitest/docker/.release-dist`.
5. Builds `bisq-bitcoind:ci` and `bisq-daemon:ci` as in e2e-tests. It then builds `bisq-daemon:release` from
   [`Dockerfile.bisq-release`](../apitest/docker/Dockerfile.bisq-release) on top of `bisq-daemon:ci`, which only
   replaces the seed node, daemon and CLI binaries with the release ones.
6. Runs `run-e2e-tests.sh --skip-build --skip-docker-build` with the policy phase off, because a release peer does
   not know the `--denyListResource` option:
   - `bob`: adds [`dao-compose.compat.yml`](../apitest/docker/dao-compose.compat.yml), which runs `bob` on the
     release image. The shared-stack and fresh-stack phases run as in e2e-tests.
   - `alice`: adds [`dao-compose.compat-alice.yml`](../apitest/docker/dao-compose.compat-alice.yml), which runs
     `alice` on the release image. The shared-stack phase is skipped, and the fresh-stack phase runs only the
     classes listed in [`compat-alice-tests.txt`](../apitest/docker/compat-alice-tests.txt).
7. Always uploads `e2e-tests-compat-logs` and `e2e-tests-compat-reports`, both kept for 7 days.

[`run-e2e-tests-compat.sh`](../apitest/docker/run-e2e-tests-compat.sh) does the same locally.

## Installer Evidence (`installer-evidence.yml`)

**Trigger.** Manual only.

**Concurrency.** Group `installer-evidence-<ref>` without cancellation.

**Permissions.** `contents: read`.

**Jobs.** Three independent jobs: `linux-installer-evidence` on `ubuntu-24.04`, `macos-installer-evidence` on
`macos-15` (Apple Silicon) and `windows-installer-evidence` on `windows-2025`. Each job:

1. Checks out the commit with submodules and checks the Gradle wrapper checksums.
2. Linux only: installs the packaging and inspection tools (`binutils`, `cpio`, `dpkg`, `dpkg-dev`, `fakeroot`,
   `file`, `gzip`, `rpm`, `tar`, `xz-utils`, `zip`). Windows only: installs WiX Toolset 3.14.1.20250415 with
   Chocolatey and adds its `bin` directory to `PATH`.
3. Installs Corretto 21 and runs `setup-gradle` with `cache-provider: basic`.
4. Runs `./gradlew verifyInstallerEvidenceBundle --scan`. The chain of tasks is:
   - `generateInstallerManifest` runs `verifyReleaseEnvironment` and `:desktop:generateInstallers`, then writes
     `installer-manifest.tsv` and `INSTALLER-SHA256SUMS`.
   - `generateInstallerStructureReport` writes reports about the package contents where the local tools allow it.
   - `generateInstallerEvidenceBundle` packs everything into `installer-evidence.zip`.
   - `verifyInstallerEvidenceBundle` checks that the ZIP holds the required entries.
5. Uploads `installer-evidence-ubuntu-24.04-java-21`, `installer-evidence-macos-15-java-21` or
   `installer-evidence-windows-2025-java-21` with `INSTALLER-SHA256SUMS`, `installer-manifest.tsv`,
   `installer-build-info.json`, `installer-structure-summary.txt`, `installer-structure-report.tsv`, the
   `installer-structure/` directory and `installer-evidence.zip`.

The workflow does not run the Java release gates (`verifyReleaseBuild`) and does not compare two builds. It has no macOS
Intel job. Each runner builds only its own installer formats: DEB and RPM on Linux, DMG on macOS, EXE on Windows. See
[usage](reproducible-builds/usage.md) for how to compare the evidence.

## Build Installers (`installers.yml`)

Builds the installers a release ships. It checks that they build. It does not check reproducibility and does not
sign anything.

**Triggers.** Manual, and `workflow_call` from Build and Smoke Test Installers.

**Concurrency.** Group `installers-<ref>` without cancellation.

**Permissions.** `contents: read`.

**Job `installers`.** A matrix with `fail-fast: false`, so a failed platform does not cancel the others:

| Job | Runner | Artifact | Expected files |
|---|---|---|---|
| Linux DEB and RPM | `ubuntu-24.04` | `installers-linux-x86_64` | `bisq_*-1_amd64.deb`, `bisq-*-1.x86_64.rpm` |
| macOS Apple Silicon DMG | `macos-15` | `installers-macos-aarch64` | `Bisq-aarch64-*.dmg` |
| macOS Intel DMG | `macos-15-intel` | `installers-macos-x86_64` | `Bisq-x86_64-*.dmg` |
| Windows EXE | `windows-2025` | `installers-windows-x86_64` | `Bisq-*.exe` |

1. Checks out the commit with submodules and checks the Gradle wrapper checksums.
2. Linux: installs the same packaging tools as Installer Evidence. Windows: installs WiX Toolset 3.14.1.20250415.
3. Installs Corretto 21 and runs `setup-gradle` with `cache-provider: external`.
4. Restores the Gradle cache that Build Bisq saves, with the same key and fallback. Build Bisq has no macOS Intel
   job, so the macOS Intel job starts without a cache. This workflow never saves a cache.
5. Runs `./gradlew :desktop:generateInstallers --scan`. Each platform runs `jpackage`. On Linux the task builds both the
   DEB and the RPM, relaxes the dependency names of the DEB and repacks the RPM deterministically. The output goes to
   `desktop/build/packaging`.
6. For each expected pattern, requires exactly one non-empty file. The DMG name contains the architecture of the
   build JVM, so this step also catches a runner that built for the other architecture. The step copies the files
   to `$RUNNER_TEMP/installer-upload` and writes `SHA256SUMS-<artifact>`. It adds the commit and the checksums to
   the job summary.
7. Uploads the directory as `<artifact>-<commit SHA>`, kept for 5 days.

Downloading these artifacts needs a GitHub login. They are for developers and are not release downloads.

## Smoke Test Installers (`installer-smoke-test.yml`)

Installs each installer from a Build Installers run and starts Bisq with a new data directory. It waits until Tor
runs in its sandbox, the onion service is published, the wallet is set up and Bisq reports the P2P network as
ready. Bisq does so when it has loaded the initial data from a seed node, and also when it finds no seed node or
peer, so this line alone does not prove that data arrived. It does not wait for the wallet or the DAO state to
synchronize.

**Triggers.**

- Manual, with `build-run-id` (required: the ID of a Build Installers or Build and Smoke Test Installers run) and
  `timeout-seconds` (default 600).
- `workflow_call` with the same inputs. An empty `build-run-id` means the installers of the calling run.

**Permissions.** `contents: read`, and `actions: read` to download the artifacts of another run.

**Concurrency.** Group `installer-smoke-test-<ref>` without cancellation.

**Job `smoke-test`.** A matrix with `fail-fast: false` and a 30-minute timeout per job: Linux DEB and Linux RPM on
`ubuntu-24.04`, macOS Apple Silicon DMG on `macos-15`, macOS Intel DMG on `macos-15-intel`, Windows EXE on
`windows-2025`. `JAVA_TOOL_OPTIONS=-Dprism.order=sw` selects JavaFX software rendering, because the runners have no
GPU.

1. Fails unless `timeout-seconds` is a positive whole number.
2. With a `build-run-id`: the ID must be numeric. The run, read through the GitHub API, must belong to this
   repository, have been started by `workflow_dispatch` from `installers.yml` or
   `installers-build-and-smoke-test.yml`, and have a 40-character head SHA. An artifact name and its own checksums
   alone do not prove which workflow built it.
3. Downloads the artifact `<artifact>-<SHA>` of that run, or of the current run when called without an ID.
4. Checks the files against `SHA256SUMS-<artifact>`.
5. Installs the package:
   - DEB: `apt-get install` of the package together with `xvfb`, `xauth` and `libgtk-3-0t64`.
   - RPM: `rpm -i --nodeps` on Ubuntu, together with `xdg-utils`, the one package which the RPM requires. This
     tests the package contents, not its dependencies on a Red Hat based system. `rpm` only warns when a script of
     the package fails, so the step fails on that warning.
   - DMG: mounts the image read-only and copies `Bisq.app` to `$RUNNER_TEMP`.
   - EXE: silent installation with `/quiet /norestart` and a verbose installer log, limited to 10 minutes.

   On Linux the job first creates `/usr/share/applications` and `/usr/share/desktop-directories`. The scripts of
   the packages add the menu entry with `xdg-desktop-menu`, which fails without these directories of a desktop
   system, and the runner has no desktop.

   The step fails if the installed `Bisq` executable is missing.
6. Starts Bisq, waits and stops it in one step, so that no process has to outlive its step. On Windows the runner
   ends the processes which a step leaves behind if they hold its output streams.
   - Starts Bisq with `--appDataDir=$RUNNER_TEMP/bisq-data --acceptTerms=true`. On Linux it runs under `xvfb-run`.
   - Reads `bisq.log` every 5 seconds. The job passes once the log contains all three lines
     `Tor runs in the level `, `Tor hidden service published after` and
     `walletInitialized=true, p2pNetWorkReady=true`. It fails at once on a line containing `] ERROR `, when the
     process exits, or when the timeout passes. The job summary shows the result and the reason.
   - Stops Bisq when the step ends. On Windows it also stops the `tor` process.
7. On failure, copies only the `*.log*` files of the data directory (the data directory also holds the wallet and
   onion service keys). It uploads them with the launcher output and, for the RPM and on Windows, the output of
   the installation as `smoke-test-logs-<artifact>-<package>-<SHA of this run>`, kept for 5 days.

## Build and Smoke Test Installers (`installers-build-and-smoke-test.yml`)

**Trigger.** Manual only, with `timeout-seconds` (default 600).

**Permissions.** `contents: read` and `actions: read`. Called workflows get at most these permissions.

**Concurrency.** Group `installers-build-and-smoke-test-<ref>` without cancellation.

**Jobs.**

1. `check-timeout` fails unless `timeout-seconds` is a positive whole number, so a wrong value stops the run
   before the installers are built.
2. `build` runs Build Installers.
3. `smoke-test` runs Smoke Test Installers on the installers of this run. It also runs when a platform build
   failed, so that the other platforms are still tested. The smoke test of the failed platform then reports that
   its installer is missing. It is skipped when the run was cancelled or `check-timeout` failed.

## Release Builder Image (`release-builder-image.yml`)

Linux Release Builder runs only by hand. This workflow builds its image whenever the image changes, so a broken
image is found before a release. An example is a Dependabot update of the base image to packages newer than
`UBUNTU_APT_SNAPSHOT`.

**Triggers.** `push` to any branch and `pull_request`, when `docker/release-builder/**` or the workflow file
changes. Tag pushes are left out, because path filters do not apply to them.

**Permissions.** `contents: read`.

**Job `release-builder-image`.** Runs on `ubuntu-24.04` with a 30-minute timeout. It does not use Gradle.

1. Checks out the commit without submodules.
2. Builds `docker/release-builder/linux` as `bisq-release-builder-linux:java-21` with `--pull=false`.
3. Builds the image three more times with a bad `UBUNTU_APT_SNAPSHOT`: empty, `20261111` (not a full timestamp)
   and `20260501T000000Z` (older than the packages of the base image). Each build must fail with the message of the
   Dockerfile guard. A build that fails for another reason does not count. `verifyReleaseBuilderImage` checks only
   that the guard text exists. This step checks that the guard works.

## Linux Release Builder (`release-builder.yml`)

Checks that two clean builds of the same commit in the pinned Linux release-builder image produce the same Java and
installer manifests. The manifests hold the path, size and SHA-256 of each file. `build-info.json` and the other
diagnostic files are not compared. [Linux reproducible builds](reproducible-builds/linux.md) describes the image and the
local equivalent.

**Trigger.** Manual only.

**Concurrency.** Group `release-builder-<ref>` without cancellation.

**Permissions.** `contents: read`.

**Job `linux-release-builder`.** Runs on `ubuntu-24.04`.

1. Checks out the commit with submodules and checks the Gradle wrapper checksums.
2. Builds the image `bisq-release-builder-linux:java-21` from
   [`docker/release-builder/linux/Dockerfile`](../docker/release-builder/linux/Dockerfile). It uses Ubuntu 24.04
   pinned by digest and a Corretto JDK pinned by version and SHA-256, and installs apt packages from a fixed
   snapshot. It sets `SOURCE_DATE_EPOCH=0`, `TZ=UTC`, `LANG` and `LC_ALL` to `C.UTF-8`, and puts the Gradle home in
   `/tmp/bisq-home/.gradle`.
3. Adds two detached worktrees, `build/release-builder-worktrees/a` and `b`, at `$GITHUB_SHA`, each with all
   submodules.
4. Runs `./gradlew clean verifyReleaseBuild verifyInstallerEvidenceBundle` in each worktree in its own container,
   as the runner user. The container sees only `.git` and its own worktree, not the whole checkout. The Gradle home
   is inside the container, so each build downloads Gradle and all dependencies again.
5. Runs `compareReleaseEvidenceBundles` and then `compareInstallerEvidenceBundles`, each in its own container that sees
   `.git` and `a`, and `b` read-only. Each compares the manifest of `a` with the manifest of `b` and fails on a missing,
   extra or changed file.
6. Uploads `release-builder-linux-java-21` (`SHA256SUMS`, `release-manifest.tsv`, `build-info.json`,
   `release-evidence.zip` of `a`) and `release-builder-linux-installers-java-21` (the installer evidence files of
   `a`).

`verifyGithubActionsSecurity` fails Build Bisq if this file loses the text of most steps above. It does not check
the wrapper checksum step, the checkout with submodules or the `--user` and `--platform` options.

## Related Configuration

- [`.github/dependabot.yml`](../.github/dependabot.yml) asks Dependabot for weekly updates, with a 7-day cooldown,
  of the GitHub Actions in the workflows and of the Docker base images in `apitest/docker`,
  `docker/release-builder/linux` and `build-logic/docker-image-builder/src/main/resources`. Major updates of
  `amazoncorretto` and `ubuntu` are ignored. Its pull requests run the workflows above like any other pull request.
  If a base image update of the release builder brings packages newer than `UBUNTU_APT_SNAPSHOT`, Release Builder
  Image fails until the snapshot is moved.
- [`.github/boring-cyborg.yml`](../.github/boring-cyborg.yml) is not a workflow. It configures the Boring Cyborg
  GitHub App: labels `in:altcoins` and `is:no-priority` for pull requests that change `assets/`, and a comment on a
  contributor's first merged pull request.
