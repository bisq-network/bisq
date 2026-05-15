# Reproducible Build Attestation Plan

This document describes a recommended process for publishing reproducible-build
evidence, independent builder attestations, and release-manager verification for
Bisq releases.

The goal is to let a user or maintainer answer this question without trusting a
single machine or a single person:

> Did independent builders reproduce the same release artifacts from the same
> source, build instructions, and release environment?

The existing Bisq reproducible-build outputs remain the canonical comparison
artifacts:

- `release-evidence.zip`
- `release-manifest.tsv`
- `SHA256SUMS`
- `installer-evidence.zip`
- `installer-manifest.tsv`
- `INSTALLER-SHA256SUMS`

Attestations should describe and sign the comparison result. They should not
replace installer signatures or release signatures.

## Recommended Model

Use a threshold model:

1. CI builds the release commit and uploads evidence.
2. At least three independent builders rebuild the same release commit or tag.
3. Each builder compares local evidence against the CI or release-manager
   evidence and signs a machine-readable attestation.
4. The release manager performs the same rebuild and comparison.
5. The release manager verifies the independent attestations, signs the final
   release artifacts, and signs a release attestation index.
6. Public verifiers can check the release signatures, evidence signatures,
   independent builder signatures, and attestation index.

CI evidence is useful, but it should not count toward the independent builder
threshold. A release-manager attestation is useful, but it should not replace
the independent builder threshold.

Recommended minimum release policy:

- At least three independent successful builder attestations.
- At least one release-manager attestation.
- All attestations reference the exact release tag, commit hash, artifact
  names, artifact SHA-256 hashes, and evidence bundle SHA-256 hashes.
- Java payload mismatches block the release unless fully explained.
- Installer mismatches block the affected OS or architecture artifact unless
  fully explained.
- macOS Intel and Apple Silicon DMGs are treated as distinct artifacts.

## Roles

### CI Builder

CI provides the first public evidence for the release commit. It should run the
existing release evidence tasks and upload the generated evidence artifacts.

CI should publish:

- workflow run URL
- runner OS and architecture
- Java version
- Gradle version
- commit hash and tag if available
- `release-evidence.zip` SHA-256
- per-OS `installer-evidence.zip` SHA-256

GitHub artifact attestations can be added later for CI-produced evidence, but
they should be treated as CI provenance only. They do not replace independent
GPG-signed builder attestations.

### Independent Builder

An independent builder should use a clean checkout of the release tag or release
commit, verify the Gradle wrapper checksum, run the documented evidence tasks,
compare the local evidence to the chosen target evidence, and sign the result.

Expected commands today:

```bash
git submodule update --init --recursive
shasum -a 256 -c gradle/wrapper/gradle-wrapper.sha256

./gradlew clean verifyReleaseBuild
./gradlew verifyInstallerEvidenceBundle

./gradlew compareReleaseEvidenceBundles \
  -PleftReleaseEvidence=build/reports/release/release-evidence.zip \
  -PrightReleaseEvidence=/path/to/reference/release-evidence.zip

./gradlew compareInstallerEvidenceBundles \
  -PleftInstallerEvidence=build/reports/release/installer-evidence.zip \
  -PrightInstallerEvidence=/path/to/reference/installer-evidence.zip
```

The builder should then produce a `rebuild-attestation.json` file and a detached
GPG signature:

```bash
gpg --digest-algo SHA256 --armor --detach-sign rebuild-attestation.json
```

### Release Manager

The release manager should verify all submitted builder attestations before
signing and publishing release artifacts.

The release manager should:

1. Verify each builder signature.
2. Verify each builder key is an expected Bisq attestation key.
3. Verify each attestation references the release tag and commit.
4. Verify each attestation references the same artifact and evidence hashes.
5. Verify the independent builder threshold is met.
6. Rebuild and compare locally.
7. Create and sign `release-attestation-index.json`.
8. Publish the index, index signature, builder attestations, and builder
   signatures next to the release assets.

### Public Verifier

A verifier should be able to download the release assets and attestation bundle,
then verify:

- release artifact signatures
- release evidence signatures
- builder attestation signatures
- release attestation index signature
- threshold policy
- exact artifact and evidence hashes

## Attestation Format

Use a small JSON format that follows the in-toto Statement shape. This keeps
Bisq close to common supply-chain attestation tooling while still allowing GPG
detached signatures and the existing Bisq key workflow.

