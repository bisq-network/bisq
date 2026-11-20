# Maker change in the deposit transaction

## Scope

This specification covers the change output of the maker in the deposit transaction of the Bisq v1
trade protocol, and the trade amounts that the maker accepts because of it. BSQ swaps are out of
scope.

## The maker change

The maker funds the deposit transaction with the funds it reserved for the offer. The taker requires
them to be exactly the offer amount plus the seller's security deposit for a sell offer, and the
buyer's security deposit for a buy offer. So in a sell offer the maker gets back the remainder of the
offer amount, the offer amount minus the trade amount, as a change output of the deposit
transaction. A buy offer has no maker change. Besides the 2-of-2 multisig output, the deposit
transaction has at most this one output.

A change output below the dust limit, or one with a non-standard script, makes the deposit
transaction non-standard. The network does not relay it, so it is never mined, while the taker fee
transaction is already published.

## Rules

- The maker refuses a take offer request whose trade amount leaves a remainder of the offer amount
  above zero and below the dust limit, `Restrictions.getMinNonDustOutput()` (546 satoshi). The rule
  applies to buy and sell offers alike, as the take offer screen of the desktop application does. The
  maker drops such a request without a reply, so the taker only sees the trade protocol timeout.
- The taker refuses the prepared deposit transaction of the maker unless the maker change is absent
  for a zero remainder, or is one output of exactly the remainder, at least the dust limit, with a
  P2WPKH script. The taker checks this before it publishes its taker fee transaction. The buyer as
  taker checks it again before it signs.

546 satoshi is the dust limit of a legacy output. A P2WPKH output is already standard from 294
satoshi, so the rules refuse a few amounts that the network would relay. The take offer screen and
the coin selection of the wallet use the same limit, so one limit applies everywhere.

An honest maker wallet never creates a dust change output: it adds dust change to the miner fee,
which the taker refuses as a fee mismatch. It sends the change to a fresh P2WPKH address. So the
rules refuse only trades that fail anyway, and deposit transactions of a modified maker.

## Compatibility

The take offer screen of the desktop application refuses a trade amount that leaves a dust
remainder in all versions, so a desktop taker never sends one. An API taker can, and gets no reply
from a maker of this version. Up to v1.10.11 the taker accepted a dust or non-P2WPKH maker change.
