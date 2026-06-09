# Burning Man Model Technical Spec

This document specifies the Burning Man model implemented in `bisq.core.dao.burningman` and its subpackages. It is
intended as a technical reference for candidate construction, share calculation, payout receiver selection, burn targets,
address-list enforcement, and accounting. Public context is available at `https://bisq.wiki/Burning_Men`; implementation
details below are taken from the Java package and tests.

## Scope

The model has four distinct layers:

- Candidate and share model: deterministic DAO-derived state used by payout selection.
- Delayed payout transaction (DPT) receiver model: deterministic trade-protocol output generation and verification.
- BTC trade-fee receiver model: weighted random receiver selection for normal trade fees.
- Presentation and accounting model: UI targets, historical received BTC, and profit/balance reporting.

Parameters in `BurningManService` and `DelayedPayoutTxReceiverService` are protocol-sensitive. Changing them can make
two peers derive different DPT outputs and fail trade verification unless the change is coordinated as a protocol
upgrade.

## Units

- BSQ amounts are stored in the DAO's smallest BSQ unit.
- BTC amounts are satoshis.
- Burn-target BTC-fee estimates are stored as BSQ smallest-unit values; the code comment states `100` units equal
  `1 BSQ`.
- Percentages are represented as `double` shares in the range `[0, 1]`, for example `0.11` for 11%.

## Main Parameters

| Parameter | Value | Applies to | Source |
| --- | ---: | --- | --- |
| Genesis output name prefix | `Bisq co-founder ` | Genesis candidate names and burn preimages | `BurningManService.GENESIS_OUTPUT_PREFIX` |
| Genesis output amount factor | `0.1` | Fixed issuance weight for genesis outputs | `BurningManService.GENESIS_OUTPUT_AMOUNT_FACTOR` |
| Compensation issuance decay window | `24` DAO cycles | Candidate compensation share | `NUM_CYCLES_COMP_REQUEST_DECAY` |
| Burn amount decay window | `12` DAO cycles | Candidate burn share | `NUM_CYCLES_BURN_AMOUNT_DECAY` |
| Issuance boost factor | `10` | Maximum burn share from compensation share | `ISSUANCE_BOOST_FACTOR` |
| Maximum non-legacy burn share | `0.11` | Candidate cap for fee/DPT receiver share | `MAX_BURN_SHARE` |
| Burn-target reimbursement window | `12` DAO cycles | Burn target | `BurnTargetService.NUM_CYCLES_BURN_TARGET` |
| Average distribution window | `3` DAO cycles | Expected revenue display | `NUM_CYCLES_AVERAGE_DISTRIBUTION` |
| Burn target activation block | Mainnet `769845`, regtest `111` | Reimbursement adjustment | `BurnTargetService.ACTIVATION_BLOCK` |
| Default estimated BTC trade fees per cycle | `6_200_000` | Burn target when DAO param is default | `DEFAULT_ESTIMATED_BTC_TRADE_FEE_REVENUE_PER_CYCLE` |
| UI burn target boost | `10_000_000` | Suggested upper burn target only | `BURN_TARGET_BOOST_AMOUNT` |
| DPT snapshot grid | `10` blocks | DPT receiver selection height | `SNAPSHOT_SELECTION_GRID_SIZE` |
| Minimum DPT snapshot height | Mainnet `767950`, regtest `0` | DPT receiver selection | `MIN_SNAPSHOT_HEIGHT` |
| Minimum DPT output amount | `1_000` sats | DPT output filtering | `DPT_MIN_OUTPUT_AMOUNT` |
| Minimum DPT remainder to legacy BM | `25_000` sats | Legacy fallback output | `DPT_MIN_REMAINDER_TO_LEGACY_BM` |
| Minimum DPT fee rate | `10` sat/vbyte | DPT fee calculation | `DPT_MIN_TX_FEE_RATE` |
| Address-list share tolerance | `0.5` | DPT address-list share filter | `BM_ADDRESS_LIST_SHARE_RANGE_TOLERANCE` |
| BTC-fee selection granularity | `10_000` weight units | Random BTC-fee receiver selection | `BtcFeeReceiverService` |
| Accounting earliest block | Mainnet `656035`, regtest `111` | BM accounting store | `EARLIEST_BLOCK_HEIGHT` |
| Accounting earliest month | Java month `10` in `2020` (November 2020) | Balance UI month range | `EARLIEST_DATE_YEAR/MONTH` |
| Historical BSQ price last month | Java month `10` in `2022` (November 2022) | Accounting BSQ conversion cache | `HIST_BSQ_PRICE_LAST_DATE_YEAR/MONTH` |
| Accounting block/tx hash truncation | Last `4` bytes | Compact accounting storage and signatures | `AccountingBlock`, `AccountingTx` |
| Accounting output max storage value | `< Integer.MAX_VALUE` sats | Protobuf output amount | `AccountingTxOutput` |

