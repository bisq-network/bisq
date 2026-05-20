# BSQ Swap Fee Handling

This document explains how BSQ swap trade fees and Bitcoin miner fees are represented in the swap transaction. The main implementation is in `BsqSwapCalculation`, with protocol checks in the BSQ swap buyer and seller tasks.

## Core Idea

A BSQ swap is one Bitcoin transaction with both BSQ-colored inputs and BTC inputs:

- The buyer contributes BSQ inputs.
- The seller contributes BTC inputs.
- The seller receives BSQ.
- The buyer receives BTC.
- Any input value not assigned to outputs becomes the Bitcoin transaction fee.

BSQ trade fees are not paid through separate fee transactions. They are represented as BSQ-colored satoshis that are not returned as BSQ outputs. From Bitcoin's point of view, those satoshis are part of the miner fee. From the DAO's point of view, they are burned BSQ trade fees.

The implementation therefore treats a participant's BSQ trade fee as already contributing toward that participant's share of the transaction miner cost.

## Role Fees

The trade stores two role-based trade fees:

- `makerFee`
- `takerFee`

Those are not the same as buyer/seller fees. The buyer-side and seller-side fee used in the transaction depends on the offer direction:

| Offer direction | Maker role | Taker role | Buyer trade fee | Seller trade fee |
| --- | --- | --- | --- | --- |
| `BUY` | Buyer | Seller | `makerFee` | `takerFee` |
| `SELL` | Seller | Buyer | `takerFee` | `makerFee` |

BSQ swap offer payloads do not carry a maker fee like Bisq v1 offers do. For BSQ swap offers, `offer.getMakerFee()` can be zero on the offer object. The actual `makerFee` and `takerFee` for the selected trade amount are supplied in the take-offer request and validated by the maker before the trade is started.

## Per-Side Miner Contribution

For both buyer and seller, the code computes the BTC amount that this side must still contribute toward the miner fee as:

```text
adjustedTxFee = max(0, txFeePerVbyte * participantVBytes - participantTradeFee)
```

In code this is `BsqSwapCalculation.getAdjustedTxFee`.

`participantVBytes` is the estimated virtual size of that side's inputs plus the relevant output part:

```text
vBytes = 5 + sum(inputVBytes) + outputVBytes

segwit input:     68 vbytes
non-segwit input: 149 vbytes
with change:      62 output vbytes
without change:   31 output vbytes
```

The trade fee is subtracted because those BSQ satoshis are already absent from BSQ outputs and therefore already increase the transaction fee.

## Buyer Side

The buyer spends BSQ and receives BTC.

The buyer's BSQ input requirement is:

```text
buyersBsqInput = bsqTradeAmount + buyersTradeFee
```

The buyer's BTC payout is:

```text
buyersBtcPayout = btcTradeAmount - adjustedBuyerTxFee
```

If the buyer's BSQ trade fee already covers the buyer-side miner portion, `adjustedBuyerTxFee` is zero and the buyer receives the full BTC trade amount. The excess fee remains in the transaction as miner fee / burned BSQ.

## Seller Side

The seller spends BTC and receives BSQ.

The seller's BTC input requirement is:

```text
sellersBtcInput = btcTradeAmount + adjustedSellerTxFee
```

The seller's BSQ payout is:

```text
sellersBsqPayout = bsqTradeAmount - sellersTradeFee
```

This is the important seller-side detail: the BTC seller does not provide a separate BSQ input to pay the trade fee. The seller's trade fee is deducted from the BSQ payout. That deducted amount is absent from the BSQ outputs, so it contributes to the transaction fee/burn gap.

If the seller's trade fee is larger than the seller-side miner portion, `adjustedSellerTxFee` is zero. The seller then contributes only the BTC trade amount as BTC input, and the seller-side trade fee alone covers that side's miner contribution.

## Actual Transaction Fee

Ignoring dust and wallet coin-selection excess, the Bitcoin transaction fee is:

```text
actualTxFee =
    buyersTradeFee
  + sellersTradeFee
  + adjustedBuyerTxFee
  + adjustedSellerTxFee
```

Equivalently, each side contributes:

```text
sideTxFeeContribution = max(participantTradeFee, txFeePerVbyte * participantVBytes)
```

