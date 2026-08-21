# Trade Limits

This document explains how Bisq currently decides the maximum BTC amount for a
Bisq v1 trade. The rules combine payment-method limits, the local user-defined
limit, account signing, and the maker/taker role.

The short version is:

1. Every payment method has a maximum BTC amount.
2. Your local `userDefinedTradeLimit` can lower that amount.
3. Account signing can lower the amount only for a trader who is buying BTC with
   a chargeback-risk fiat payment method in a mature-market currency.
4. Selling BTC is never reduced by account signing.
5. All trades are capped by the current hard network cap of `0.250 BTC`.

## Scope

This specification covers the current Bisq v1 trade-limit behavior: payment
method limits, local user limits, account-signing reductions, offer creation,
offer taking, and peer verification.

BSQ swaps use `PaymentMethod.BSQ_SWAP` for their payment-method maximum, but
they do not use the local user-defined limit or fiat account-signing rules.

## Direction And Role

Bisq offer direction is written from the maker's point of view.

| Offer direction | Maker is | Taker is | Maker limit direction | Taker limit direction | Account-signing reduction can affect |
| --- | --- | --- | --- | --- | --- |
| `BUY` | Buying BTC | Selling BTC | `BUY` | `SELL` | Maker only |
| `SELL` | Selling BTC | Buying BTC | `SELL` | `BUY` | Taker only |

This is the most important role rule: account signing is a buyer-side limit.
If the trader is selling BTC, signing state does not reduce the trade limit.

## Current Constants

| Name | Current value | Meaning |
| --- | ---: | --- |
| DAO `MAX_TRADE_LIMIT` default | `2 BTC` | DAO parameter used as the base for payment-method limits. |
| Hard network cap | `0.250 BTC` | `TradeLimits.MAX_TRADE_AMOUNT`; clamps the DAO value and rejects larger trades. |
| Default local user-defined limit | `0.1 BTC` | Initial `Preferences.INITIAL_TRADE_LIMIT`. |
| Current small-trade limit | `0.002 BTC` | Limit for unsigned or newly peer-signed chargeback-risk BTC buyers. |
| Small-trade activation timestamp | `2025-02-17 00:00 UTC` | Before this timestamp the small-trade limit was `0.01 BTC`. |
| Legacy account exception timestamp | `2019-03-01 00:00 UTC` | Account witnesses at or before this timestamp bypass signing limits. |

Unless the DAO lowers `MAX_TRADE_LIMIT` below `0.250 BTC`, the effective network
maximum used by payment methods is `0.250 BTC`.

```text
networkMax = min(DAO_MAX_TRADE_LIMIT, 0.250 BTC)
```

## Calculation Flow

For the local trader, the trade limit is calculated in this order:

1. Calculate the payment-method maximum from `networkMax`.
2. Apply the local user preference.
3. Decide whether account-signing restrictions apply.
4. If signing restrictions apply, apply the signing-age schedule.

In formulas:

```text
PM = paymentMethodMax
U  = userDefinedTradeLimit
L  = min(PM, U)
S  = smallTradeLimit = 0.002 BTC
```

`L` is the normal local limit. Account signing can reduce `L` only in the
chargeback-risk buyer cases described below.

Peer verification is slightly different. A node can verify a peer's payment
method, currency, direction, witness, and signing age, but it cannot know the
peer's local `userDefinedTradeLimit`. For peer checks, replace `L` with `PM`.

## Complete Decision Table

Use this table after computing `PM`, `U`, `L`, and `S`.

| Currency | Payment method and currency | Trader role | Account state | Local limit |
| --- | --- | --- | --- | ---: |
| Crypto | Any | Buyer or seller | Any | `L` |
| Fiat, not mature-market | Any | Buyer or seller | Any | `L` |
| Fiat mature-market | Not chargeback-risk | Buyer or seller | Any | `L` |
| Fiat mature-market | Chargeback-risk | Seller | Any | `L` |
| Fiat mature-market | Chargeback-risk | Buyer | Signed by arbitrator | `L` |
| Fiat mature-market | Chargeback-risk | Buyer | Legacy witness at or before `2019-03-01 00:00 UTC` | `L` |
| Fiat mature-market | Chargeback-risk | Buyer | Unsigned, no valid signed witness, or peer signed less than 30 days ago | `S` |
| Fiat mature-market | Chargeback-risk | Buyer | Peer signed at least 30 days and less than 60 days ago | `0.5 * L` |
| Fiat mature-market | Chargeback-risk | Buyer | Peer signed at least 60 days ago | `L` |

For peer verification, the equivalent limits are `PM`, `S`, `0.5 * PM`, and
`PM`, because the verifier cannot apply the peer's local user preference.

## Payment-Method Maximum

Payment methods use either a risk category or a custom static limit.

### Risk Categories

Risk-category methods are derived from `networkMax` with a risk factor. The code
rounds the first-month value to 10,000 satoshis and then multiplies it by four.
With the current `networkMax = 0.250 BTC`, the resulting category maxima are:

