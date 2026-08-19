# Release checklist

This checklist lists every task that a Bisq 1 release requires. It covers the version number, the
bundled data files, the consensus activation heights, the build, the signatures and the publication.

[release-process.md](release-process.md) describes the manual build and upload work in more detail.
This file is the complete task list and points to the document that explains how to create each file.

Work through the steps in order, with one exception: the data-store step 2.1 uses the version number
that step 3 sets, so set the version in `copy_dbs.sh` before you run that script. Step 1 and step 2
need a fully synced mainnet node, so start them early.

---

## Step 1: Decide the consensus changes

These items change how nodes calculate the DAO state or how they validate trade messages. A node that
runs an older release will calculate a different result. Decide them before anything else, because
the activation heights depend on the planned release date.

### 1.1 Hard fork activation heights

File: `core/src/main/java/bisq/core/dao/DaoHardFork.java`

| Line | Constant | Current value |
|------|----------|---------------|
| 23 | `ACTIVATE_HARD_FORK_3_HEIGHT_MAINNET` | `963_000` |
| 24 | `ACTIVATE_HARD_FORK_3_HEIGHT_TESTNET` | `3_000_000` |

These are finalized consensus constants for the coordinated hard-fork-3 rollout. Verify them during
release preparation, but do not change them as part of an ordinary release.

Three consensus rules activate together with hard fork 3:

- `core/src/main/java/bisq/core/dao/node/parser/TxParser.java` — spending a lockup output outside an
  unlock transaction becomes invalid.
- `core/src/main/java/bisq/core/dao/governance/merit/MeritConsensus.java` — an issuance may back merit
  at most once per cycle.
- `core/src/main/java/bisq/core/dao/governance/proposal/role/RoleValidator.java` — the required bond
  unit and the unlock time in a role proposal must match the bonded role type.

The release must ship far enough before the activation height that users have time to upgrade. If the
schedule no longer permits that, stop and obtain an explicit new consensus decision rather than
silently moving the height. The rule is described in
[specifications/dao/bond-lockup-spend.md](specifications/dao/bond-lockup-spend.md) and in
[specifications/dao/merit.md](specifications/dao/merit.md), section 7.

Before the release, repeat the duplicate merit audit described in
[specifications/dao/merit.md](specifications/dao/merit.md), section 7.2, against a node that is synced
past the last completed voting cycle. Repeat it after every result phase between the release and the
activation height.

Historical hard fork heights must not be changed. Only check that they are unchanged:

| File | Constant | Value |
|------|----------|-------|
| `core/src/main/java/bisq/core/dao/node/parser/TxOutputParser.java` | `ACTIVATE_HARD_FORK_1_HEIGHT_MAINNET` | `605000` |
| `core/src/main/java/bisq/core/dao/node/full/RpcService.java` | `ACTIVATE_HARD_FORK_2_HEIGHT_MAINNET` | `680300` |
| `core/src/main/java/bisq/core/dao/state/model/governance/DaoArithmetics.java` | `DAO_CONSENSUS_V2_ACTIVATION_HEIGHT` | `954_200` |
| `core/src/main/java/bisq/core/dao/governance/merit/MeritConsensus.java` | `MERIT_CONSENSUS_V2_ACTIVATION_HEIGHT` | `954_200` |
| `core/src/main/java/bisq/core/dao/governance/bond/role/BondedRolesRepository.java` | `LEGACY_REGISTRATION_CUTOFF_HEIGHT_MAINNET` | `963_000` |

### 1.2 Activation dates

Some rules activate on a calendar date instead of a block height. Check that the release ships before
the date, and remove the compatibility code once a date has passed.

| File | Line | Constant | Date |
|------|------|----------|------|
| `core/src/main/java/bisq/core/support/dispute/messages/DisputeMessage.java` | 41 | `SENDER_SIGNATURE_PUB_KEY_VALIDATION_ACTIVATION_DATE` | 2026-09-01 |
| `core/src/main/java/bisq/core/trade/model/bisq_v1/Contract.java` | 69 | `DISPUTE_AGENT_PUB_KEYS_ACTIVATION_DATE` | 2026-08-01 |

