# BTC withdrawal amount

## Scope

This specification defines how a withdrawal of BTC from the Bisq wallet to an external address turns
the entered amount into the amount the receiver gets, the fee and the amount the sender spends, which
amounts are refused, and which inputs the desktop checks before it estimates the fee. It covers the
send funds screen of the desktop and the `sendbtc` API call. The withdrawal of a trade payout with
`withdrawfunds` has its own fee estimation and is not covered, except for the dust limit of the
receiver's output; [`../trade/withdrawal-completion.md`](../trade/withdrawal-completion.md) defines
when that withdrawal completes the trade.

## Terms

- The **source addresses** are the addresses that the user selects on the desktop, and all addresses
  with an available balance for the API. Their spendable coins are the **selected coins**.
- Bisq applies a **dust limit** of 546 sat (bitcoinj's `MIN_NONDUST_OUTPUT`) to all withdrawal
  outputs on every network.
- The **fee for the size** is the chosen fee rate times the virtual size of the transaction that is
  sent. The fee of a withdrawal is never below it. Besides a remainder and the fee of a change output
  that is not created (rules 5 and 8), the fee can exceed it by the fee of a few bytes, as a signature
  can be a byte longer or shorter in each round of the fee estimation.
- The **remainder** is a change below the dust limit, which the withdrawal does not return as a change
  output.

## Rules

### Fee rate and dust limit

1. The fee rate must be positive. The API requires at least the minimum rate reported by the fee
   service, including when it uses a saved preference. The custom fee field of the desktop checks
   its own bounds.
2. No output of a withdrawal is below the dust limit, neither the receiver's output nor a change
   output.
3. A receiver's output below the dust limit is refused with an error that callers can tell apart from
   other errors. It keeps the type and the message of the former check (an `IllegalArgumentException`
   with "The amount is too low (dust limit)."), so the API reports the same error as before.
   When the estimated fee would put the receiver below the limit, the wallet checks whether a signed
   transaction paying exactly 546 sat covers the chosen fee rate.

### Fee included

The entered amount is what the sender spends, the fee included. This is the default on the desktop
and the only mode of the API.

4. If the coins selected for the amount exceed it by at least the dust limit, a change output returns
   the excess, and the receiver gets the amount minus the fee for the size.
5. Otherwise the transaction has no change output, the excess is the remainder (it can be zero), and
   the sender spends the amount plus the remainder. The remainder pays the fee first: the fee is the
   larger of the fee for the size and the remainder, and the receiver gets the amount minus the part
   of the fee for the size that the remainder does not pay. The receiver never gets more than the
   amount.

Rule 5 exists because the remainder has to be spent: a change output below the dust limit is not
allowed. Using it for the fee first keeps the fee at the minimum that the size and the remainder
allow, and the receiver's amount changes continuously with the fee rate. Paying the remainder to the
miners on top of the full fee for the size would charge up to 545 sat more than needed, and the
receiver's amount would jump at the rate where the remainder alone pays the fee. Bitcoin Core, when it
subtracts the fee from the amount, gives a dropped change to the recipient even beyond the requested
amount. Bisq keeps the entered amount as the upper bound, as the user entered what to spend.

### Fee excluded

The entered amount is what the receiver gets, and the sender spends it plus the fee. Only the desktop
offers this mode.

6. The receiver's amount is the entered amount. It can be lower than the fee.
7. The coin selection adds coins until they pay the amount and the fee for the size of a transaction
   with a change output. If they leave a change of at least the dust limit, a change output returns
   it. Before dropping change or adding an input because the previous signature would leave too
   little change, the wallet checks whether the same inputs can return exactly 546 sat at the chosen rate.
8. Otherwise the transaction has no change output. It spends all selected coins, or coins that match
   the amount and that fee exactly, and the fee is the rest of the spent coins after the receiver's
   amount. The withdrawal is refused if that fee is below the fee for the size. If a change output
   would have got less than the dust limit, that change is the remainder, at most the part of the fee
   above the fee for the size; the fee then also contains the fee for the size of the change output
   that is not created.

### Confirmation and send

9. The desktop's confirmation shows the amounts of the transaction that is sent: the sender's amount,
   the receiver's amount, the fee, the size and the fee rate. A positive remainder is noted as dust
   already included in the displayed mining fee.
10. The send selects the coins again from the source addresses of the estimate, with its receiver's
    amount and its fee, to the receiver's address of the estimate. As long as the wallet does not
    change in between, it creates the estimated transaction. If the spendable coins of the source
    addresses or their selection order change between estimation rounds, the wallet refuses the
    estimate. A fee calculated for several inputs could otherwise be charged for a single input.

### Checks on the desktop

11. Before it estimates the fee, the desktop checks, in this order, that a source address is
    selected, that the entered amount is at least the dust limit and that the destination address is
    valid. It reports a failed check with its own message, as a popup like the other checks of the
    screen. Without these checks the fee estimation fails with an error that does not tell the user
    what to fix.
12. An amount that the fee leaves below the dust limit (rule 3) is reported as lower than the
    transaction fee and the dust limit. After the estimation the desktop checks that the source
    addresses hold the sender's amount.

## Not covered

- The withdraw button is not disabled for invalid inputs; the checks run when it is pressed.
- If the wallet changes while the confirmation is open, the send can select other coins than the
  estimate (rule 10): when a coin arrives or is spent, or when a new block changes the order in which
  the coin selection takes the coins. The receiver's amount stays, but the inputs, the change and the
  fee can differ. Sending the confirmed transaction would remove this, but it moves the point at which
  the send commits the transaction.
- With the fee excluded, every round of the fee estimation signs its transaction, and a round that
  adds coins can need another one. Source addresses with hundreds of coins that each add little more
  than the fee for their input need many rounds, and the desktop waits for them. Sizing the rounds
  without signatures and signing only the last one would remove this.
- With the fee excluded, rule 8 spends all selected coins, also coins whose input costs more fee than
  they add. Fewer coins without a change output can pay the same withdrawal with a lower fee, and
  can even make a refused withdrawal possible. A coin selection that leaves out such coins would
  remove this.
- The API has no fee-excluded mode.