Suggested predicate type:

```text
https://bisq.network/reproducible-builds/rebuild-attestation/v1
```

Example:

```json
{
  "_type": "https://in-toto.io/Statement/v1",
  "subject": [
    {
      "name": "Bisq-64bit-1.10.0.deb",
      "digest": {
        "sha256": "..."
      }
    },
    {
      "name": "Bisq-x86_64-1.10.0.dmg",
      "digest": {
        "sha256": "..."
      }
    },
    {
      "name": "Bisq-aarch64-1.10.0.dmg",
      "digest": {
        "sha256": "..."
      }
    }
  ],
  "predicateType": "https://bisq.network/reproducible-builds/rebuild-attestation/v1",
  "predicate": {
    "releaseVersion": "1.10.0",
    "releaseTag": "v1.10.0",
    "commit": "...",
    "sourceRepository": "https://github.com/bisq-network/bisq",
    "builder": {
      "name": "Builder name",
      "gpgFingerprint": "...",
      "role": "independent-builder"
    },
    "environment": {
      "os": "macOS 15",
      "architecture": "x86_64",
      "javaVersion": "21.0.6",
      "gradleVersion": "..."
    },
    "commands": [
      "./gradlew clean verifyReleaseBuild",
      "./gradlew verifyInstallerEvidenceBundle",
      "./gradlew compareReleaseEvidenceBundles ...",
      "./gradlew compareInstallerEvidenceBundles ..."
    ],
    "evidence": {
      "localReleaseEvidenceSha256": "...",
      "referenceReleaseEvidenceSha256": "...",
      "localInstallerEvidenceSha256": "...",
      "referenceInstallerEvidenceSha256": "..."
    },
    "result": {
      "releaseEvidenceMatched": true,
      "installerEvidenceMatched": true,
      "mismatches": []
    },
    "createdAt": "2026-05-15T00:00:00Z"
  }
}
```

The exact JSON bytes should be signed with a detached GPG signature. A future
tool should generate deterministic JSON with sorted keys so generated
attestations are easy to review.

## Release Attestation Index

The release attestation index is the release-manager signed summary of the
accepted attestations.

It should contain:

- release version
- release tag
- commit hash
- release artifact list with SHA-256 and size
- evidence bundle list with SHA-256 and size
- accepted builder attestations with file names, SHA-256 hashes, and signer
  fingerprints
- rejected or ignored attestation file names, if any, with reason
- threshold policy used for this release
- release-manager fingerprint
- creation timestamp

The index should be signed:

```bash
gpg --digest-algo SHA256 --armor --detach-sign release-attestation-index.json
```

Public verifiers should treat the index as a summary, not as a substitute for
verifying the builder signatures and artifact hashes.

## Where To Publish

Recommended short-term approach:

1. Attach a single attestation bundle to the GitHub release.
2. Mirror the same bundle on the Bisq downloads page.
3. Include detached GPG signatures for the bundle and for the index inside it.

Suggested GitHub release asset:

```text
Bisq-${VERSION}-reproducible-builds.zip
Bisq-${VERSION}-reproducible-builds.zip.asc
```

Suggested bundle layout:

```text
release-attestation-index.json
release-attestation-index.json.asc
attestations/
  <builder-fingerprint>/
    rebuild-attestation-linux-x86_64.json
    rebuild-attestation-linux-x86_64.json.asc
    rebuild-attestation-macos-aarch64.json
    rebuild-attestation-macos-aarch64.json.asc
evidence/
  release-evidence.zip
  release-evidence.zip.asc
  installer-evidence-linux.zip
  installer-evidence-linux.zip.asc
  installer-evidence-macos-x86_64.zip
  installer-evidence-macos-x86_64.zip.asc
  installer-evidence-macos-aarch64.zip
  installer-evidence-macos-aarch64.zip.asc
  installer-evidence-windows.zip
  installer-evidence-windows.zip.asc
```

Recommended medium-term approach:

- Create a dedicated attestation repository, for example
  `bisq-network/bisq-rebuild-attestations`.
- Builders submit signed attestation files by pull request.
- The release manager verifies and merges them.
- The release manager packages the accepted files from that repository into
  the GitHub release attestation bundle.

A dedicated repository is preferable to the main source repository because
release evidence is generated after the release source commit and should not
mutate the source tree for that release. It also avoids adding generated release
data to the main repository history.

## Key Management

