# Burning Man address list protection

This document describes the versioned Burning Man address list used by the Bisq v1 trade protocol to protect delayed
payout transaction receivers against manipulated DAO data.

## Goal

The delayed payout transaction (DPT) pays Burning Man receivers derived from DAO state. If a trader is eclipsed or
otherwise receives malicious DAO data, an attacker could try to introduce an attacker-controlled Burning Man receiver
and make both peers create a DPT paying that address.

The address list protection limits DPT receivers to a release-bundled allowlist. New or manipulated DAO receiver
addresses are excluded unless they are present in the selected list version.

## Resource files

Bundled address lists are stored under:

```text
p2p/src/main/resources/burningman/
```

The file name is versioned independently from the Bisq application version:

```text
bm-addresses-v0001.json
bm-addresses-v0002.json
...
```

Each file contains:

- `schemaVersion`: JSON schema version.
- `listVersion`: monotonic BM address list version. This must match the version in the file name.
- `network`: base currency network, for example `BTC_MAINNET`.
- `chainHeight`: DAO chain height used for export.
- `burningManSelectionHeight`: snapshot height used for the exported receiver data.
- `legacyBurningManAddress`: fallback receiver for legacy BM payments.
- `entries`: active BM receiver addresses at export time.

Entry order is not significant. The loader accepts unsorted entries and validates receiver-address uniqueness rather
than imposing an order. The exporter preserves the order in which grouped receiver addresses are first encountered.

`cappedBurnAmountShare` is included as reference data. It is valid JSON for values to be written in scientific notation,
for example:

```json
{
    "receiverAddress": "bc1q7r6mg67a8verfjj205qnznvk425dlj9krduuuu",
    "cappedBurnAmountShare": 4.573561114277962E-4
}
```

Gson parses that as a normal `double`. The current DPT protection uses the resource file as an address allowlist;
`cappedBurnAmountShare` from the resource is not used to calculate DPT payouts.

## Export process

A release manager can export the current BM data from a synced mainnet app with:

```text
--dumpBurningManData=true
```

The file is written to the app data directory, not into the database storage directory. The exporter uses the latest
bundled BM list version plus one.

Release process:

1. Sync DAO data on mainnet.
2. Export the current BM address list.
3. Compare it with the latest bundled list.
4. Add the new file under `p2p/src/main/resources/burningman/`.
5. Keep all historical list files. Old trades may need their selected version for verification.

Only append new versions. Do not modify an already released file. As `cappedBurnAmountShare` changes with each block we
need to update it with each release even if no new addresses have been added.

## Runtime loading

At startup, all bundled address list resources are loaded into a sorted map keyed by `listVersion`. The service exposes:

- supported versions.
- latest version.
- lookup by version.
- highest-common-version selection for trade setup.

Mainnet requires at least one matching mainnet resource file. Non-mainnet modes may load bundled files without applying
filtering when no resource matches the current network.

## Trade protocol

The first trade protocol messages exchange supported BM address list versions:

- `InputsForDepositTxRequest.supported_burning_man_address_list_versions`
- `InputsForDepositTxResponse.supported_burning_man_address_list_versions`

The lists must be non-empty, positive, distinct, and sorted.

After receiving the peer list, each side selects the highest common version. The selected version is persisted in:

- `ProcessModel.burningManAddressListVersion`
- `Contract.burningManAddressListVersion`

The contract field is part of the signed contract JSON. If peers do not select the same version, their contract JSON or
DPT will not match.

## DPT creation and verification

When the DPT receiver list is generated, the selected BM address list version is passed into the receiver service.

The receiver service:

1. Gets active BM candidates from DAO state at the selected snapshot height.
2. Looks up the selected address list.
3. Removes candidates whose receiver address is not in the allowlist.
4. Logs skipped BM receivers.
5. Builds the DPT receiver outputs from the filtered candidate set.
6. Validates that every DPT receiver is present in the selected allowlist.

The same selected version is used by:

