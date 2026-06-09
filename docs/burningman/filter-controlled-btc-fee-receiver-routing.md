# Filter-Controlled BTC Fee Receiver Routing Spec

Date: 2026-06-09

## Status

This spec describes BTC trade-fee receiver routing in `BtcFeeReceiverService` when the signed network `Filter` provides
additional BTC fee receivers. It extends the Burning Man BTC-fee receiver selection described in
`docs/burningman/Burningman-specification.md`.

## Scope

This routing applies only to normal BTC trade fees. It is not used for delayed payout transaction receiver selection,
which remains deterministic and trade-protocol-sensitive.

The relevant implementation classes are:

- `bisq.core.filter.Filter`
- `bisq.core.filter.FilterPolicyService`
- `bisq.core.dao.burningman.BtcFeeReceiverService`
- `bisq.core.provider.mempool.MempoolService`
- `bisq.desktop.main.overlays.windows.FilterWindow`

## Filter Field

The signed network filter uses the restored protobuf field:

```protobuf
repeated string btc_fee_receiver_addresses = 20;
```

The same field is included in the canonical `Filter` schema as field `20` so signed-filter hash preimages stay aligned
with protobuf field numbering.

## Entry Format

Each repeated filter entry can contain one or more receiver specifications.

Supported forms:

- weighted receiver: `address#fraction`
- multiple weighted receivers in one entry: `address1#0.2;address2#0.3`
- legacy plain address: `address`

Entries are split on semicolons after the filter list has been read. Empty semicolon segments are ignored.

Weighted and plain entries must not be mixed in the same filter configuration. A configuration containing both
`address#fraction` and `address` entries is invalid.

## Weighted Fractions

Weighted fractions are decimal probabilities.

Requirements:

- `0.0001 <= fraction <= 1`
- total weighted fraction sum must be `<= 1.0`
- address part must not be empty
- fraction part must be a decimal value

The lower bound comes from the existing 10,000-point receiver-selection ceiling. One selection point is 0.01%, so a
fraction below `0.0001` would round down to zero probability and is rejected.

## Selection Algorithm

Selection uses `10_000` total weight points.

For weighted filter receivers:

```text
filterWeight = floor(fraction * 10000)
remainingBurningManWeight = 10000 - sum(filterWeights)
```

The configured filter receivers are added first with their explicit weights. The remaining weight is then delegated to
Burning Man receiver selection.

For the Burning Man remainder:

```text
candidateWeight = floor(cappedBurnAmountShare * remainingBurningManWeight)
```

If active Burning Man candidates do not fill the whole remainder because of rounding, caps, or missing receiver
addresses, the gap is assigned to the legacy Burning Man address. If there are no active Burning Man candidates, the
whole remainder is assigned to the legacy Burning Man address.

The final receiver is selected with the existing weighted random selector over the combined filter-receiver and Burning
Man receiver weights.

## Example

Filter value:

```text
address1#0.2;address2#0.3
```

Allocated weights:

- `address1`: `2_000` points, 20% total probability
- `address2`: `3_000` points, 30% total probability
- Burning Man receivers: `5_000` points, 50% total probability

If a Burning Man has a `10%` capped burn amount share, that candidate receives:

```text
floor(0.10 * 5000) = 500
```

That is 5% total receiver probability.

## Legacy Plain Address Mode

If all filter entries are plain addresses with no `#`, the filter uses legacy behavior:

- all configured addresses participate with equal relative weight;
- Burning Man receivers are not used for that selection;
- the filter receivers therefore cover 100% of the selection probability.

Example:

```text
address1,address2
```

This gives `address1` and `address2` equal probability.

## Failure Handling

Runtime selection treats invalid filter receiver configuration as non-fatal:

1. `BtcFeeReceiverService` logs a warning.
2. The invalid filter receiver configuration is ignored.
3. Selection falls back to normal Burning Man BTC-fee receiver routing.

The developer filter window validates the configuration before publishing a filter, so invalid configurations should
normally be rejected before they reach the network.

## Mempool Validation

BTC fee transaction mempool validation must accept any configured filter receiver address. `MempoolService` extracts the
configured addresses from the active filter and adds them to the known BTC fee receiver list.

If the filter receiver configuration is invalid, the extracted address list is empty and mempool validation continues to
use the existing DAO donation and Burning Man receiver addresses.

## Compatibility

The protobuf field number and name are the historical filter field:

```protobuf
repeated string btc_fee_receiver_addresses = 20;
```

Restoring the same field number preserves wire compatibility with older payloads that used this field. Filters that do
not set the field continue to use normal Burning Man BTC-fee receiver routing.

Because the field participates in canonical filter encoding, adding or changing configured receivers changes the signed
filter hash preimage.