| Category | Risk factor | Current max |
| --- | ---: | ---: |
| Very low risk | 1 | `0.250 BTC` |
| Low risk | 2 | `0.1252 BTC` |
| Mid risk | 3 | `0.0832 BTC` |
| High risk | 4 | `0.0624 BTC` |

The payment methods in each category are listed below. The category name maps to
the current max in the table above.

Very low risk:

`ADVANCED_CASH`, `BLOCK_CHAINS`, `BLOCK_CHAINS_INSTANT`, `BSQ_SWAP`

Low risk:

`SWISH`, `HAL_CASH`, `F2F`, `PERFECT_MONEY`, `JAPAN_BANK`,
`AUSTRALIA_PAYID`, `ALI_PAY`, `WECHAT_PAY`, `PROMPT_PAY`

Mid risk:

`MONEY_GRAM`, `WESTERN_UNION`, `SWIFT`

High risk:

`SEPA`, `SEPA_INSTANT`, `MONEY_BEAM`, `FASTER_PAYMENTS`,
`CLEAR_X_CHANGE`, `POPMONEY`, `US_POSTAL_MONEY_ORDER`,
`INTERAC_E_TRANSFER`, `CASH_DEPOSIT`, `CASH_BY_MAIL`, `NATIONAL_BANK`,
`SAME_BANK`, `SPECIFIC_BANKS`, `AMAZON_GIFT_CARD`, `UPHOLD`, `REVOLUT`,
`TRANSFERWISE`, `TRANSFERWISE_USD`, `PAYSERA`, `PAXUM`, `RTGS`, `IMPS`,
`NEQUI`, `PIX`, `CAPITUAL`, `CELPAY`, `MONESE`, `SATISPAY`, `VERSE`,
`STRIKE`, `ACH_TRANSFER`, `DOMESTIC_WIRE_TRANSFER`, `MERCADO_PAGO`, `SBP`

### Custom Static Limits

These methods do not use the category table. Their static limit is scaled by the
current network maximum relative to the original `2 BTC` DAO base.

```text
customMax = staticBaseLimit * networkMax / 2 BTC
```

With the current `networkMax = 0.250 BTC`, the custom limits are:

| Payment method | Static base limit | Current max |
| --- | ---: | ---: |
| `NEFT` | `0.02 BTC` | `0.0025 BTC` |
| `BIZUM` | `0.04 BTC` | `0.005 BTC` |
| `UPI` | `0.05 BTC` | `0.00625 BTC` |
| `PAYTM` | `0.05 BTC` | `0.00625 BTC` |
| `TIKKIE` | `0.05 BTC` | `0.00625 BTC` |

### Effect Of The Local User Limit

The default local user-defined limit is `0.1 BTC`. With the current hard cap,
this lowers very-low-risk and low-risk methods:

| Payment-method max | Default user limit | Local base limit |
| ---: | ---: | ---: |
| `0.250 BTC` | `0.1 BTC` | `0.1 BTC` |
| `0.1252 BTC` | `0.1 BTC` | `0.1 BTC` |
| `0.0832 BTC` | `0.1 BTC` | `0.0832 BTC` |
| `0.0624 BTC` | `0.1 BTC` | `0.0624 BTC` |

A user can raise the local preference up to the clamped DAO maximum. That can
restore very-low-risk methods from `0.1 BTC` to `0.250 BTC` and
low-risk methods from `0.1 BTC` to `0.1252 BTC`, but it cannot raise a
method above its payment-method maximum.

## Account-Signing Reduction

Account signing is considered only when both the currency and payment method are
in the chargeback-risk set.

Mature-market currencies are:

`AUD`, `BRL`, `CAD`, `EUR`, `GBP`, `USD`

Chargeback-risk payment method IDs are:

`SEPA`, `SEPA_INSTANT`, `INTERAC_E_TRANSFER`, `CLEAR_X_CHANGE`,
`REVOLUT`, `NATIONAL_BANK`, `SAME_BANK`, `SPECIFIC_BANKS`,
`CHASE_QUICK_PAY`, `POPMONEY`, `MONEY_BEAM`, `UPHOLD`

`CHASE_QUICK_PAY` is deprecated and not in the active payment-method list, but
it remains in this check for old data.

A payment method can be high risk without being in the account-signing
chargeback-risk set. For example, `FASTER_PAYMENTS` and `CASH_DEPOSIT` have the
high-risk payment-method maximum, but account signing does not reduce their
limits.

The signing schedule uses time since the account was signed, not time since the
payment account was created.

| BTC buyer account state | Local limit | Peer-verification limit |
| --- | ---: | ---: |
| Unsigned or no valid signed witness | `0.002 BTC` | `0.002 BTC` |
| Peer signed less than 30 days ago | `0.002 BTC` | `0.002 BTC` |
| Peer signed at least 30 days and less than 60 days ago | `0.5 * L` | `0.5 * PM` |
| Peer signed at least 60 days ago | `L` | `PM` |
| Signed by arbitrator | `L` | `PM` |
| Legacy witness at or before `2019-03-01 00:00 UTC` | `L` | `PM` |

At 30 days, a valid peer-signed account reaches the half-limit stage and can be
treated as a signer by the signing logic. The full trade limit is reached at 60
days.