- seller DPT creation.
- buyer verification of prepared DPT.
- buyer verification of final DPT.
- refund/arbitration DPT receiver verification.

The DPT txid comparison remains the final consensus check between peers. If a peer uses a modified list or different DAO
data and creates different outputs, the DPT comparison fails.

## Hash support

A content hash is not required for the core protection.

The selected version is part of the signed contract, and both peers independently recreate and compare the DPT outputs.
A peer cannot profit by claiming a version while using different content, because the resulting contract or DPT will
differ.

A hash can still be useful as an operational and diagnostic tool:

- detect release packaging mistakes early.
- help arbitrators identify the exact resource content used.
- fail earlier in protocol setup if peers support the same version number but have different file content.

If added later, hash canonical content rather than raw pretty-printed JSON. For example, hash a deterministic
serialization of schema version, list version, network, heights, legacy address, and entries sorted by receiver address.
Hash support can be kept out of consensus at first by using it only in logs, tests, and release checks.

## Share range filter

The allowlist protects against new attacker-controlled receiver addresses. It does not by itself protect against
manipulated DAO data that assigns an excessive share to an already-allowed address. To reduce that risk, the receiver
service applies an additional share range filter after the address allowlist.

The range check is filter-only, not a direct trade failure condition:

1. The resource file stores reference share data per receiver.
2. The DPT receiver service first applies the address allowlist.
3. It then compares the DAO-provided `cappedBurnAmountShare` at the selected height against the resource range.
4. If the share is outside the range, that BM candidate is skipped and logged.
5. DPT outputs are calculated from the remaining candidates.

With this design a false positive only excludes a BM receiver from that DPT. It does not let an attacker add a receiver,
and it should not by itself fail the trade unless peers have different DAO data near the threshold and therefore produce
different DPTs.

Current range model:

```text
lower = max(0, baselineShare * (1 - relativeTolerance))
upper = baselineShare * (1 + relativeTolerance)
```

Where:

- `baselineShare` is the exported `cappedBurnAmountShare`.
- `relativeTolerance` is currently a constant set to `0.5`, meaning `+/-50%`.

The `+/-50%` value is intentionally a first conservative implementation constant and should be tuned with data. Before
changing it, run a release check over historical DAO data:

1. For each existing BM address, export shares at regular snapshot heights over a representative period.
2. Measure absolute and relative movement from the intended release baseline.
3. Report how many BMs would be skipped for candidate tolerances.
4. Choose tolerances that would have produced zero or near-zero skips historically.

For very small shares, a future absolute floor may be more appropriate than a pure relative bound. For example, a move
from `0.0004` to `0.0008` is a 100% relative change but still economically small. The release check should report both
relative and absolute deviations so tolerances are not biased against low-share contributors.

If explicit min/max range fields are added later, old resource files without those fields should remain valid by
deriving the range from `cappedBurnAmountShare`.

## Tests worth keeping

The following behavior should be covered by focused tests:

- bundled resource files load by version.
- file name version and `listVersion` must match.
- unsorted entries must load without being reordered; receiver addresses must remain non-blank and unique.
- supported version lists must be sorted, positive, distinct, and non-empty.
- highest common version selection returns the expected version.
- peer messages round-trip supported versions through protobuf.
- selected version persists in `ProcessModel`, `TradingPeer`, and `Contract`.
- DPT receiver generation skips unlisted BM addresses.
- DPT receiver generation skips listed BM addresses whose DAO-provided share is outside the selected list's share range.
- DPT receiver generation uses the filtered receiver count for fee calculation.
- receiver validation rejects outputs not present in the selected allowlist.
- refund/arbitration verification uses the contract's selected version.

## Operational notes

- Keep BM list versions monotonic and independent from Bisq application versions.
- Keep historical list files immutable.
- Add a new file with each release as the `cappedBurnAmountShare` changes with each block even if no new addresses have
  been added.
- Treat a large address set change as suspicious and review manually before release.
- Prefer failing early on no common version rather than falling back silently.