## Candidate Construction

`BurningManService.getBurningManCandidatesByName(chainHeight)` builds a deterministic `TreeMap` keyed by candidate name.
Candidates come from accepted compensation proposals and from genesis transaction outputs.

### Compensation Candidates

For every `CompensationProposal`:

1. The proposal must have a matching DAO `Issuance`.
2. The issuance height must be `<= chainHeight`.
3. The candidate name is `CompensationProposal.getName()`.
4. The receiver address is selected as follows:
   - If the compensation proposal has `burningManReceiverAddress`, use it as a custom address.
   - Otherwise inspect the compensation request tx:
     - If the tx has 4 outputs, use output index `2`, the BTC change output.
     - Otherwise use output index `1`, the BSQ candidate output.
5. The issuance amount is usually `Issuance.getAmount()`, except tx
   `01455fc4c88fca0665a5f56a90ff03fb9e3e88c3430ffc5217246e32d180aa64`, which is forced to `119400` to exclude the
   conference sponsorship reimbursement portion.
6. Refund-agent reimbursement artifacts are filtered out: name `RefundAgent`, cycle index `<= 15`, and issuance amount
   `> 350000`.
7. The amount is decayed over 24 DAO cycles and stored as a `CompensationModel`.

### Genesis Candidates

For each genesis tx output:

1. The candidate name is `Bisq co-founder ` plus the output index.
2. The receiver address is the output address.
3. The issuance amount is the genesis output value.
4. The decayed amount is `Math.round(amount * 0.1)`.
5. The compensation model records the genesis tx id and output index.

Genesis output candidates use the same burn preimage scheme as compensation candidates.

### Burn Outputs

Burn outputs are proof-of-burn transactions whose OP_RETURN hash matches the candidate name:

1. Candidate name bytes are UTF-8.
2. The preimage hash is generated with `ProofOfBurnConsensus.getHash`.
3. DAO proof-of-burn OP_RETURN tx outputs are grouped by `ProofOfBurnConsensus.getHashFromOpReturnData`.
4. Matching outputs with block height `<= chainHeight` are attached to the candidate.
5. The raw burned amount is `Tx.getBurntBsq()`.
6. The decayed burned amount is calculated over 12 DAO cycles.

Duplicate `BurnOutputModel` and `CompensationModel` instances are ignored by set semantics.

## Decay Formula

Both compensation and burn decay use `BurningManService.getDecayedAmount`:

```text
fromBlock = cyclesInDaoStateService.getChainHeightOfPastCycle(currentHeight, numCycles)

if currentHeight <= fromBlock:
    decayed = amount
else:
    factor = max(0, (issuanceHeight - fromBlock) / (currentHeight - fromBlock))
    decayed = max(0, Math.round(amount * factor))
```

The result is linear:

- Amounts at the current height count 100%.
- Amounts at the lookback boundary count as 0%.
- Amounts older than the lookback boundary also count as 0%.
- Rounding is Java `Math.round`.

Invalid inputs throw:

- `issuanceHeight > currentBlockHeight`
- negative current height
- negative amount
- negative issuance height

## Receiver Address Selection and Validation

Each candidate has one current receiver address:

- If any compensation model used a custom address, only custom addresses are considered. The most recent custom address
  wins; same-height ties choose the lexicographically smallest address.
- If no custom address exists, non-custom addresses are considered. The earliest address wins; same-height ties choose
  the lexicographically smallest address.
- Genesis candidates have a non-custom address from the genesis output.

`BtcAddressValidator` validates the selected address. A candidate with a missing or invalid receiver address has maximum
boosted compensation share `0`, even if it has compensation and burns. Active Burning Men must have:

- `cappedBurnAmountShare > 0`
- a valid receiver address

## Share Calculation

For all candidates at a given chain height:

```text
compensationShare = candidate.decayedCompensation / totalDecayedCompensation
burnAmountShare   = candidate.decayedBurn / totalDecayedBurn
```

If the corresponding total is zero, the share is zero.

The candidate's maximum allowed receiver share is:

```text
maxBoostedCompensationShare =
    validReceiverAddress
        ? min(MAX_BURN_SHARE, compensationShare * ISSUANCE_BOOST_FACTOR)
        : 0
```

With current parameters:

```text
maxBoostedCompensationShare = min(0.11, compensationShare * 10)
```

This means compensation share controls the cap, while burned BSQ controls the attempted receiver share.

## Capping Algorithm

The capping algorithm prevents any non-legacy candidate from receiving more than their boosted compensation cap.

1. Sort candidates by descending `burnCapRatio`.

```text
burnCapRatio = burnAmountShare > 0
    ? burnAmountShare / maxBoostedCompensationShare
    : 0
```

2. Initialize:

```text
thresholdBurnCapRatio = 1.0
remainingBurnShare = 1.0
remainingCapShare = 1.0
cappingRound = 0
```

3. For each candidate in sorted order:

```text
invScaleFactor = remainingBurnShare / remainingCapShare

if remainingCapShare <= 0
   or burnCapRatio <= 0
   or burnCapRatio < invScaleFactor:
    cappingRound += 1
    stop

if burnCapRatio < thresholdBurnCapRatio:
    thresholdBurnCapRatio = invScaleFactor
    cappingRound += 1

candidate.imposeCap(cappingRound, burnAmountShare / thresholdBurnCapRatio)
remainingBurnShare -= burnAmountShare
remainingCapShare -= maxBoostedCompensationShare
```

4. Compute:

```text
sumAllCappedBurnAmountShares =
    sum(maxBoostedCompensationShare for candidates with roundCapped)

sumAllNonCappedBurnAmountShares =
    sum(burnAmountShare for candidates without roundCapped)
```

5. Each candidate finalizes adjusted and capped shares:

- Already-capped candidates set `cappedBurnAmountShare = maxBoostedCompensationShare`.
- Non-capped candidates keep `burnAmountShare` if no one was capped.
- Otherwise the remaining receiver share is redistributed to non-capped candidates:

```text
distributionBase = 1 - sumAllCappedBurnAmountShares
adjustment = distributionBase / sumAllNonCappedBurnAmountShares
adjustedBurnAmountShare = burnAmountShare * adjustment
```

- If the adjusted share is below the candidate's cap, it becomes the capped share.
- If the adjusted share reaches or exceeds the cap, the candidate is capped at `maxBoostedCompensationShare`; any
  residual gap is left for the legacy Burning Man.

Exact floating-point behavior is part of DPT determinism. Tests assert specific double results, including expected
floating-point rounding artifacts.

## Legacy Burning Man

The legacy Burning Man is a fallback, not a normal candidate in the main candidate map.

- DPT legacy address comes from DAO param `RECIPIENT_BTC_ADDRESS` at the relevant chain height, unless an enforceable
  Burning Man address list supplies its own `legacyBurningManAddress`.
- BTC-fee legacy address for historical accounting is fixed:
  `38bZBj5peYS3Husdz7AH3gEUiUbYRD951t`.
- `LegacyBurningMan` does not apply compensation-share caps.
- Presentation computes legacy DPT/BTC-fee share as:

```text
1 - sum(cappedBurnAmountShare of all normal candidates)
```

Legacy proof-of-burn markers:

| Category | Mainnet OP_RETURN data | Regtest OP_RETURN data |
| --- | --- | --- |
| DPT | `1701e47e5d8030f444c182b5e243871ebbaeadb5e82f` | `170114af04ea7e34bd7378b034ddf90da53b7c27a277` |
| DPT | `1701293c488822f98e70e047012f46f5f1647f37deb7` | same regtest DPT marker as above |
| BTC fees | `1701721206fe6b40777763de1c741f4fd2706d94775d` | `1701b3253b7b92bb7f0916b05f10d4fa92be8e48f5e6` |

Regtest DPT and fee markers use preimages `dpt` and `fee`, respectively.

