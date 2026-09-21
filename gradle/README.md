# Gradle wrapper and dependency verification

This folder holds the pinned Gradle wrapper and the files for Gradle's dependency
verification.

| File | Purpose |
|---|---|
| `wrapper/gradle-wrapper.properties` | Gradle version and the SHA-256 of its distribution |
| `wrapper/gradle-wrapper.jar`, `../gradlew`, `../gradlew.bat` | the wrapper itself |
| `wrapper/gradle-wrapper.sha256` | approved checksums of the four files above |
| `verification-metadata.xml` | checksums and PGP keys of every dependency artifact |
| `verification-keyring.keys` | the public keys used to verify dependency signatures |
| `dependency-checksum-fallback-allowlist.tsv` | dependency artifacts accepted with a checksum only |

Currently pinned: **Gradle 9.0.0**, Java **21.0.6** from **Azul Systems, Inc.**
The Gradle and Java values are also pinned in `gradle.properties`
(`releaseBuild.gradleVersion`, `releaseBuild.javaVersion`, `releaseBuild.javaVendor`)
and repeated in `docs/release-checklist.md`.

## How to upgrade the Gradle version

### 1. Pick a version and verify its distribution

Look up the version on [gradle.org/releases](https://gradle.org/releases/). All addresses
follow the same pattern, and `https://services.gradle.org/versions/all` lists them per
version in the fields `downloadUrl`, `checksumUrl` and `wrapperChecksumUrl`.

```bash
V=9.0.0
B=https://services.gradle.org/distributions
curl -LO $B/gradle-$V-bin.zip
curl -LO $B/gradle-$V-bin.zip.sha256
curl -LO $B/gradle-$V-bin.zip.asc
curl -LO $B/gradle-$V-wrapper.jar.sha256

echo "$(cat gradle-$V-bin.zip.sha256)  gradle-$V-bin.zip" | sha256sum -c -
gpg --import <path to the checkout>/gradle/verification-keyring.keys
gpg --verify gradle-$V-bin.zip.asc gradle-$V-bin.zip
```

`gpg --verify` must print "Good signature from Gradle Inc.". Its remark that the key is not
certified with a trusted signature is normal; it only says that no owner trust was assigned.

The value in `gradle-<version>-bin.zip.sha256` is the one that belongs into
`distributionSha256Sum` in `wrapper/gradle-wrapper.properties`.

**About the signing key.** Gradle replaced its release key on 2026-08-08. Releases up to
and including 9.7.0 are signed by `1BD97A6A154E7810EE0BC832E2F38302C8075E3D`, which is the
key present in `verification-keyring.keys`. That key now carries a revocation with the
reason "key is superseded", not "key is compromised", so its earlier signatures keep their
meaning. Releases from **9.7.1** on are signed by
`EA96F38569C044AAEF7FCF732887F479B0B9771A`. When upgrading to 9.7.1 or newer, add that key
to `verification-keyring.keys` and let the new `gradle:gradle:<version>` entry in
`verification-metadata.xml` name it.

### 2. Write the wrapper files

Generate the four files with the distribution you just verified, in an empty directory, so
they cannot come from anywhere else:

```bash
unzip -q gradle-$V-bin.zip -d dist
mkdir gen && cd gen
echo "rootProject.name = 'wrapper-gen'" > settings.gradle
../dist/gradle-$V/bin/gradle wrapper --gradle-version $V --distribution-type bin \
    --gradle-distribution-sha256-sum <value from gradle-$V-bin.zip.sha256>
```

Check that the generated `gradle/wrapper/gradle-wrapper.jar` has the SHA-256 published in
`gradle-<version>-wrapper.jar.sha256`, then copy all four files into the project:

- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`

Keep the executable bit on `gradlew`.

You can instead run the `wrapper` task in the project itself:

```bash
./gradlew wrapper --gradle-version $V --distribution-type bin \
    --gradle-distribution-sha256-sum <value from gradle-$V-bin.zip.sha256>
```

Two details matter here:

- The checksum option is not optional. As long as `gradle-wrapper.properties` contains
  `distributionSha256Sum`, the task stops with "gradle-wrapper.properties contains
  distributionSha256Sum property, but the wrapper configuration does not have one".
- **The task has to run twice.** It always writes the jar and the two scripts of the Gradle
  version that is *running*, while it writes the new version into
  `gradle-wrapper.properties`. So the first run leaves the jar and the scripts at the old
  version, and only the second run, which already uses the new distribution, replaces them.
  Stopping after the first run is how this project ended up with a Gradle 8.9 wrapper jar
  next to a 9.0.0 distribution address, which turned every build red until it was corrected.

The way described above needs one run and ties the files to a distribution you verified
yourself.

### 3. Refresh the approved checksums

`wrapper/gradle-wrapper.sha256` lists the four files above. It is checked by the first step
of `.github/workflows/build.yml` and by the Gradle task `verifyGradleWrapperSecurity`.
**Changing the wrapper without updating this file turns every build red, including every
open pull request.**

```bash
head -2 gradle/wrapper/gradle-wrapper.sha256 > new.sha256
sha256sum gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar \
    gradle/wrapper/gradle-wrapper.properties >> new.sha256
mv new.sha256 gradle/wrapper/gradle-wrapper.sha256
sha256sum -c gradle/wrapper/gradle-wrapper.sha256
```

`.gitattributes` contains `*.bat text eol=crlf`, so `gradlew.bat` is stored with single line
feeds in Git and written with carriage return and line feed into the working directory. Take
its checksum from the checked out file, not from `git show`.

### 4. Update the pinned release environment

Set `releaseBuild.gradleVersion` in `gradle.properties` to the new version and change the
same value in `docs/release-checklist.md`. `verifyReleaseEnvironment` compares it with the
Gradle version that runs the build and fails if they differ. The release builder image needs
no change, because its Dockerfile runs `./gradlew`.

Five files are then ready to be committed, plus `gradle.properties` and the checklist.

### 5. Trust the Develocity plugin of the new Gradle version

The workflows run Gradle with `--scan`, and `--scan` makes Gradle apply the Develocity
plugin. **Which version it applies depends on the Gradle version**: Gradle 8.9 applied
`com.gradle:develocity-gradle-plugin:3.17.5`, Gradle 9.0.0 applies `4.1`. Dependency
verification then rejects the new version, because the signing key is trusted per version:

```text
Error resolving plugin [id: 'com.gradle.develocity', version: '4.1', ...]
> Dependency verification failed ...
    Artifact was signed with key '7B79ADD11F8A779FE90FD3D0893A028475557671'
    (Gradle Inc. <info@gradle.com>) and passed verification but the key isn't in
    your trusted keys list.
```

This happens during plugin resolution, so it stops the build before any task runs, and a
build without `--scan` does not show it. Reproduce it with `./gradlew help --scan`.

Add the new version to `verification-metadata.xml`: a `<trusting>` line for that key and a
`<component>` entry with the checksums of the jar and the module. Take the artifacts from
`https://plugins.gradle.org/m2/com/gradle/develocity-gradle-plugin/<version>/`, verify the
`.asc` signatures against the key above, then record their SHA-256 values.

## Dependency verification

To update the verification metadata without PGP signature metadata run:

- `./gradlew resolveAndVerifyDependencies --write-verification-metadata sha256`

To refresh dependency PGP signature verification metadata run:

- `./gradlew refreshDependencyVerificationKeyring`
- `./gradlew resolveAndVerifyDependencies --write-verification-metadata pgp,sha256`

Refresh the keyring first so newly available signing keys are present before Gradle rewrites
verification metadata. This avoids recording checksum-only fallbacks for artifacts whose
signatures can now be verified.

This refreshes Gradle's dependency verification keys from the configured key servers, exports
the armored keyring, resolves every resolvable Gradle configuration, enables
`verify-signatures` in `verification-metadata.xml`, configures the armored keyring format,
records trusted PGP keys for signed artifacts, and keeps SHA-256 checksums with
`reason="Artifact is not signed"` for artifacts whose publishers do not provide detached
signatures. The exported armored keyring (`verification-keyring.keys`) provides the public
keys Gradle uses for signature verification and the signer identity data used by the report.

The `refreshDependencyVerificationKeyring` task wraps
`./gradlew help --refresh-keys --export-keys`, so it refreshes Gradle's dependency
verification keys from the configured key servers and exports the armored keyring. Some
Gradle and Bouncy Castle combinations may write the keyring file and then fail while
finishing key export; the task accepts that case only when the armored keyring was updated.
The report task only needs `verification-keyring.keys` for signer names, emails and key
creation dates.

Dependency verification is configured for the armored keyring, so a binary keyring
(`verification-keyring.gpg`) is not used. The project does not contain one, and
`refreshDependencyVerificationKeyring` warns if one appears.

To verify the current metadata without rewriting it run:

- `./gradlew resolveAndVerifyDependencies`

To regenerate the dependency signature report run:

- `./gradlew dependencySignatureReport`

It writes `docs/dependency-signature-report.md`.

Checksum-only artifacts must be listed in `dependency-checksum-fallback-allowlist.tsv` with
an artifact-level review rationale:

```text
<group:name:version>\t<artifact-file-name>\t<review-rationale>
```

Keep the list exact and sorted by module and artifact. `verifyDependencySignaturePolicy`
fails when a checksum-only artifact is missing from the allowlist, when an allowlist entry is
stale, or when an entry has no rationale.

## A note on the configuration cache

`org.gradle.configuration-cache=true` is set in `gradle.properties`.
`resolveAndVerifyDependencies`, `verifyDependencySignaturePolicy` and
`dependencySignatureReport` resolve every configuration of every project while they run,
which the configuration cache does not allow. They declare themselves as not compatible with
it, so Gradle runs them without the cache and prints a line such as

```text
Configuration cache entry discarded with 41 problems.
```

That line is expected for these tasks and does not mean the task failed. Look at the task
output and at `BUILD SUCCESSFUL` instead. The same applies to `generateJarHashes` and
`generateReleaseManifest` on the release evidence path.

## Related verification tasks

| Task | Checks |
|---|---|
| `verifyGradleWrapperSecurity` | wrapper checksums and the pinned distribution address |
| `verifyReleaseEnvironment` | running Gradle and Java against the pins in `gradle.properties` |
| `verifyDependencySignaturePolicy` | every checksum-only artifact is in the allowlist |
| `verifyGithubActionsSecurity` | workflows pin actions by commit hash and use no floating versions |
| `verifyReleaseBuild` | runs the full release payload verification and writes the evidence |