Maintain a small Bisq attestation keyring separate from the release signing key.

The keyring should define:

- accepted independent builder fingerprints
- release-manager fingerprints
- key expiry dates
- key revocation process
- whether a key can sign independent builder attestations, release-manager
  attestations, or both

The app and verification tooling should not depend on live keyserver lookups.
Keyservers are useful for manual audits, but automated verification should use
the project-published keyring and exact fingerprints.

## In-App Display

The in-app download flow should continue to treat detached installer signature
verification as the hard security gate.

Attestation display can be added as an additional transparency signal:

- download the compact release attestation index from the same release source
  used for installers or from a pinned Bisq URL
- verify the index signature against embedded Bisq release or attestation keys
- verify builder signatures against an embedded Bisq attestation keyring
- check that the attested artifact name and SHA-256 match the selected
  installer, including the selected macOS architecture
- show a concise status such as `Reproduced by 3 independent builders`
- provide a details view with builder fingerprints, platforms, evidence hashes,
  and links to the published evidence

The app should not fail an update solely because attestation metadata is
temporarily unavailable during the first rollout. Once the attestation process
is mature and consistently published, the project can decide whether missing or
invalid attestations should become a warning or a hard blocker.

Privacy requirement:

- Do not query third-party keyservers from the app.
- Do not report user downloads or verification results.
- Keep attestation downloads limited to the same release metadata or download
  origins already used by the updater.

## Gradle Tooling To Add

The current Gradle tasks already generate and compare release evidence. The
missing tooling is the attestation layer around those outputs.

Useful future tasks:

- `generateRebuildAttestation`
  - reads local evidence manifests
  - reads comparison target metadata
  - writes deterministic `rebuild-attestation.json`
- `verifyRebuildAttestation`
  - validates JSON shape
  - checks artifact and evidence hashes
  - verifies detached GPG signature
  - checks signer fingerprint against the attestation keyring
- `collectReleaseAttestations`
  - verifies all submitted attestations
  - checks threshold policy
  - writes `release-attestation-index.json`
- `verifyReleaseAttestationIndex`
  - verifies the signed index and every referenced attestation
- `packageReleaseAttestations`
  - builds `Bisq-${VERSION}-reproducible-builds.zip`
- `verifyGithubReleaseReadiness`
  - checks that the attestation bundle and signatures are present on the
    GitHub release after the process is adopted

## Rollout Plan

### Phase 1: Manual Attestations

- Define the JSON schema.
- Define the accepted attestation keyring.
- Have three builders produce and sign attestations manually.
- Publish the bundle next to release artifacts.
- Keep verification instructions in the docs.

### Phase 2: Gradle Generation And Verification

- Add `generateRebuildAttestation`.
- Add `verifyRebuildAttestation`.
- Add `collectReleaseAttestations`.
- Add release-readiness checks for the attestation bundle.

### Phase 3: Dedicated Attestation Repository

- Create a small repository for signed attestation submissions.
- Require pull requests for builder attestations.
- Require release-manager verification before bundling attestations into the
  release assets.

### Phase 4: In-App Transparency

- Add an attestation summary to the download window.
- Verify the selected installer against the signed attestation index.
- Keep installer signature verification as the hard gate.

### Phase 5: Harder Release Policy

- Decide whether a missing valid attestation index blocks final release.
- Decide whether the app should show warnings or block updates if expected
  attestations are missing.

## Open Decisions

- Minimum number of independent builders. Recommended default: three.
- Whether a release manager can also count as one independent builder.
  Recommended default: no.
- Whether all OS and architecture artifacts need the same threshold at launch.
  Recommended default: start with Java payload and supported installer evidence,
  then tighten per-OS and per-architecture thresholds as deterministic builders
  mature.
- Exact attestation repository name.
- Exact keyring file location and governance.
- Whether to add GitHub artifact attestations for CI evidence in addition to
  GPG signatures.

## References

- [Reproducible Builds definition](https://reproducible-builds.org/docs/definition/)
- [Reproducible Builds build environment](https://reproducible-builds.org/docs/perimeter/)
- [SLSA provenance](https://slsa.dev/provenance)
- [in-toto Statement specification](https://github.com/in-toto/attestation/blob/main/spec/v1/statement.md)
- [GitHub artifact attestations](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations)
- [Bitcoin Core Guix attestation repository](https://github.com/bitcoin-core/guix.sigs)
