# Payment method of revealed payment account data

## Scope

This specification defines which payment method the payment account data must have that a trader
reveals to the trade peer in the Bisq v1 trade protocol. It applies to the account data of the BTC
seller, which the BTC buyer receives with the deposit transaction, and to the account data of the
BTC buyer, which the BTC seller receives in a message of its own. It applies to fiat and to
cryptocurrency trades.

BSQ swaps have no payment account data and are out of scope.

## Terms

- **Commitment**: the hash of the payment account data which a trader puts into the trade contract.
  The data itself is revealed later.
- **Payment method of a trader in the contract**: the payment method which a trader states for its
  account when the contract is made. The contract holds one for the maker and one for the taker.

## Rules

1. Revealed payment account data is accepted only if
   - it hashes to the commitment of the peer in the contract, and
   - its payment method is the payment method of the peer in the contract.
2. The payment method to compare with is the one of the role of the peer, maker or taker. It is
   not always the payment method of the offer: a taker of a SEPA offer can use a SEPA Instant
   account.
3. A contract of an old version which holds no payment methods of the traders takes the payment
   method of the offer for both.
4. If the payment method is missing on either side, the check fails.
5. Newly revealed data which fails the check is not stored for the trade and not shown, and the
   protocol step fails as for a wrong hash.
6. The BTC seller applies the check each time it validates the account of the BTC buyer, also for
   account data which was stored before a restart (see
   [`fiat-buyer-payment-account-validation.md`](fiat-buyer-payment-account-validation.md)). Stored
   data which fails the check stays stored, but the account is not validated, so the trade does
   not pass the gates which need the validation.

## Rationale

The commitment binds the content of the account data, but the trader has chosen that content. When
the contract is made, only the two stated payment methods are compared with each other. Without
rule 1 a trader can state the payment method of the offer and commit to account data of another
payment method. Then

- the BTC seller checks the account age and the signing of an account of another payment method
  against the trade limit of the payment method of the offer, and
- the BTC buyer is shown account data of another payment method than the one of the trade. Where
  payment methods share the format of their account data, for example an address which is valid on
  two networks, nothing else tells the BTC buyer which one was agreed.

## Compatibility

No message and no field of the contract changes. A client without this change states the payment
method of the account data it commits to, so its data passes the check.

## Known gaps

- The check compares the payment method which the data names. It does not check that the kind of
  the data is the one of that payment method, so data of another kind which names the payment
  method of the contract passes the check.
- A validation which a version without this check has stored as successful is not repeated. A
  trade which was open during the update keeps that result.
- A contract which is received with a dispute can hold revealed account data. A trader checks that
  data against the commitment only. A dispute agent does not check it.
- The payment method which a trader states is compared with the one of the peer when the contract
  is made, not with the one of the offer. A maker of a SEPA Instant offer can state SEPA.