`core/src/main/java/bisq/core/support/dispute/DisputeValidation.java` carries a `TODO` to remove the
legacy contract fallback after the dispute agent public key date has passed.

### 1.3 Values in the trade protocol that must stay identical between peers

Do not change these unless the change is planned as a protocol change. Both trade peers calculate the
delayed payout transaction from them, so a difference breaks the trade.

`core/src/main/java/bisq/core/dao/burningman/DelayedPayoutTxReceiverService.java`:
`MIN_SNAPSHOT_HEIGHT` (`767950`), `SNAPSHOT_SELECTION_GRID_SIZE`, `DPT_MIN_OUTPUT_AMOUNT`,
`DPT_MIN_REMAINDER_TO_LEGACY_BM`, `DPT_MIN_TX_FEE_RATE`, `BM_ADDRESS_LIST_SHARE_RANGE_TOLERANCE`.

`core/src/main/java/bisq/core/dao/burningman/BurnTargetService.java`: `ACTIVATION_BLOCK` (`769845`).

`core/src/main/java/bisq/core/dao/burningman/accounting/BurningManAccountingService.java`:
`EARLIEST_BLOCK_HEIGHT` (`656035`).

### 1.4 Protocol and database version numbers

File: `common/src/main/java/bisq/common/app/Version.java`

| Line | Constant | Current | Increase only when |
|------|----------|---------|--------------------|
| 96 | `P2P_NETWORK_VERSION` | `1` | An object sent over the network changes in a way that older nodes cannot read |
| 100 | `LOCAL_DB_VERSION` | `1` | The format of the data written to disk changes in a way that older releases cannot read |
| 111 | `TRADE_PROTOCOL_VERSION` | `4` | The trade protocol changes. Existing offers become invalid or must be migrated |

A normal release does not change these three values.

---

## Step 2: Update the bundled data files

For most of these you need a Bisq desktop client that runs as a DAO full node against a local Bitcoin
Core node, fully synced on mainnet.

### 2.1 Data stores from the peer-to-peer network

Document: [data-stores.md](data-stores.md)

**Set the new version number first.** `copy_dbs.sh` holds the version in its own `version=` variable
(line 5) and uses it to name the two new files. If you run the script while that variable still holds
the previous release version, it writes files with the previous version in the name and overwrites
resource files that were already released. Those files must never change, see the note further below.
So do step 3 of this checklist, or at least the `copy_dbs.sh` part of it, before you run the script.

1. Start the client and let it sync completely. A restart may be needed more than once, because one
   request returns at most 3000 trade statistic objects.
2. Compare the hashes under `DAO > Network Monitor` and the total number of trade statistics with at
   least one other client that runs as a light node.
3. Check that `version=` in `desktop/package/macosx/copy_dbs.sh` is the new release version.
4. Set `BISQ_DIR` to your Bisq data directory and run
   `desktop/package/macosx/copy_dbs.sh`.

The script writes into `p2p/src/main/resources/`:

| Resource file | Note |
|---------------|------|
| `DaoStateStore_BTC_MAINNET` | Overwritten |
| `BsqBlocks_BTC_MAINNET/` | Directory, new block files are added |
| `SignedWitnessStore_BTC_MAINNET` | Overwritten |
| `BurningManAccountingStore_v3_BTC_MAINNET` | Overwritten |
| `TradeStatistics3Store_<version>_BTC_MAINNET` | New file with the release version in the name |
| `AccountAgeWitnessStore_<version>_BTC_MAINNET` | New file with the release version in the name |

The following three are only copied when it is needed. The lines are commented out in the script:
`ProposalStore_BTC_MAINNET`, `TempProposalStore_BTC_MAINNET`, `BlindVoteStore_BTC_MAINNET`.

