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
