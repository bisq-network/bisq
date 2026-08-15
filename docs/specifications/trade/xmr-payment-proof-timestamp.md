# XMR payment proof timestamp

## Scope

This specification defines the timestamp rule for the automatic confirmation of an XMR payment in a
BTC/XMR trade. The XMR seller queries an explorer service with the transaction hash and the
transaction key supplied by the buyer, and the service returns the data of that Monero transaction,
among it the time at which the transaction was confirmed in a block.

The general freshness rules of
[`network/peer-timestamp-validation.md`](../network/peer-timestamp-validation.md) do not apply here.
The transaction timestamp is a historical value, not evidence of the current time.

## Security invariant

The transaction used as payment proof must have been created for this trade. A transaction which
already existed when the trade started must be rejected. Otherwise a buyer could present an unrelated
earlier payment, or a payment made for a different trade, and obtain the automatic confirmation
without paying.

## Rule

The transaction timestamp must not be earlier than the start date of the trade, with a tolerance of
two hours for clock differences between the buyer, the seller and the explorer service.

The start date of the trade is the date at which the offer was taken.

There is no upper bound. The buyer may pay at any time within the trade period, which lasts up to one
day and is therefore considerably longer than the tolerance. The proof is requested only after the
buyer has started the payment, so a transaction confirmed several hours after the trade started is
the normal case and must remain valid until the trade ends. Rejecting a later transaction would break
the automatic confirmation for ordinary trades and would report a timestamp mismatch as the reason.

The comparison must be overflow safe. The timestamp comes from an external service, which is not
trusted, and can carry any signed 64-bit value. It must be compared with the lower bound derived from
the local trade date. A difference based check (`tradeDate - timestamp`) overflows for extreme values
and would then accept a transaction of any age.

The rule is not applied in development mode, where trades are created with regtest data.

## Relation to the other proof checks

The timestamp check is one of several conditions of the payment proof. The proof is accepted only if
the receiving address, the transaction hash, the transaction key, the amount of a matching output and
the required number of confirmations match the trade as well. The timestamp rule alone does not bind
a transaction to a trade; it removes the reuse of transactions which predate the trade.
