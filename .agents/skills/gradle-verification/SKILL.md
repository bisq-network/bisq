---
name: gradle-verification
description: "Change Bisq 1 build inputs under Gradle dependency verification: add, update or remove a dependency or Gradle plugin, edit gradle/libs.versions.toml, gradle/verification-metadata.xml or gradle/verification-keyring.keys, handle a Dependabot Gradle update, upgrade the Gradle wrapper, or fix \"Dependency verification failed\" and failures of resolveAndVerifyDependencies, verifyDependencySignaturePolicy, verifyGradleWrapperSecurity or verifyReleaseEnvironment. Use it in place of generic Gradle upgrade or audit guidance, which misses the project's checksum files and signature checks."
---

# Gradle Verification

The build verifies every dependency by PGP signature or SHA-256, and the wrapper by approved checksums. A build input that changes without its verification files turns `verifyReleaseBuild` red on every open pull request. The procedures are in [the Gradle README](../../../gradle/README.md). Follow them; this skill lists the decisions that are easy to get wrong.

## Dependencies and plugins

* A new dependency needs strong justification (see `AGENTS.md`). Prefer an existing library of the build.
* Follow [refreshing build dependencies](../../../gradle/README.md#verifying-and-refreshing-build-dependencies). Refresh the keyring before writing metadata, then set `<key-servers enabled="false"/>` again. Only when a missing entry stops Gradle during configuration, write the bootstrap metadata first as the README describes, then run the whole refresh sequence.
* Review every key the refresh adds to `gradle/verification-keyring.keys` and every new `<trusting>` entry. Check that the key belongs to the publisher of the artifact.
* A checksum-only artifact needs an allowlist entry with a rationale in `gradle/dependency-checksum-fallback-allowlist.tsv`. A version change of a checksum-only dependency, for example `bitcoinj` or `jsonrpc4j` from JitPack, replaces its entries. See [updating the allowlist](../../../gradle/README.md#updating-the-allowlist-after-a-dependency-change).
* JavaFX needs entries for every platform classifier by hand. See [updating JavaFX](../../../gradle/README.md#updating-javafx).
* Commit `libs.versions.toml`, `verification-metadata.xml`, the keyring when it changed, the allowlist and `docs/dependency-signature-report.md` together.
* Never make a failure pass by trusting a group, an artifact pattern or a key broadly, by removing a `reason`, by turning a signature check into a checksum, or by disabling verification. Report the failure instead.

## Gradle wrapper

* Follow [upgrading the Gradle version](../../../gradle/README.md#how-to-upgrade-the-gradle-version) step by step. A generic upgrade misses required steps, among them the `VALIDSIG` check against the release key fingerprint (the key changed with 9.7.1), `gradle/wrapper/gradle-wrapper.sha256`, `releaseBuild.gradleVersion` with the release checklist, and the trust for the Develocity plugin of the new version.
* Never hand-edit the wrapper jar or scripts. Generate them from the verified distribution, and update the approved checksums only for reviewed files.
* The README steps for the new version include verification entries: the release key in the keyring from 9.7.1 on and the Develocity plugin trust, which `--scan` builds need. A configuration failure caused by a missing one of these is part of the procedure; add it as the README describes.
* If the build fails to configure for any other reason, stop, restore every file the upgrade changed and report what breaks. Adapt build scripts only after the developer agrees.

## Verify

Run the tasks of the [related verification tasks](../../../gradle/README.md#related-verification-tasks) table that the change touches:

* dependency change: `resolveAndVerifyDependencies`, `verifyDependencySignaturePolicy` and `dependencySignatureReport`
* wrapper change: `shasum -a 256 -c gradle/wrapper/gradle-wrapper.sha256`, `verifyGradleWrapperSecurity`, `verifyReleaseEnvironment`, `resolveAndVerifyDependencies` and `./gradlew help --scan`, which is the only local run that shows a missing Develocity trust

"Configuration cache entry discarded" is expected for the dependency tasks; read the task output and `BUILD SUCCESSFUL`.