**If you commit the two new files that carry the version in the name, you must also add the version
string to `HISTORICAL_RESOURCE_FILE_VERSION_TAGS` in
`common/src/main/java/bisq/common/app/Version.java` (line 46).** The application reads only the
versions listed there. A file that is not listed is ignored, and a listed version without a file is
accepted without an error.

Add the new version to that list only after the seed nodes run the new release. If a user client has
a historical resource file that the seed node does not have, it causes many unnecessary requests.
This is why commit `c2a4c8d2de` removed such files again.

Old files that carry a version in the name must never be renamed or deleted. Older peers still
request them.

After you refresh `DaoStateStore_BTC_MAINNET`, run
`core/src/test/java/bisq/core/dao/governance/merit/BundledMeritDuplicationAuditTest.java`. It reads
that resource directly, so its result can change.

Also run the resource integration audit explicitly:

```bash
./gradlew :core:test --tests bisq.core.dao.BundledDaoStateAuditTest -PrunResourceAudits=true
```

It checks that the separate BSQ block resources are contiguous through the DAO-state height, that
their embedded transaction and output coordinates are internally consistent, that no historical
lockup was spent by anything other than a canonical unlock shape matching its parsed transaction
type, that evaluated role terms still match their role types, and that the refreshed P2P stores and
versioned Burning Man address lists are readable and internally consistent. It is kept out of the
regular unit-test suite because it scans the full bundled history.

Create a pull request against the release branch that contains screenshots of the hashes of a full
node and a light node, so that a reviewer can compare them.

### 2.2 Burning Man address list

Document: [burning-man-address-list.md](burning-man-address-list.md)

Directory: `p2p/src/main/resources/burningman/`, files `bm-addresses-v0001.json`,
`bm-addresses-v0002.json`, `bm-addresses-v0003.json`.

A new file is required for every release. The value `cappedBurnAmountShare` changes with every block,
so the file changes even when no new address was added.

1. Sync the DAO data on mainnet.
2. Start the client with the program argument `--dumpBurningManData=true`.
3. The file is written into the application data directory, not into the database directory. The
   exporter chooses the next version number automatically, which is the highest bundled version plus
   one.
4. Compare the new file with the previous one. A large change in the set of addresses is a reason to
   review it manually.
5. Copy the file into `p2p/src/main/resources/burningman/`.

There is no constant to change. The service reads the directory and takes the highest version number
it finds. The value `listVersion` inside the file must match the number in the file name, and the
entries must be sorted by `receiverAddress`. Both rules are checked when the file is loaded.

Never change a file that was already released.

### 2.3 Bitcoinj checkpoints

Document: [bitcoinj-checkpoint.md](bitcoinj-checkpoint.md)

File: `core/src/main/resources/wallet/checkpoints.txt` (mainnet), and
`core/src/main/resources/wallet/checkpoints.testnet.txt` (testnet).

The tool is in a different repository:

1. Clone `https://github.com/bisq-network/bitcoinj`.
2. Run `cd tools ; ./build-checkpoints --peer=127.0.0.1 --days=10`. A local Bitcoin node must run.
3. Copy the generated `tools/checkpoints.txt` into `core/src/main/resources/wallet`.

These checkpoints let a new user start from a recent block header instead of downloading the whole
chain, so an old file makes the first start much slower.

### 2.4 DAO state hash checkpoints

Document: [specifications/dao/dao-state-checkpoints.md](specifications/dao/dao-state-checkpoints.md)

File: `core/src/main/resources/dao/daoStateHash.checkpoints`

Each line has the form `<blockHeight>,<daoStateHash>`. The hash must be exactly 40 lower case
hexadecimal characters. A wrong entry makes every node delete its local DAO data, so the file is
validated strictly when it is loaded and a wrong entry stops the start.

1. Run a full node with the program argument `--dumpDaoStateHashCheckpoints=true`.
2. It appends a line for every block height that can be divided by 1000 to
   `dao_state_hash_checkpoints.txt` in the application data directory.
3. Copy the new lines into the resource file.

