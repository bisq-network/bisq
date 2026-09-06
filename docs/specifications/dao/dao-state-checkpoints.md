# DAO State Checkpoint

DAO state checkpoints are bundled consensus hashes that Bisq nodes use to detect
whether their local DAO state has diverged from the expected chain history.

## Overview

The DAO state hash chain is built while Bisq parses the Bitcoin blockchain. For every
processed block, `DaoStateMonitoringService` creates a hash of the serialized DAO state
that includes the previous block's hash. This creates a chain of hashes where one
mismatch means all subsequent hashes are invalid.

A checkpoint fixes the expected DAO state hash at a particular block height. When Bisq
processes that height, it compares the locally computed hash against the checkpoint. If
they differ, the local DAO data is considered corrupted or out of sync and Bisq forces a
resync from the bundled DAO state resource.

## Checkpoint data

Checkpoints are stored in the resource file
`../../../core/src/main/resources/dao/daoStateHash.checkpoints`. Each line contains one entry
with the block height and the hex-encoded DAO state hash separated by a comma:

```
586920,523aaad4e760f6ac6196fec1b3ec9a2f42e5b272
```

Blank lines and lines starting with `#` are ignored.

`DaoStateMonitoringService` loads the file at startup via the classpath
(`getResourceAsStream`), which works when running from source, from a jar and from a
binary.

Entries are validated at load time: the height must be a positive integer and the hash must
be exactly 40 lower-case hex chars, matching the format written by
`--dumpDaoStateHashCheckpoints`. This is deliberately strict — a typo'd or upper-case hash
would make every node fail the checkpoint and wipe its local DAO data, so a malformed
resource file fails fast at startup instead.

## When checkpoints are checked

Checkpoints are verified at two points, because neither alone covers all cases.

**After each parsed block.** `DaoStateMonitoringService.createHashFromBlock(Block block)` is
invoked from `DaoStateSnapshotService.onDaoStateChanged(Block block)` after a block has been
parsed and the DAO state has been updated. Once the DAO state hash for the block has been
created and added to the hash chain, the service calls
`maybeVerifyCheckpoint(block.getHeight())`. This reports a failed checkpoint during a full
sync as soon as the affected height is passed, rather than only at the very end.

The verification cannot happen in `DaoStateMonitoringService.onDaoStateChanged(Block block)`
itself, because that callback is invoked before the hash for the block exists:
`DaoStateMonitoringService` is registered as DAO state listener before
`DaoStateSnapshotService` (see `DaoSetup`), and only `DaoStateSnapshotService` triggers the
hash creation.

**At `onParseBlockChainComplete`.** `verifyCheckpoints()` checks every entry in the
checkpoint map against the whole hash chain. This is required because the per-block check
alone would almost never fire: the hash chain is restored from the persisted snapshot at
startup, and checkpoint heights are typically far below the snapshot height, so those
blocks are never parsed again. In addition, unless the full mode DAO monitor is enabled,
`DaoStateSnapshotService` does not call `createHashFromBlock` at all during initial parsing.

This means:

- Only block heights with an entry in the checkpoint map are verified.
- Both the freshly parsed blocks and the hash chain restored from the snapshot are covered.
- The check is skipped when the `ignoreDevMsg` option is enabled.

## Verification behavior

`maybeVerifyCheckpoint` looks up the local `DaoStateHash` for the requested block height and
compares its hex-encoded hash with the checkpoint value. Only self-created hashes are
compared: a hash taken over from a seed node or from resources reflects the peers' view and
says nothing about the validity of the local DAO state.

- **Matching hash:** A log info entry is written stating that the checkpoint passed.
- **No self-created local hash found:** A log info entry is written but no resync is
  triggered. This happens if the hash chain does not reach the checkpoint height, or if the
  hash at that height was taken over from peers.
- **Mismatching hash:**
    - `DaoStateStorageService.removeAndBackupAllDaoData()` is called once to back up and
      remove the local DAO data, forcing a resync from resources on the next startup.
    - All registered `DaoStateMonitoringService.Listener` instances receive
      `onCheckpointFailed()`.
    - Subsequent mismatches are ignored once `checkpointFailed` has been set.

## Financial safety after failure

A mismatching self-created checkpoint must immediately revoke shared DAO financial readiness,
before cleanup or listener callbacks run. The failure is sticky for the lifetime of the process:
later matching hashes, snapshot application, synchronization completion, or successful recovery of
another DAO error must not clear it. Cleanup failure or a missing/throwing listener must not restore
authorization. Failure flags are local and must not enter serialized consensus state or its hashes.

The BSQ wallet must reject new signing, commitment, and broadcast operations while checkpoint-failed.
Both BSQ and non-BSQ coin selection in that wallet must refuse outputs, including own unconfirmed
change: unreliable coloring must not make genuine BSQ spendable as ordinary BTC. Publication of a
BSQ transaction involving both wallets must check before committing to either wallet. Existing swap
seller readiness checks must reject input admission and BTC-input signing after failure.

These rules guard BSQ-coloring-dependent financial use, not every BTC operation influenced by DAO
data. Ordinary BTC withdrawals and DAO-independent escrow settlement are not globally disabled.
They do not change consensus UTXO lookup or spendability semantics, checkpoint contents, validation
of missing/non-self-created hashes, or the explicit `ignoreDevMsg` policy. No activation cutoff is
required for revoking authorization after an already detected mismatch.

Transactions already submitted to a broadcaster cannot be recalled. Their outcome callbacks,
network observation, and wallet/trade-history accounting must continue; a later checkpoint failure
must not be reported as cancellation of an already submitted transaction.

Restart alone is not proof of trustworthy recovery. Existing backup/removal remains best effort:
independently queued writes and the shutdown persistence flush can recreate deleted DAO files.
Race-free all-store cleanup, snapshot provenance, and verification of historical coloring require
separate recovery work. A checkpoint failure must remain financially blocked regardless of whether
that cleanup succeeds.

## Generating new checkpoint entries

The `--dumpDaoStateHashCheckpoints=true` program argument writes ready-to-paste checkpoint
lines to a text file whenever a hash for a block height divisible by 1000 is created:

- Output file: `dao_state_hash_checkpoints.txt` in the application data directory.
- Each line has the form `<height>,<daoStateHash>` and can be copied verbatim into the
  `dao/daoStateHash.checkpoints` resource file.
- Entries are appended, so the file grows across restarts until manually cleared.

The option is off by default. It is intended as a maintenance aid for choosing new
checkpoint heights; running with it enabled has no effect on verification behaviour.

## Related code

- `../../../core/src/main/java/bisq/core/dao/monitoring/DaoStateMonitoringService.java`
- `../../../core/src/main/java/bisq/core/dao/monitoring/model/DaoStateHash.java`
- `../../../core/src/main/java/bisq/core/dao/monitoring/model/DaoStateBlock.java`
- `../../../core/src/main/resources/dao/daoStateHash.checkpoints`
