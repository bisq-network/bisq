# BTC withdrawal amount

## Scope

This specification defines how the amount entered for a BTC withdrawal from the Bisq wallet becomes
the amount the receiver gets and the amount the sender spends, which amounts are refused, and when
the desktop checks the inputs of a withdrawal. It covers the send funds screen of the desktop and
the `sendbtc` API call. The dust rule also applies to the withdrawal of a trade payout, see
[`../trade/withdrawal-completion.md`](../trade/withdrawal-completion.md).

## Dust limit

The wallet refuses an output to the receiver below 546 satoshi on every network. This is bitcoinj's
dust limit, the figure Bitcoin Core uses for a legacy output. A segwit output has a lower one. The
wallet throws a `ReceiverAmountBelowDustException`, an `IllegalArgumentException` with the message
used before, so that a caller which does not handle it gets the same error as before.

## Fee included or excluded

On the desktop the user chooses whether the entered amount includes the mining fee:

- Fee included (the default): the receiver gets the entered amount minus the fee for the size of the
  transaction, and a remainder of the inputs is paid on top (see Remainder of the inputs). An amount
  that the fee leaves below the dust limit is refused.
- Fee excluded: the receiver gets the entered amount and the sender spends the entered amount plus
  the fee. The fee is estimated for the transaction that pays the entered amount to the receiver, so
  the entered amount can be lower than the fee. An amount below the dust limit is refused.

The API always treats the amount as including the fee. It refuses an amount below the dust limit
before it estimates the fee.

## Remainder of the inputs

A remainder of the inputs that would be a change output below the dust limit is part of the fee.
bitcoinj adds a remainder below its own change limit (537 satoshi for the segwit change address of
the wallet) to the fee itself. The desktop adds a remainder that bitcoinj kept as a change output
below 546 satoshi to the fee and notes that in the confirmation. The desktop takes the receiver's
amount and the fee from the estimated transaction, so the confirmation shows what is sent. With the
fee included, the sender then spends the entered amount plus such a remainder.

Known deviation: the API adds a remainder of the inputs below 546 satoshi to the fee without adding
it to the amount, whether bitcoinj absorbed it or kept it as a change output. The receiver then gets
the remainder less. An absorbed remainder is paid twice in the fee, and a kept change output is
still created. A receiver's amount that falls below the dust limit this way fails after the
estimation.

## Checks on the desktop

The desktop checks the inputs that do not depend on the fee before it estimates the fee, in this
order: a source address is selected, the amount is at least the dust limit, and the destination
address is valid. Each failed check is reported with its own message, as the estimation would fail
on these inputs with an error that does not tell the user what to fix. After the estimation it
checks that the selected addresses hold the amount the sender spends. An amount that the fee leaves
below the dust limit is reported as lower than the fee and the dust limit.

## Not covered

- The withdraw button is not disabled for invalid inputs. The checks run when it is pressed.
- With the fee excluded, if the selected inputs exceed the amount plus the fee by less than
  bitcoinj's change limit, the estimation does not settle on a fee: the fee without a change output
  leaves a change output, and the fee of a transaction with a change output leaves the remainder in
  the fee. After ten rounds the estimation uses the transaction of its last round, which has no
  change output, so the user pays the remainder plus the fee of a change output more than needed.
  The confirmation shows that fee.