The UI sign-state enum has more labels than the numeric limit schedule needs:
`UNSIGNED`, `ARBITRATOR`, `PEER_INITIAL`, `PEER_LIMIT_LIFTED`, `PEER_SIGNER`,
and `BANNED`. The current `getSignState` method returns `PEER_INITIAL` for a
peer signature younger than 30 days and `PEER_SIGNER` after 30 days. `BANNED` is
a filter/display state, not a separate numeric branch in `getTradeLimit`.

## Offer Creation

When creating a Bisq v1 offer, `CreateOfferService` stores `maxTradeLimit` in
the offer payload. The value comes from `OfferUtil.getMaxTradeLimit`, which
delegates to `AccountAgeWitnessService.getMyTradeLimit`.

The desktop create-offer flow validates and adjusts the requested amount against
the local trade limit before building the offer. The Core API path passes the
requested amount into `CreateOfferService`; the resulting payload still records
the local `maxTradeLimit`, but the placement validation task checks the offer
amount only against the payment-method maximum. That validation is broader than
the local limit check: it does not apply the user-defined cap or the
account-signing schedule again.

## Offer Taking

When taking an offer, the taker uses the mirrored offer direction:

```text
takerLimit = getMyTradeLimit(paymentAccount, offerCurrency, offer.getMirroredDirection())
```

The selected amount is initialized as:

```text
selectedAmount = min(intendedTradeAmount, takerLimit)
```

The offer book also filters or warns when the local user's best matching payment
account has a limit below the offer minimum.

## Peer Verification

During trade protocol verification, a node validates the peer's account witness
and recomputes the peer's trade limit.

| Local relationship | Peer direction used for verification |
| --- | --- |
| I am maker verifying a taker against my offer | `offer.getMirroredDirection()` |
| I am taker verifying the maker's offer | `offer.getDirection()` |

Offer-book filtering also uses `verifyPeersTradeAmount` to estimate whether the
maker's account can support the offer amount. In that filter path, a missing
peer witness permits only trades at or below the small-trade limit. If a witness
exists, the verifier uses the payment-method maximum and the signing schedule.
Peer checks do not apply the peer's local user-defined limit because that
setting is not part of the witness or offer contract.

Trade amount validation also rejects any trade above `0.250 BTC`, including
legacy offers that were persisted before the hard cap was introduced.

## Examples

These examples assume the current `networkMax = 0.250 BTC` and the default local
user-defined limit `U = 0.1 BTC`.

| Scenario | Why | Limit |
| --- | --- | ---: |
| Maker creates a `BUY` offer with `SEPA`/`EUR`, unsigned account | Maker is buying BTC with a chargeback-risk mature-market method. | `0.002 BTC` |
| Maker creates a `BUY` offer with `SEPA`/`EUR`, peer signed 45 days ago | Half-limit stage: `0.5 * min(0.0624, 0.1)`. | `0.0312 BTC` |
| Maker creates a `BUY` offer with `SEPA`/`EUR`, peer signed 60+ days ago | Full local base limit: `min(0.0624, 0.1)`. | `0.0624 BTC` |
| Maker creates a `SELL` offer with `SEPA`/`EUR`, unsigned account | Maker is selling BTC, so signing does not reduce the limit. | `0.0624 BTC` |
| Taker takes a `SELL` offer with `SEPA`/`EUR`, unsigned account | Taker is buying BTC, so the small-trade limit applies. | `0.002 BTC` |
| Taker takes a `BUY` offer with `SEPA`/`EUR`, unsigned account | Taker is selling BTC, so signing does not reduce the limit. | `0.0624 BTC` |
| Any trader uses `SWISH` | Low-risk payment-method max, then lowered by the default user limit. | `0.1 BTC` |
| Any trader uses `ADVANCED_CASH` with default preference | Very-low-risk method lowered by default user limit. | `0.1 BTC` |
| Any trader uses `ADVANCED_CASH` after raising preference to `0.250 BTC` | Very-low-risk method reaches the hard cap. | `0.250 BTC` |
| Any trader uses `UPI` | Custom scaled payment-method limit. | `0.00625 BTC` |

## Source References

- `TradeLimits.MAX_TRADE_AMOUNT` defines the `0.250 BTC` hard cap.
- `TradeLimits.getMaxTradeLimitFromDaoParam` reads and clamps DAO
  `MAX_TRADE_LIMIT`.
- `PaymentMethod.getMaxTradeLimitAsCoin` derives risk-category and custom
  payment-method maxima.
- `PaymentMethod.hasChargebackRisk` defines the account-signing chargeback-risk
  method set.
- `CurrencyUtil.getMatureMarketCurrencies` defines the mature-market currency
  set.
- `AccountAgeWitnessService.getMyTradeLimit` applies the local user-defined
  preference and account-signing schedule.
- `AccountAgeWitnessService.verifyPeersTradeLimit` recomputes peer limits during
  trade protocol verification.
- `AccountAgeWitnessService.verifyPeersTradeAmount` is used by offer-book
  filtering for the maker-side trade-limit estimate.
- `OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT` defines the small-trade
  fallback amount.