## BTC Trade Fee Receiver Selection

`BtcFeeReceiverService.getAddress()` selects a receiver for normal BTC trade fees.
When the signed network filter provides BTC fee receiver addresses, filter-controlled receiver weights are applied before
the remaining probability is delegated to the Burning Man selection below. See
`docs/burningman/filter-controlled-btc-fee-receiver-routing.md` for that filter-controlled routing spec.

1. Get active candidates at the current DAO chain height.
2. If none exist, return the legacy Burning Man address.
3. Convert each candidate share to integer weight:

```text
weight = floor(cappedBurnAmountShare * 10000)
```

This gives 0.01% granularity. Zero-weight entries are effectively ignored.

4. Sum weights. If the sum is below `10000`, append a legacy Burning Man weight for the missing remainder.
5. Draw a random target from `[1, sum]` and return the cumulative-weight winner.
6. If the winner is the appended legacy entry, return the legacy Burning Man address.

This selection is weighted random and not deterministic across peers. It is not used for DPT verification.

## Delayed Payout Transaction Receiver Selection

DPT receiver selection is deterministic and trade-protocol-sensitive.

### Snapshot Height

Peers use a delayed snapshot height to reduce mismatch risk when peers have slightly different DAO heights:

```text
grid = 10

if chainHeight > genesisHeight + 3 * grid:
    ratio = Math.round(chainHeight / (double) grid)
    snapshot = ratio * grid - grid
else:
    snapshot = genesisHeight

snapshot = max(snapshot, MIN_SNAPSHOT_HEIGHT)
```

Mainnet `MIN_SNAPSHOT_HEIGHT` is `767950`; regtest uses `0`.

Examples with genesis height `102`, grid `10`, and min height `0`:

- `139 -> 130`
- `140 -> 130`
- `141 -> 130`
- `149 -> 140`
- `1000 -> 990`

`getReceivers` rejects selection heights below `MIN_SNAPSHOT_HEIGHT`.

### Fee and Spendable Amount

The DPT fee rate is derived from trade setup:

```text
depositTxSizeForRate = 278
txFeePerVbyte = max(10, Math.round(tradeTxFee / 278))
```

Spendable amount:

```text
txSize = 51 + numOutputs * 32
minerFee = txFeePerVbyte * txSize
minerFee = max(TradeWalletService.MIN_DELAYED_PAYOUT_TX_FEE.value, minerFee)
spendableAmount = inputAmount - minerFee
```

When no active candidates remain after filtering, `numOutputs` is `1` and the whole spendable amount goes to legacy BM.
Otherwise `numOutputs` is the filtered candidate count, not the unfiltered count.

### Output Bounds

Candidate outputs are filtered by:

```text
minOutputAmount = max(1000, txFeePerVbyte * 32 * 2)
maxOutputAmount = Math.round(spendableAmount * (0.11 * 1.2))
```

The max output check allows 20% headroom over the 11% max share, so the effective sanity bound is 13.2% of spendable
amount.

### Candidate Output Calculation

Small candidate shares are excluded from the adjustment denominator:

```text
adjustment = 1 - sum(candidate.cappedBurnAmountShare
                    for candidate if round(candidate.cappedBurnAmountShare * spendableAmount) < minOutputAmount)
```

Each candidate output is then calculated:

```text
amount = Math.round(candidate.cappedBurnAmountShare / adjustment * spendableAmount)
```

Outputs are retained only if:

- candidate has a receiver address
- `amount >= minOutputAmount`
- `amount <= maxOutputAmount`

Retained outputs are sorted by amount, then by receiver address.

If retained outputs sum to less than `spendableAmount`, the remainder is handled as follows:

- If `remainder > 25000` sats, append a legacy BM output for that amount.
- Otherwise leave the remainder as additional miner fee.

Known implementation quirk: the code comment notes that small outputs should be filtered out before adjustment, but the
current implementation filters them after adjustment. An output just under the threshold can be included after adjustment
and cause the DPT to underpay relative to the intended distribution.

## Burning Man Address Lists

Bundled address lists protect DPT receivers against manipulated DAO data.

Resource files live in:

```text
p2p/src/main/resources/burningman/
```

File names match:

```text
bm-addresses-vNNNN.json
```