Dust change can increase the actual miner fee. When buyer BSQ change or seller BTC change would be dust, the code drops that change output and lets the value fall through to miner fee.

## Example

Assume:

```text
btcTradeAmount     = 100,000,000 sat
bsqTradeAmount     =   5,000,000 BSQ sat
buyersTradeFee     =          50 BSQ sat
sellersTradeFee    =         150 BSQ sat
txFeePerVbyte      =          10 sat/vbyte
buyersVBytes       =         200
sellersVBytes      =         200
```

Then:

```text
buyer miner portion  = 10 * 200 = 2,000 sat
seller miner portion = 10 * 200 = 2,000 sat

adjustedBuyerTxFee  = 2,000 -  50 = 1,950 sat
adjustedSellerTxFee = 2,000 - 150 = 1,850 sat
```

Transaction amounts:

```text
buyer BSQ input   = 5,000,000 + 50 = 5,000,050
seller BTC input  = 100,000,000 + 1,850 = 100,001,850
seller BSQ output = 5,000,000 - 150 = 4,999,850
buyer BTC output  = 100,000,000 - 1,950 = 99,998,050

actual tx fee     = 50 + 150 + 1,950 + 1,850 = 4,000 sat
```

## Zero Clamp and the Old Negative-Fee Failure

Older code calculated:

```text
adjustedTxFee = txFeePerVbyte * participantVBytes - participantTradeFee
```

and then required `adjustedTxFee` to be positive. That fails whenever:

```text
participantTradeFee >= txFeePerVbyte * participantVBytes
```

This happened only for some offers because it depends on:

- the fee rate,
- the selected wallet inputs,
- whether inputs are segwit or non-segwit,
- whether there is change,
- and the trade amount, which determines the BSQ trade fee.

Low fee rates and small segwit input sets make the failure more likely. Larger or non-segwit input sets increase `participantVBytes`, so they make the negative case less likely.

The current behavior clamps the adjusted BTC miner contribution at zero:

```text
adjustedTxFee = max(0, txFeePerVbyte * participantVBytes - participantTradeFee)
```

This is valid because the BSQ trade fee is already in the miner-fee pool. When the clamp is used, the side contributes no extra BTC miner fee; the trade fee itself covers that side's calculated miner portion, and any excess slightly overpays the miner.

## Where Values Are Checked

The maker validates the take-offer request before reserving the offer:

- the offer exists and is available,
- the trade amount is inside the offer bounds,
- the trade date is recent,
- the peer fee rate is not below the stored minimum and is in tolerance against the local fee rate,
- `makerFee` and `takerFee` match the expected BSQ trade fees for the selected amount.

`FeeService` clamps provider-supplied fee rates into Bisq's accepted range before storing them. The stored invariant is:

```text
maxFeePerVbyte >= txFeePerVbyte >= minFeePerVbyte >= networkMin
```

This protects the `getAdjustedTxFee` zero-clamp logic from underpaying relay minimums when a BSQ trade fee alone covers a side's miner portion, and avoids overflow in fee estimators if a bad fee provider returns absurdly high rates.

During protocol execution, each side verifies the peer-supplied inputs and change against locally calculated values. Peer change is allowed to be less than the exact expected change, because dust or coin-selection excess can fall through to miner fee. Peer change must not be greater than expected, because that would let the peer reclaim value that should have paid fees.

## Main Code Paths

- `core/src/main/java/bisq/core/trade/bsq_swap/BsqSwapCalculation.java`
- `core/src/main/java/bisq/core/offer/bsq_swap/BsqSwapOfferModel.java`
- `core/src/main/java/bisq/core/offer/bsq_swap/OpenBsqSwapOffer.java`
- `core/src/main/java/bisq/core/trade/bsq_swap/BsqSwapTakeOfferRequestVerification.java`
- `core/src/main/java/bisq/core/trade/protocol/bsq_swap/tasks/buyer/BuyerCreatesBsqInputsAndChange.java`
- `core/src/main/java/bisq/core/trade/protocol/bsq_swap/tasks/seller/SellerCreatesAndSignsTx.java`
- `core/src/main/java/bisq/core/trade/protocol/bsq_swap/tasks/buyer/ProcessBsqSwapFinalizeTxRequest.java`
- `core/src/main/java/bisq/core/trade/protocol/bsq_swap/tasks/seller/ProcessTxInputsMessage.java`
