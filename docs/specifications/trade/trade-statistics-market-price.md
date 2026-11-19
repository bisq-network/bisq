# Market price from trade statistics

## Scope

This specification defines the market price which a Bisq 1 node derives from its trade statistics for a
currency for which the price feed has no price, and how the user interface labels it. BSQ is such a
currency: no price provider delivers it.

The main window shows the derived price in its market price box, and the DAO dashboard shows the derived
BSQ price as its headline. The spread view uses it as reference, and the price alert and the market alert
of the mobile notifications compare against it. Offers, trades and fees do not use it (see
[`trade-statistics-validity.md`](trade-statistics-validity.md)).

## Rules

1. The price of a currency is derived from the valid statistics of the currency which the node holds,
   ordered by their trade date. A statistic which the node receives after newer statistics of its currency
   does not decide the price. A statistic dated ahead of the node's clock, which validity allows by up to
   one day, is the newest one of its currency until a statistic with a later date arrives.
2. For BSQ, the price is the volume weighted average price of the BSQ statistics dated in the 24 hours up to
   and including the newest BSQ statistic, at most the newest 200 of them: the sum of their BTC amounts
   divided by the sum of their BSQ volumes. A statistic dated exactly 24 hours before the newest one is not
   part of the average.
3. For every other currency, the price is the price of its newest statistic.
4. The price is never 0. If the average of rule 2 cannot be represented as a long (rule 5 of
   `trade-statistics-validity.md`), the price of the newest BSQ statistic is used.
5. A price from the price feed replaces the derived price of its currency.
6. The main window labels a derived BSQ price as an average of recent Bisq trades and names the period in
   its tooltip, and a derived price of another currency as the price of the latest Bisq trade. The DAO
   dashboard names the period below a derived BSQ price.
7. A received statistic costs no work which grows with the number of statistics which the node holds. The
   BSQ price is derived once after the statistics which arrived together, for example in one bundle of the
   network, are applied.

## Trust model

Trade statistics are unauthenticated (see the trust model of `trade-statistics-validity.md`). Any peer can
publish a valid statistic with any price up to the limit, an amount of up to 2 BTC and the current date. The
derived price is informational.

The volume weight limits the effect of a statistic to its share of the volume. On a day with 1,665 BSQ
traded at 1,200 satoshis per BSQ, a trade of 25 BSQ at twice the price moves the average by 1.5%, where it
doubled the price of the newest trade. A statistic of nearly 2 BTC, about 100 days of that BSQ volume, at
twice the price sets the average to nearly twice the price until the newest BSQ statistic is dated more than
24 hours after it, where the price of the newest trade was replaced by the next trade. Dated one day ahead
of the clock, it stays in the average for about two days.

A statistic dated ahead moves the end of the period (rule 2) to its date. The trades of the last day which
are more than 24 hours older than that date then do not count, until a statistic with a later date arrives.
A small one at a distant price is therefore the price until the next BSQ trade arrives, as the price of the
newest trade was. Neither rule protects the price against invented statistics.

## Reasons

- Rule 1: statistics do not arrive in the order of their trades. A trader republishes the statistic of an
  older trade which the network misses, and a node receives statistics which it missed. Before, the price of
  the statistic received last was used, which could be the price of a trade of months ago. A statistic dated
  ahead was the newest one after a restart already; it is now the newest one while the node runs as well.
- Rule 2: the prices of single BSQ trades differ widely, because makers ask very different prices, and some
  trades are far off the market. In the year to November 2026 single trades were up to 20 times below and
  10 times above the market, and each one was shown as the BSQ price until the next trade. On the BSQ trades
  of the three months to November 2026, the average changes by a median of 0.14% per trade instead of 2.4%,
  and its largest deviation from the average of 7 days is 41% instead of 192%. Leaving out prices far from
  the median of the period, a volume weighted median, or a weight by the BTC amount did not improve these
  figures.
- Rule 2, period: the market has about 10 BSQ trades per day, so 24 hours cover several trades and follow a
  change of the market within a day. The period ends at the newest BSQ statistic, not at the current time,
  so it always contains a trade, and a market without trades keeps the average of its last trading day.
- Rule 3: other currencies are rarely without a price from the price feed. This specification does not
  change their price.
- Rule 4: the consumers of the price divide by it.
- Rule 7 and the limit of rule 2: any peer can publish valid statistics without limit, about 200,000 in one
  bundle of 10 MB, and a statistic dated before 2021-11-01 is valid with any currency and without a hash
  check. A search through all statistics of a node (about 425,000 in November 2026) for each received
  statistic, or an average over all BSQ statistics of the period for each received BSQ statistic, blocked
  the user thread of every node which received such a bundle for many minutes. No day of the history up to
  November 2026 had more than 49 BSQ trades, so the limit of 200 does not change the price of a real
  trading day.

## Compatibility

No message, persisted data or API change. The derived BSQ price changes from the price of the newest BSQ
trade to the average, and a price alert for BSQ fires on the average.

## Open decisions

- The period of 24 hours fits the current number of BSQ trades.
- Rule 3 could use the average of rule 2 for every currency. It would then also apply to every currency
  while the price feed is not available.
- Statistics dated after the node's clock could be left out when the newest statistic is chosen. That limits
  what a statistic dated ahead can do, but a statistic of a peer whose clock is ahead would count only from
  the next statistic which arrives after its date.

## Not covered

The average BSQ prices of 30 and 90 days, the market charts, and the market price of the API (see
[`../api/market-price.md`](../api/market-price.md)).