Only hashes that the node calculated itself are compared. A hash that was taken over from a seed node
or from the bundled resources says nothing about the local DAO state. For that reason a checkpoint
above the height of the bundled `DaoStateStore_BTC_MAINNET` cannot be verified by a fresh
installation. Extend the checkpoints after you refresh the DAO state resource, and keep that in mind
when you choose the heights.

### 2.5 Translations

Document: [translation-process.md](translation-process.md)

Run `core/update_translations.sh`. It uses the Transifex command line client and updates all files in
`core/src/main/resources/i18n`. Check the result for obvious problems before you commit it. Only the
English file `displayStrings.properties` is edited directly in the project.

### 2.6 Other bundled lists

Update these only when a change is needed.

| File | Content |
|------|---------|
| `core/src/main/resources/denylist/btc_mainnet.denylist` | Node addresses that are blocked at start. Also holds `requiredVersionForTrading` |
| `core/src/main/resources/btc_mainnet.trusted_bsq_block_providers` | Trusted BSQ block providers with their public keys |
| `core/src/main/resources/btc_mainnet.seednodes` | Seed nodes |
| `p2p/src/main/resources/bitcoin_core_nodes_main.txt` | Bitcoin Core nodes used for peer discovery |

`core/src/main/java/bisq/core/dao/node/full/RpcService.java` holds
`SUPPORTED_NODE_VERSION_RANGE`. Change it if a newer Bitcoin Core version should be supported.

---

## Step 3: Update the version number

Ten values in eight files hold the version. Set `oldVersion` and `newVersion` in
`desktop/package/macosx/replace_version_number.sh` by hand first, then run that script. It updates
most of the other files.

| # | File | Line | Value |
|---|------|------|-------|
| 1 | `common/src/main/java/bisq/common/app/Version.java` | 39 | `VERSION` |
| 2 | `build-logic/packaging/src/main/kotlin/bisq/gradle/packaging/PackagingPlugin.kt` | 22 | `APP_VERSION` |
| 3 | `desktop/package/macosx/Info.plist` | 8 | `CFBundleVersion` |
| 4 | `desktop/package/macosx/Info.plist` | 11 | `CFBundleShortVersionString` |
| 5 | `desktop/package/macosx/finalize.sh` | 6 | `version=` |
| 6 | `desktop/package/macosx/copy_dbs.sh` | 5 | `version=` |
| 7 | `desktop/package/macosx/insert_snapshot_version.sh` | 5 | `version=` |
| 8 | `desktop/package/macosx/replace_version_number.sh` | 5 | `oldVersion=` |
| 9 | `desktop/package/macosx/replace_version_number.sh` | 6 | `newVersion=` |
| 10 | `desktop/src/main/resources/version.txt` | 1 | The version |

The version must have exactly three parts that are separated by a dot. `Version.getSubVersion`
rejects any other form.

The task `verifyGithubReleaseReadiness` checks only entries 1, 2 and 5. Check the other seven by
hand, for example with `grep -rn "<old version>"`.

The version number on the website is changed in a separate repository, in `_config.yml` of
`bisq-network/bisq-website`.

---

## Step 4: Release notes

Create the directory `release-notes/<version>/` with three files, as in the earlier releases:

- `full-release-notes.md`
- `notable-changes.md`
- `release-highlights.md`

Also write a short version of the notes. It is used in the update message that is shown inside the
application.

Helpful commands, described in [release-process.md](release-process.md):

```
git shortlog --no-merges v<previous version>..v<new version>
git log --format='- %aN' v<previous version>..v<new version> | sort -fiu
```

You can create a local tag to write the notes before the final tag exists, and delete it again
afterwards.

---

## Step 5: Build

Document: [reproducible-builds/](reproducible-builds/) and
[reproducible-builds/usage.md](reproducible-builds/usage.md)

The build environment is pinned in `gradle.properties`:

```
releaseBuild.javaVersion=21.0.6
releaseBuild.javaVendor=Azul Systems, Inc.
releaseBuild.gradleVersion=8.9
```

