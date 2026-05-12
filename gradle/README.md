# How to upgrade the Gradle version

Visit the [Gradle website](https://gradle.org/releases/) and decide the:

 - desired version
 - desired distribution type
 - what is the sha256 for the version and type chosen above

Adjust the following command with tha arguments above and execute it twice:

    ./gradlew wrapper --gradle-version 6.6.1 \
        --distribution-type all \
        --gradle-distribution-sha256-sum 11657af6356b7587bfb37287b5992e94a9686d5c8a0a1b60b87b9928a2decde5

The first execution should automatically update:

- `bisq/gradle/wrapper/gradle-wrapper.properties`

The second execution should then update:

- `bisq/gradle/wrapper/gradle-wrapper.jar`
- `bisq/gradlew`
- `bisq/gradlew.bat`

The four updated files are ready to be committed.

To update the verification-metadata file without PGP signature metadata run:

- `./gradlew resolveAndVerifyDependencies --write-verification-metadata sha256`

To refresh dependency PGP signature verification metadata run:

- `./gradlew refreshDependencyVerificationKeyring`
- `./gradlew resolveAndVerifyDependencies --write-verification-metadata pgp,sha256`

Refresh the keyring first so newly available signing keys are present before
Gradle rewrites verification metadata. This avoids recording checksum-only
fallbacks for artifacts whose signatures can now be verified.

This refreshes Gradle's dependency verification keys from the configured key
servers, exports the armored keyring, resolves every resolvable Gradle
configuration, enables `verify-signatures` in `gradle/verification-metadata.xml`,
configures the armored keyring format, records trusted PGP keys for signed
artifacts, and keeps SHA-256 checksums with `reason="Artifact is not signed"` for
artifacts whose publishers do not provide detached signatures. The exported
armored keyring (`gradle/verification-keyring.keys`) provides the public keys
Gradle uses for signature verification and the signer identity data used by the
report. The binary keyring format (`gradle/verification-keyring.gpg`) is ignored.

The `refreshDependencyVerificationKeyring` task wraps
`./gradlew help --refresh-keys --export-keys`, so it refreshes Gradle's dependency
verification keys from the configured key servers and exports the armored keyring.
Some Gradle/Bouncy Castle combinations may write the keyring file and then fail
while finishing key export; the task accepts that case only when the armored
keyring was updated. The report task only needs
`gradle/verification-keyring.keys` for signer names, emails, and key creation dates.

To verify the current metadata without rewriting it run:

- `./gradlew resolveAndVerifyDependencies`

To regenerate the dependency signature report run:

- `./gradlew dependencySignatureReport`

Checksum-only artifacts must be listed in
`gradle/dependency-checksum-fallback-allowlist.tsv` with an artifact-level
review rationale:

```text
<group:name:version>\t<artifact-file-name>\t<review-rationale>
```

Keep the list exact. `verifyDependencySignaturePolicy` fails when a
checksum-only artifact is missing from the allowlist, when an allowlist entry is
stale, or when an entry has no rationale.
