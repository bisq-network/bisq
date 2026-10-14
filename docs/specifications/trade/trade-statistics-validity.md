# Trade statistics validity

## Scope

This specification defines which trade statistics a BISQ 1 node uses, how average prices computed
from them handle extreme values, and which market prices can deactivate an open offer through its
trigger price.

## Rules

1. A node uses a trade statistic only if it is valid. All parts of the application which use the
   values of trade statistics apply this rule, mostly by reading them through
   `TradeStatisticsManager`, which keeps only valid statistics.
2. A trade statistic is valid if all of the following hold:
   - the amount, the price and the date are greater than zero, and the currency and the payment
     method are not empty;
   - for a statistic dated after 2021-11-01, the amount is at most 2 BTC and the currency is in the
     currency list;
   - the date is at most one day after the node's clock;
   - the trade volume can be computed from the price and the amount, and it is greater than zero.
3. A node checks the validity against its clock when it receives a statistic. Stored statistics are
   checked again at every startup.
4. The trigger price of an open offer deactivates the offer only with a recent price from the price
   feed, which is at most 30 minutes old (`MarketPrice.isRecentExternalPriceAvailable`). A price
   which a node derives from trade statistics never deactivates an offer.
5. An average price computed from valid statistics, in the market charts and for the average BSQ
   price, does not fail for any values. An average which cannot be represented as a long is 0, which
   stands for no average, as when there are no trades.

## Trust model

A trade statistic is an unauthenticated claim: no signature binds it to a trade or to a trader, and
any peer can publish one. Validity (rule 2) removes only values which no trade can have. A valid
statistic can still state a price, an amount or a date which no trade had. Values which a node
derives from trade statistics, such as the newest price of a currency, average prices and volumes,
are therefore informational. They must not change an offer, a trade, a fee or the DAO state.

## Reasons

- Rule 2, date: a statistic carries the date of its trade, which lies in the past when the
  statistic is published. A statistic dated in the future would be the newest one of its currency
  until that date, also after a restart. One day covers differences between the clocks of nodes, as
  for account age witnesses and signed witnesses.
- Rule 2, volume: the charts, the trades table and the average prices compute the volume from the
  price and the amount, and some of them divide by it. For values which no trade can have, this
  computation fails or gives zero.
- Rule 4: a price derived from trade statistics is for display only. The state of an offer depends
  only on the price feed.
- Rule 5: the validity rules bound single values (rule 2), not every combination of them. A view
  must not fail because of the values of a statistic.

## Compatibility

No message, no persisted data and no hash change. Trade statistics are stored and relayed as
before. All statistics in the bundled stores pass the date and volume checks. A node whose clock is
more than one day behind does not use new statistics until its clock is corrected and it is
restarted. While no recent price from the price feed is available, for example right after startup
or during an outage of the price feed, no offer is deactivated by its trigger price.

## Known gaps

While a node holds no price from the price feed for a currency, the price alert and the market alert
of the mobile notifications compare against the newest price from trade statistics, which any peer
can publish. A price alert which fires is removed.

The trade volume of an altcoin in trade statistics over its look-back period decides whether the
node shows the altcoin as actively traded or as delisted (DAO parameter `ASSET_MIN_VOLUME`). Only
altcoins which are not delisted can be chosen for a new payment account and in the preferences.
Valid but invented statistics can therefore keep a delisted altcoin listed on every node which
receives them. They cannot delist an altcoin, because statistics are never removed.

## Not covered

Rules for storing and relaying trade statistics.