`verifyReleaseEnvironment` compares these values with the Java virtual machine that runs Gradle, not
with the Java toolchain. Run Gradle itself with Azul Zulu 21.0.6.

1. Verify the build and produce the evidence files:

   ```
   ./gradlew verifyReleaseBuild
   ```

   This also runs `verifyGradleWrapperSecurity`, `verifyReleaseBuilderImage`,
   `verifyGithubActionsSecurity`, `verifyDependencySignaturePolicy` and
   `verifyReproducibleArchives`.

2. Produce the installers and their evidence files:

   ```
   ./gradlew verifyInstallerEvidenceBundle
   ```

   The installer tasks are `:desktop:generateInstallers` (disk image on macOS, executable installer
   on Windows), `:desktop:deb` and `:desktop:rpm`.
   On Windows the WiX toolset must be installed and must be on the search path.

3. Optional, for a second independent build in a container:

   ```
   docker build --pull=false -t bisq-release-builder-linux:java-21.0.6 docker/release-builder/linux
   docker run --rm --platform linux/amd64 --user "$(id -u):$(id -g)" \
     -v "$PWD:/workspace" -w /workspace \
     bisq-release-builder-linux:java-21.0.6 \
     ./gradlew clean verifyReleaseBuild verifyInstallerEvidenceBundle
   ```

   The GitHub workflow `release-builder.yml` builds the same commit twice and compares the two
   results with `compareReleaseEvidenceBundles` and `compareInstallerEvidenceBundles`.

The results are written to `build/reports/release/`, among them `SHA256SUMS`, `release-manifest.tsv`,
`build-info.json`, `release-evidence.zip`, `INSTALLER-SHA256SUMS`, `installer-manifest.tsv` and
`installer-evidence.zip`.

Install the produced application on macOS and check that it works before you build the other
platforms. Install and start the package on Linux and on Windows as well. Run a virus scan on the
Windows machine.

If dependencies were added, changed or removed, or if the Gradle version changed, update the
dependency verification data. The commands are documented in
[../gradle/README.md](../gradle/README.md):

```
./gradlew refreshDependencyVerificationKeyring
./gradlew resolveAndVerifyDependencies --write-verification-metadata pgp,sha256
./gradlew dependencySignatureReport
```

An artifact that has no signature must be listed in
`gradle/dependency-checksum-fallback-allowlist.tsv`. The list must be exact: an entry that is no
longer needed also makes the build fail.

---

## Step 6: Sign

Set `BISQ_GPG_USER` and `BISQ_SHARED_FOLDER` in your environment.

Run `desktop/package/macosx/finalize.sh`. Run it for every platform, even though it is stored in the
macOS directory, and change into `desktop/package/macosx` before you start it.

It collects the installers, renames them to the public names, creates `Bisq-<version>.jar.txt` from
the four SHA-256 files of the platform jar files, and creates a detached signature for every file.

You can do the same with Gradle tasks:

```
./gradlew createReleaseJarTxt \
  -PreleaseVersion=${NEW_VERSION} \
  -PreleaseDir=/path/to/final-release-dir \
  -PmacosX86_64JarSha256=/path/to/desktop-${NEW_VERSION}-all-mac-x86_64.jar.SHA-256 \
  -PmacosAarch64JarSha256=/path/to/desktop-${NEW_VERSION}-all-mac-aarch64.jar.SHA-256 \
  -PlinuxJarSha256=/path/to/desktop-${NEW_VERSION}-all-linux.jar.SHA-256 \
  -PwindowsJarSha256=/path/to/desktop-${NEW_VERSION}-all-win.jar.SHA-256

./gradlew signReleaseArtifacts \
  -PreleaseVersion=${NEW_VERSION} \
  -PreleaseDir=/path/to/final-release-dir \
  -PgpgUser=${BISQ_GPG_USER}
```

Sign the evidence files as well, as described in
[reproducible-builds/usage.md](reproducible-builds/usage.md):

