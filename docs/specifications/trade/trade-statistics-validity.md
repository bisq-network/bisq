# Trade statistics validity

## Scope

This specification defines which trade statistics a BISQ 1 node uses, how sums and average prices
computed from them handle extreme values, and which market prices can deactivate an open offer
through its trigger price.

## Rules

1. A node uses a trade statistic only if it is valid. All parts of the application which use the
   values of trade statistics apply this rule, mostly by reading them through
   `TradeStatisticsManager`, which keeps only valid statistics.
2. A trade statistic is valid if all of the following hold:
   - the amount, the price and the date are greater than zero, and the currency and the payment
     method are not empty;
   - the price value of the statistic is at most 10^18 (fiat prices have 4 decimals, altcoin prices
     8);
   - for a statistic dated after 2021-11-01, the amount is at most 2 BTC and the currency is in the
     currency list;
   - the date is at most one day after the node's clock;
   - the trade volume can be computed from the price and the amount, and it is greater than zero.
3. A node checks the validity against its clock when it receives a statistic. Stored statistics are
   checked again at every startup.
4. The trigger price of an open offer deactivates the offer only with a recent price from the price
   feed, which is at most 30 minutes old (`MarketPrice.isRecentExternalPriceAvailable`). A price
   which a node derives from trade statistics never deactivates an offer.
5. An average price computed from valid statistics, in the market charts, for the average BSQ price
   and for the market price of BSQ, does not fail for any values. An average which cannot be
   represented as a long is 0, which stands for no average, as when there are no trades. A value
   converted with the average BSQ price, such as a trade fee in BSQ shown in the user's currency, is
   not shown if it cannot be represented as a long.
6. A sum over valid statistics, such as the traded amount or volume of a period, never wraps
   around. An average is computed from sums as floating point numbers. A total which is
   shown or compared, such as the volume in the charts or the traded amount of an altcoin, is the
   largest long if the sum exceeds the long range.

## Storage and relay

The serialized trade statistic may occupy at most 256 bytes, including optional metadata. A node
checks this limit before hashing an incoming record and when reading a store. Each store holds at
most 100,000 distinct records. The writable store and each versioned archive have separate limits.
The largest record and archive shipped through v1.10.12 contain 76 bytes and 75,912 records
respectively.

A new statistic must pass the validity rules before admission, for both broadcasts and initial
sync, except the rule that its currency is in the currency list. A node stores and relays a
statistic of a currency which it does not know, and does not use it (rule 1). Only a successful
insertion triggers persistence, listener notification or relay. At capacity the node keeps the
records it has and declines further additions. This also means fabricated records can occupy the
available slots and exclude later genuine trades. When an updated release bundles records from the
writable store, their removal from that store frees space. On loading an older file above the limit
the node keeps the first 100,000 distinct decodable records in file order.

## Trust model

A trade statistic is an unauthenticated claim: no signature binds it to a trade or to a trader, and
any peer can publish one. Validity (rule 2) removes only values which no trade can have. A valid
statistic can still state a price, an amount or a date which no trade had. Values which a node
derives from trade statistics, such as the market price of a currency without a price from the price
feed (see [`trade-statistics-market-price.md`](trade-statistics-market-price.md)), average prices and
volumes, are therefore informational. They must not change an offer, a trade, a fee or the DAO state.

## Reasons

- Rule 2, date: a statistic carries the date of its trade, which lies in the past when the
  statistic is published. A statistic dated in the future would be the newest one of its currency
  until that date, also after a restart. One day covers differences between the clocks of nodes, as
  for account age witnesses and signed witnesses.
- Rule 2, price: the limit is far above the price of any trade. Prices derived from valid
  statistics, such as the market price of a currency without a price from the price feed, stay in
  the long range when they are converted.
- Rule 2, volume: the charts, the trades table and the average prices compute the volume from the
  price and the amount, and some of them divide by it. For values which no trade can have, this
  computation fails or gives zero.
- Storage and relay, currency: the currency list differs between versions. A later version can add
  a currency, and its statistics must reach the nodes of that version through nodes of this version
  as well. A node which updates then already holds the statistics of the new currency. Nodes of
  earlier versions stored and relayed every statistic.
- Rule 4: a price derived from trade statistics is for display only. The state of an offer depends
  only on the price feed.
- Rule 5: the validity rules bound single values (rule 2), not every combination of them. A view
  must not fail because of the values of a statistic.
- Rule 6: statistics dated before 2021-11-01 have no amount cap, and any peer can publish many
  statistics, so a sum of valid values can exceed the long range. A sum which wraps around gives a
  negative or wrong total or average. An average of floating point sums stays below about twice the
  highest price of its statistics (the volume of a statistic is rounded), so with the price limit of
  rule 2 within the long range. A total which saturates stays at least as large as each of its
  parts.

## Compatibility

The wire format, persisted format and hash calculation stay compatible with earlier versions. All
statistics in the bundled stores pass the date, price and volume checks. A node whose
clock is more than one day behind does not use new statistics until its clock is corrected and it
is restarted. While no recent price from the price feed is available, for example right after
startup or during an outage of the price feed, no offer is deactivated by its trigger price.

## Known gaps

While a node holds no price from the price feed for a currency, the price alert and the market alert
of the mobile notifications compare against the market price derived from trade statistics, which any
peer can set by publishing statistics. A price alert which fires is removed.

The average BSQ prices of 30 and 90 days weight each statistic by its volume and leave out only
prices more than 10 standard deviations from the trimmed mean. A valid statistic of 2 BTC is more than
the BSQ volume of a month. On the BSQ trades of the 30 days to 2026-11-16, one such statistic at twice
the average stays in the average and moves it from 1,386 to 2,141 satoshis per BSQ, and one at half the
average moves it to 813 satoshis. A statistic at a very low price moves it further, because its BSQ volume
is its BTC amount divided by its price and the band of 10 standard deviations reaches below zero: one
statistic of 2 BTC at 10 satoshis per BSQ moves the average of 30 days to about 14 and the average of 90
days to about 29 satoshis per BSQ (statistics to 2026-11-18).

The trade volume of an altcoin in trade statistics over its look-back period decides whether the
node shows the altcoin as actively traded or as delisted (DAO parameter `ASSET_MIN_VOLUME`). Only
altcoins which are not delisted can be chosen for a new payment account and in the preferences.
Valid but invented statistics can therefore keep a delisted altcoin listed on every node which
receives them. They cannot delist an altcoin, because statistics are never removed and the traded
amount never wraps around (rule 6).
