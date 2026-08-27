# Altcoin volume precision

## Scope

This specification defines how the altcoin volume of a trade is derived from the signed trade
amount and price, and when a derived volume must be rejected. It applies to offers, take-offer
models, contracts and trades of altcoin markets. Fiat volume rounding
(whole currency units, HalCash multiples) is unchanged and out of scope.

## Rule

The altcoin volume is the trade amount multiplied by the price, rounded half up to the number of
decimals the asset supports on its own chain, at most 8. Volumes are stored at 10^-8 precision,
so one on-chain unit corresponds to 10^(8 - precision) internal units.

The asset precision is metadata declared by the asset. An asset without a declared precision uses
the default of 8 decimals, which makes the rounding an exact no-op. The declared precision must
not exceed the real number of decimals the chain supports, otherwise the stated volume can be
impossible to transfer.

The rounding must use exact integer arithmetic. The rounded value must be bit-identical for both
peers, and precision 8 must reproduce the input exactly; floating point drifts above 2^53.

## Zero volume

A volume below half of one on-chain unit rounds to zero. It must not be inflated to one unit,
because that would silently change the agreed exchange rate.

A zero volume describes a trade with no payment obligation and must be rejected:

- at offer placement, for both the volume and the minimum volume;
- at take time, for the volume derived from the selected trade amount;
- on the maker, for the trade amount received in the take request.

Rounding is monotonic in the amount, so an offer whose minimum volume is positive at a given
price yields a positive volume for every allowed trade amount at that price. Placement validates
a market-priced offer at the market price of that moment; a price that has risen far enough since
placement can push the derived volume of such an offer to zero. Editing an open offer republishes
it without re-running placement validation. The take-time and maker checks therefore cover stale
market-priced offers, edited offers and offers that bypassed placement validation, such as
crafted offers or offers created before this rule.

## Derivation, not negotiation

The volume is not part of the signed contract; the contract carries the trade amount and the price,
and each peer derives the volume locally. Clients without this rule derive a volume that differs by
at most half of one on-chain unit; one on-chain unit is by construction the smallest amount the
asset can transfer. This bounded divergence is accepted and does not require a trade protocol
version change.

Trade statistics and the market data JSON exports deliberately keep the unrounded volume
representation they had before this rule.

## Indivisible assets

An asset whose whole on-chain unit is not a negligible value (Siafund, Askcoin) must not simply
declare precision 0: half of one unit is then a whole coin, and half-up rounding would move real
value. Such assets keep the default precision until they get their own treatment, for example
rejecting them or adjusting the BTC amount so the volume lands on a whole unit.

BSQ is a further deliberate exception. Its on-chain unit is 100 satoshi, i.e. 2 decimals, but BSQ
trading uses the dedicated BSQ swap protocol, which is out of scope here, and BSQ amounts carry
their own 2-decimal handling throughout the application. BSQ keeps the default precision so the
rounding stays an exact no-op for it; declaring precision 2 would need its own analysis of the
swap and DAO interactions.