where `NNNN` is at least four digits.

Schema version is currently `1`. Each file contains:

- `schemaVersion`
- `listVersion`
- `network`
- `chainHeight`
- `burningManSelectionHeight`
- `legacyBurningManAddress`
- sorted `entries`

Each entry contains:

- `receiverAddress`
- `cappedBurnAmountShare`

Loader validation requires:

- schema version equals `1`
- filename version equals `listVersion`
- non-blank `network`
- positive `chainHeight`
- positive `burningManSelectionHeight`
- non-blank `legacyBurningManAddress`
- non-empty entries
- non-blank, unique, lexicographically sorted receiver addresses
- finite, non-negative `cappedBurnAmountShare`

Mainnet requires at least one address list for the current network. Non-mainnet modes can load bundled lists without
applying filtering if no list matches the current network.

### Version Negotiation

Peers exchange supported versions and select the highest common version.

Peer version lists must be:

- non-null
- non-empty
- positive
- distinct
- sorted ascending

If no common version exists, selection throws. A version `<= 0` disables address-list filtering and validation.

### DPT Filtering

If the selected address list is enforceable for the current network:

1. Allowed addresses are all entry receiver addresses plus the list's legacy BM address.
2. A candidate is skipped if its receiver address is missing from the allowlist.
3. A candidate is skipped if its address has no reference `cappedBurnAmountShare`, except the legacy address.
4. A candidate is skipped if its actual `cappedBurnAmountShare` is outside:

```text
lower = max(0, referenceShare * (1 - 0.5))
upper = referenceShare * (1 + 0.5)
```

5. Receiver validation rejects any DPT output address outside the allowlist.

The address list does not directly set payout amounts. It filters candidate addresses and share ranges; the actual
output amounts are still computed from local DAO state at the selected snapshot height.

### Export

When `--dumpBurningManData=true` is enabled and DAO parsing is complete, `BurningManDataExportService` exports a new
address list to the app data directory:

- `listVersion = latestBundledVersion + 1`
- `chainHeight = current DAO chain height`
- `burningManSelectionHeight = current DPT snapshot height`
- `legacyBurningManAddress = legacy address at the snapshot height`
- entries are active candidates grouped by receiver address, with duplicate-address shares merged

The exported file name is `bm-addresses-v%04d`. Released list files should be append-only and immutable.

## Burn Target

`BurnTargetService.getBurnTarget(chainHeight, candidates)` computes:

```text
burnTarget =
    accumulatedReimbursements
  + accumulatedEstimatedBtcTradeFees
  - burnedAmountFromLegacyBurningManDPT
  - burnedAmountFromLegacyBurningMansBtcFees
  - burnedAmountFromBurningMen
```

The result can be negative.

### Reimbursements

Reimbursements are DAO issuances of type `REIMBURSEMENT` with matching `ReimbursementProposal` tx ids and height
`<= chainHeight`.

Only reimbursements with height:

```text
height > getChainHeightOfPastCycle(chainHeight, 12)
height <= chainHeight
```

are included.

For reimbursements after activation block `769845` on mainnet, the amount is adjusted:

```text
adjusted = Math.round(amount * 1.3 / 1.15)
```

The adjustment estimates the DPT value received by the BM because the losing party's security deposit is not reimbursed.
Older reimbursements are not adjusted.

### Estimated BTC Trade Fees

Estimated BTC trade fees are accumulated over DAO cycles from:

```text
getHeightOfFirstBlockOfPastCycle(chainHeight, 11)
```

through the current chain height.

For each cycle, `Param.LOCK_TIME_TRADE_PAYOUT` is read at the cycle's first block:

- If the param value is not the default `4320`, that value is used as the estimated BTC trade-fee revenue.
- If the param value is `4320`, the default estimate `6_200_000` is used.

This reuse of `LOCK_TIME_TRADE_PAYOUT` is intentional in the implementation but should be treated carefully when
changing DAO params.

### Burn Deductions

The burn target subtracts raw, non-decayed burn amounts from the last 12 cycles:

- legacy DPT burns identified by legacy DPT OP_RETURN markers
- legacy BTC-fee burns identified by legacy BTC-fee OP_RETURN markers
- normal Burning Man proof-of-burn outputs attached to candidates

## Presentation Targets