```
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/release-evidence.zip
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/release-manifest.tsv
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/SHA256SUMS
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/installer-evidence.zip
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/installer-manifest.tsv
gpg --digest-algo SHA256 --armor --detach-sign build/reports/release/INSTALLER-SHA256SUMS
```

Signing the application for macOS and for Windows is not part of the build. There is no script and no
workflow for it in this repository.

`desktop/package/signingkey.asc` holds the identifier of the key that was used. The public keys are
stored twice and must be identical in both places: `desktop/package/<id>.asc` and
`desktop/src/main/resources/keys/<id>.asc`.

---

## Step 7: Publish

1. Make at least one real trade on mainnet with a currency that is rarely used, so that no other
   trader is disturbed.

2. Commit the version change and create a signed tag:

   ```
   git tag -s v<new version> -m"Release v<new version>"
   git push --tags origin master
   ```

3. Upload all files to the GitHub release page, including `signingkey.asc` and the public keys.

4. Run the readiness check. Set `GITHUB_TOKEN` in the environment if the release is not public yet:

   ```
   ./gradlew verifyGithubReleaseReadiness -PreleaseVersion=${NEW_VERSION}
   ```

   It writes `build/reports/release/github-release-readiness.md` and stops on any failure. It checks
   the version in three files, the release tag, all 21 expected files on the GitHub release page,
   the download addresses on `bisq.network`, the public keys in all four places, the expiry date of
   the keys and their presence on the common key servers.

5. Check the uploaded files with `virustotal.com`.

6. Publish the release on GitHub and merge the pull request for the website.

The application searches for an update at the address
`https://bisq.network/downloads/v<version>/`. These files must exist there:

| Platform | File |
|----------|------|
| macOS Intel | `Bisq-x86_64-<version>.dmg` and `.asc` |
| macOS Apple Silicon | `Bisq-aarch64-<version>.dmg` and `.asc` |
| Windows | `Bisq-64bit-<version>.exe` and `.asc` |
| Debian | `Bisq-64bit-<version>.deb` and `.asc` |
| Red Hat | `Bisq-64bit-<version>.rpm` and `.asc` |
| all | `signingkey.asc` |

Document: [in-app-update-download.md](in-app-update-download.md)

---

## Step 8: After the release

1. Add the note "A newer version is already available! Please don't use this version anymore." to the
   older GitHub releases.

2. Send the update message. The application does not poll for a new version. It receives a signed
   alert over the peer-to-peer network. Start the alert sender application (`Cmd + M`), remove the
   old version, select the update option, enter the version number and add the short release notes.
   Leave the application running for about one minute so that the message spreads well, then make a
   backup of its data directory.

3. Sign the source code archive for Arch Linux. GitHub creates the archive, so this is only possible
   after the tag exists:

   ```
   gpg --digest-algo SHA256 --local-user $BISQ_GPG_USER --output bisq-${NEW_VERSION}.tar.gz.asc \
     --detach-sig --armor bisq-${NEW_VERSION}.tar.gz
   ```

4. Set the version back to a snapshot version. Set the version in
   `desktop/package/macosx/insert_snapshot_version.sh`, run it and commit the result.

5. Announce the release in the forum, in the Matrix space (General room), on Twitter and optionally
   on Reddit.

---

## Reference: files that a release can change

