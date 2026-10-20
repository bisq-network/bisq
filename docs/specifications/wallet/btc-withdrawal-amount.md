# BTC withdrawal amount

## Scope

This specification defines how the amount entered for a BTC withdrawal from the Bisq wallet becomes
the amount the receiver gets and the amount the sender spends, which amounts are refused, and when
the desktop checks the inputs of a withdrawal. It covers the send funds screen of the desktop and
the `sendbtc` API call. The dust rule also applies to the withdrawal of a trade payout with the
`withdrawfunds` API call. [`../trade/withdrawal-completion.md`](../trade/withdrawal-completion.md)
defines when that withdrawal completes the trade.

## Dust limit

The wallet refuses an output to the receiver below 546 satoshi on every network. This is bitcoinj's
constant MIN_NONDUST_OUTPUT, the figure Bitcoin Core uses for a legacy output. A segwit output has a
lower one. The refusal can be told apart from other errors, so that the desktop reports it as an
amount that is too low. A caller that does not tell it apart, such as the API, gets the same error
and message as before.

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
Such a remainder is left only when the selected addresses hold no further spendable coin, as the
coin selection adds another input rather than leave a change below 546 satoshi. bitcoinj adds a
remainder below its own change limit (537 satoshi for the segwit change address of the wallet) to
the fee itself. A remainder that bitcoinj would keep as a change output below 546 satoshi is not
sent either: with the fee included, the fee is estimated for the transaction without that output,
and the remainder is paid on top; with the fee excluded, the wallet adds the output to the fee. The
desktop and the API take the receiver's amount and the fee from the estimated transaction, so the
desktop's confirmation shows what is sent. The confirmation notes the remainder in the fee. With the
fee included, the sender then spends the entered amount plus such a remainder.

With the fee excluded, the selected inputs can exceed the amount plus the fee of a transaction with
a change output by less than bitcoinj's change limit. The fee of a transaction without a change
output would then leave a change output that this fee does not pay for. The withdrawal is a
transaction without a change output, and its fee is the rest of the inputs, more than the chosen
rate: the fee of a transaction with a change output and the remainder below the change limit, which
the confirmation notes. A transaction without a change output whose fee pays at least the chosen
rate ends the estimation.

## Checks on the desktop

The desktop checks the inputs that do not depend on the fee before it estimates the fee, in this
order: a source address is selected, the amount is at least the dust limit, and the destination
address is valid. Each failed check is reported with its own message, as the estimation would fail
on these inputs with an error that does not tell the user what to fix. After the estimation it
checks that the selected addresses hold the amount the sender spends. An amount that the fee leaves
below the dust limit is reported as lower than the fee and the dust limit.

## Not covered

- The withdraw button is not disabled for invalid inputs. The checks run when it is pressed.
- With the fee excluded, inputs that pay the amount and the fee only without a change output are
  refused as insufficient funds. The estimation sizes the fee for a transaction with a change output
  first. The same inputs can be withdrawn with the fee included. Estimating again with the fee of a
  transaction without a change output after the refusal would remove this.
- With the fee excluded and a change output that bitcoinj kept below 546 satoshi, the confirmation
  shows the size and the fee rate of the estimated transaction, which has that output. The sent
  transaction has none, so it is smaller and pays a higher rate.
- The send selects the coins again. If the wallet changes while the confirmation is open, for
  example when a new coin arrives, the sent transaction can differ from the confirmation. Sending
  the transaction of the confirmation, signed after the password prompt, would remove this.