`BurningManPresentationService` exposes UI-derived values:

- `boostedBurnTarget = burnTarget + 10_000_000`
- `averageDistributionPerCycle = round((reimbursements + estimatedBtcTradeFees over last 3 cycles) / 3)`
- `expectedRevenue = round(candidate.cappedBurnAmountShare * averageDistributionPerCycle)`

Candidate burn-target suggestions return a lower and upper amount:

1. If boosted target `<= 0` or candidate compensation share is `0`, return `(0, 0)`.
2. Lower base target:

```text
round(burnTarget * min(0.11, compensationShare))
```

3. Upper base target:

```text
round((burnTarget + 10_000_000) * maxBoostedCompensationShare)
```

4. If no BSQ has been decayed-burned by all candidates, return `(lowerBaseTarget, upperBaseTarget)`.
5. If the candidate is below cap, compute missing amount:

```text
others = totalBurnedAmount - myBurnAmount
targetAmount = myTargetShare / (1 - myTargetShare) * others
missing = Math.round(targetAmount) - myBurnAmount
```

6. If `missing < 546`, lower target is displayed as `0`.
7. Lower target is capped at `upperBaseTarget`.
8. If the candidate is already at cap, lower target is `0` and upper target remains `upperBaseTarget`.

The dust cutoff for suggested burn amount is `546` BSQ units.

## Accounting Model

Accounting is for UI reporting and historical balances. It does not drive DPT receiver construction.

### Node Selection

`AccountingNodeProvider` chooses:

- full node if either the BM full-node preference or command-line option is enabled and RPC user, password, and
  block-notify port are configured
- lite node otherwise

Accounting processing only starts when the user preference `processBurningManAccountingData` is enabled. Nodes start
after initial DAO parsing completes.

### Store

The persisted store file is `BurningManAccountingStore_v3`. It stores contiguous `AccountingBlock` objects starting at:

- mainnet `656035`
- regtest `111`

If initialized with non-connecting blocks, the store keeps blocks only up to the first gap. Adding a block requires:

- height equals last height plus one, or earliest height for the first block
- truncated previous hash equals last block truncated hash

On reorg handling, nodes purge the last 10 accounting blocks and retry. The shared retry limit is 5 attempts.

### Accounting Block Format

`AccountingBlock` stores:

- height
- block time in seconds
- last 4 bytes of block hash
- last 4 bytes of previous block hash
- compact accounting transactions

`AccountingTx` stores:

- type: `BTC_TRADE_FEE_TX` or `DPT_TX`
- outputs
- last 4 bytes of tx id

`AccountingTxOutput` stores:

- amount as a positive int in protobuf, requiring `< Integer.MAX_VALUE` sats
- Burning Man name, with legacy names shortened to `LBMF` or `LBMD` in storage

### Parser Rules

The full node parses raw Bitcoin Core blocks and first filters txs whose first output address is a known BM address.
The genesis tx id is ignored.

DPT accounting tx detection is narrow. A tx is classified as `DPT_TX` if:

- it has exactly one input
- locktime is a block-height locktime, i.e. `< 500000000`
- first input sequence is `TransactionInput.NO_SEQUENCE - 1` (`0xfffffffe`)
- all outputs go to known BM receiver addresses, including legacy addresses
- all outputs use expected script type:
  - `PUB_KEY_HASH`
  - `SCRIPT_HASH`
  - `WITNESS_V0_KEYHASH`
- the witness has 4 chunks:
  - empty first element
  - two signatures, each 140 to 146 hex characters
  - redeem script of 142 hex characters
- redeem script has 2-of-2 multisig shape:
  - starts with `5221`
  - byte separator at positions 70-72 is `21`
  - ends with `52ae`
- every output value is `<= 6 BTC`

BTC trade-fee tx detection is broader and can have false positives. A tx is classified as `BTC_TRADE_FEE_TX` if:

- it has 2 or 3 outputs
- first output address is a known BM address
- first output value is `> 2500` sats
- first output value is `< 10_000_000` sats
- first output uses an expected script type

For BTC-fee txs only the first output is stored.

### Full and Lite Accounting Network

Full nodes:

- parse Bitcoin Core blocks
- store accounting blocks
- publish new accounting blocks when P2P is ready
- answer `GetAccountingBlocksRequest`
- sign block broadcasts and block-range responses