| File or directory | Required every release | How it is created |
|-------------------|------------------------|-------------------|
| `common/src/main/java/bisq/common/app/Version.java` | Yes | By hand or `replace_version_number.sh` |
| `build-logic/packaging/.../PackagingPlugin.kt` | Yes | `replace_version_number.sh` |
| `desktop/package/macosx/Info.plist` | Yes | `replace_version_number.sh` |
| `desktop/package/macosx/{finalize,copy_dbs,insert_snapshot_version,replace_version_number}.sh` | Yes | By hand or `replace_version_number.sh` |
| `desktop/src/main/resources/version.txt` | Yes | `replace_version_number.sh` |
| `p2p/src/main/resources/DaoStateStore_BTC_MAINNET` | Yes | `copy_dbs.sh` |
| `p2p/src/main/resources/BsqBlocks_BTC_MAINNET/` | Yes | `copy_dbs.sh` |
| `p2p/src/main/resources/SignedWitnessStore_BTC_MAINNET` | Yes | `copy_dbs.sh` |
| `p2p/src/main/resources/BurningManAccountingStore_v3_BTC_MAINNET` | Yes | `copy_dbs.sh` |
| `p2p/src/main/resources/TradeStatistics3Store_<version>_BTC_MAINNET` | When a new snapshot is added | `copy_dbs.sh`, plus the entry in `HISTORICAL_RESOURCE_FILE_VERSION_TAGS` |
| `p2p/src/main/resources/AccountAgeWitnessStore_<version>_BTC_MAINNET` | When a new snapshot is added | Same as above |
| `p2p/src/main/resources/{Proposal,TempProposal,BlindVote}Store_BTC_MAINNET` | Only when needed | `copy_dbs.sh`, lines are commented out |
| `p2p/src/main/resources/burningman/bm-addresses-vNNNN.json` | Yes | `--dumpBurningManData=true` |
| `core/src/main/resources/wallet/checkpoints.txt` | Yes | `build-checkpoints` in the bitcoinj repository |
| `core/src/main/resources/dao/daoStateHash.checkpoints` | Recommended | `--dumpDaoStateHashCheckpoints=true` |
| `core/src/main/resources/i18n/` | Yes | `core/update_translations.sh` |
| `core/src/main/resources/denylist/btc_mainnet.denylist` | Only when needed | By hand |
| `core/src/main/resources/btc_mainnet.trusted_bsq_block_providers` | Only when needed | By hand |
| `core/src/main/resources/btc_mainnet.seednodes` | Only when needed | By hand |
| `p2p/src/main/resources/bitcoin_core_nodes_main.txt` | Only when needed | By hand |
| `core/src/main/java/bisq/core/dao/DaoHardFork.java` | Only for a hard fork | Decision by the developers |
| `release-notes/<version>/` | Yes | By hand |
| `gradle/verification-metadata.xml`, `gradle/verification-keyring.keys`, `docs/dependency-signature-report.md` | Only when dependencies change | Gradle tasks, see `gradle/README.md` |

---

## Known differences between the documentation and the code

These points are wrong or missing in the current documentation and tooling. Check them before you
rely on a step.

1. [release-process.md](release-process.md) tells you to run `./gradlew packageInstallers` in four
   places. That task does not exist in this repository. The tasks are `:desktop:generateInstallers`,
   `:desktop:deb` and `:desktop:rpm`, or `verifyInstallerEvidenceBundle`, which starts them.

2. No Gradle task creates the four files `desktop-<version>-all-mac-x86_64.jar.SHA-256`,
   `desktop-<version>-all-mac-aarch64.jar.SHA-256`, `desktop-<version>-all-linux.jar.SHA-256` and
   `desktop-<version>-all-win.jar.SHA-256`, but `finalize.sh` and `createReleaseJarTxt` both need
   them. Create them by hand from the platform jar files.

3. `replace_version_number.sh` also lists file names that no longer exist (`create_app.sh`,
   `release.sh`, `release.bat`, `package.sh`, `package.bat`). It does not update its own two
   variables and it does not update `insert_snapshot_version.sh`.

4. [verify-download-files.md](verify-download-files.md) names `E222AA02` as the signing key, while
   `desktop/package/signingkey.asc` currently holds `387C8307`.

5. `updater/` contains an installer for a Windows `.msi` file, but the build produces an `.exe`
   file.

6. `verifyGithubActionsSecurity` compares fixed text from `release-builder.yml`. If you change that
   workflow or `releaseBuild.javaVersion`, you must change the workflow, the file
   `docker/release-builder/linux/Dockerfile` and the expected text in that task together.
