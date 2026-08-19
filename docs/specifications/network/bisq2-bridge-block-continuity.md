# Bisq 2 bridge block continuity

## Purpose

The Bisq 1 bridge supplies DAO block history and a live block stream to Bisq 2 oracle nodes. This
contract supports proof-of-burn publication, bonded-reputation lock/unlock state and bonded-role
revalidation. Historical and live delivery must form a contiguous view across startup and reconnects.

## Historical response

Each BSQ-block response includes `snapshotHeight`, the DAO chain height captured before the historical
scan. The bridge returns only blocks at or below that height. The block list remains sparse and may be
empty even when the snapshot height is positive, because historical blocks without exported
transactions are omitted.

The explicit height is required because the highest returned sparse block cannot prove how far the
snapshot extends. Consumers use it as the completed catch-up cursor and request subsequent recovery
from the next height.

## Live stream handoff

The continuity-aware subscription sends a ready event after the observer has been registered. The
ready event carries the current DAO height and is ordered with live block publication. The consumer
requests a confirming historical snapshot after receiving that event. Blocks committed after
registration are therefore delivered by the stream, while the snapshot covers the preceding
interval. The ready height is an acknowledgement, not a completed history cursor.

The consumer may receive the same exported transaction from the snapshot and stream, so it
deduplicates this overlap by transaction id. If a live height exposes a gap above the completed
cursor, the consumer requests the missing interval.

The bridge does not treat an observer registration by itself as historical delivery. On either stream
error or normal completion, the consumer must resubscribe and explicitly catch up.

## Compatibility

`snapshotHeight` is an additive protobuf field. Older consumers ignore it. Older bridges return its
default value zero, which must not be treated as an authoritative cursor by upgraded consumers.

The continuity-aware subscription RPC is additive and the existing live subscription remains
available for older consumers. Upgraded consumers require the continuity-aware RPC; a coordinated
deployment must therefore upgrade the bridge before the consumer.