Lite nodes:

- request blocks from seed nodes starting at the next missing accounting height
- verify signatures before processing responses or broadcasts
- rebroadcast received new-accounting-block messages once per truncated block hash
- request missing blocks if a wallet best-block notification is not followed by accounting data within 10 seconds

Signature validation uses hard-coded permitted pubkeys unless dev privilege keys are enabled:

```text
02640325af0cc68462664cfacdb4b59754156f293c04aae32b5c8b1650f914fe61
027056a287f80591bde6fff24451c99b2e518bd40a8a04d079aba477e1180f603d
```

Network timing constants:

- full-node handler cleanup timer: 120 seconds
- lite-node retry delay: 10 seconds
- lite-node max seed retry count: 12
- request/response timeout: 3 minutes
- duplicate handler cleanup timer: 120 seconds

### Balance Reporting

Balances are grouped by Burning Man name.

Received BTC entries are built from accounting tx outputs:

- tx id is the last 4 bytes of the Bitcoin tx id, hex-encoded
- amount is BTC sats
- type is `BTC_TRADE_FEE_TX` or `DPT_TX`
- month is the start of the tx month

Burned BSQ entries are built from each candidate's `BurnOutputModel`:

- tx id is the burn tx id
- amount is raw burned BSQ, not decayed amount
- type is `BURN_TX`
- month is the start of the burn month

Monthly balance entries combine received BTC and burned BSQ for months from November 2020 to the current month.

Distributed BTC totals exclude both legacy Burning Man names:

- `Legacy Burningman (DPT)`
- `Legacy Burningman (BTC fees)`

BTC-to-BSQ conversion uses monthly average BSQ price:

- hard-coded historical monthly data through November 2022
- 30-day average price from trade statistics for later months
- entries without price data are skipped for BSQ-converted totals

## Consensus and Change Safety

Treat these as frozen unless a coordinated protocol migration is planned:

- candidate construction rules
- compensation and burn decay windows
- decay formula and Java rounding behavior
- issuance boost factor
- 11% cap
- capping algorithm ordering and double arithmetic
- DPT snapshot-height formula
- DPT fee/spendable/output bounds
- DPT address-list negotiation and filtering
- DPT output sorting

These can generally be changed with lower protocol risk, but still affect user-facing behavior or historical reporting:

- presentation-only burn target boost
- accounting parser false-positive thresholds
- accounting storage version
- average price calculation
- data-export process

## Known Quirks and Risks

- DPT receiver output calculation has a documented FIXME: small outputs are filtered after adjustment, so near-threshold
  outputs can be included after adjustment and underpay the DPT relative to the intended distribution.
- BTC-fee accounting detection can have false positives because it does not have full UTXO context.
- Accounting uses 4-byte truncated hashes and tx ids to save space. This is accepted by the implementation but is not a
  collision-proof identifier.
- The burn target reads `Param.LOCK_TIME_TRADE_PAYOUT` as an estimated BTC trade-fee revenue parameter and treats the
  default value `4320` specially.
- Current receiver address validation depends on `BtcAddressValidator`; unsupported address types receive a zero cap
  even if syntactically valid at the Bitcoin protocol level.
- Exact floating-point output of the capping algorithm matters for DPT determinism.

## Related Files

- `core/src/main/java/bisq/core/dao/burningman/BurningManService.java`
- `core/src/main/java/bisq/core/dao/burningman/DelayedPayoutTxReceiverService.java`
- `core/src/main/java/bisq/core/dao/burningman/BtcFeeReceiverService.java`
- `core/src/main/java/bisq/core/dao/burningman/BurnTargetService.java`
- `core/src/main/java/bisq/core/dao/burningman/BurningManPresentationService.java`
- `core/src/main/java/bisq/core/dao/burningman/BurningManAddressList*.java`
- `core/src/main/java/bisq/core/dao/burningman/BurningManDataExportService.java`
- `core/src/main/java/bisq/core/dao/burningman/model/*.java`
- `core/src/main/java/bisq/core/dao/burningman/accounting/**/*.java`
- `core/src/test/java/bisq/core/dao/burningman/*.java`
- `docs/burning-man-address-list.md`
- `p2p/src/main/resources/burningman/bm-addresses-v*.json`
