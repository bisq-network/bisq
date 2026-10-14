# Trade statistics validity

## Scope

This specification defines which trade statistics a BISQ 1 node uses.

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

## Reasons

- Rule 2, date: a statistic carries the date of its trade, which lies in the past when the
  statistic is published. A statistic dated in the future would be the newest one of its currency
  until that date, also after a restart. One day covers differences between the clocks of nodes, as
  for account age witnesses and signed witnesses.
- Rule 2, volume: the charts, the trades table and the average prices compute the volume from the
  price and the amount, and some of them divide by it. For values which no trade can have, this
  computation fails or gives zero.

## Compatibility

No message, no persisted data and no hash change. Trade statistics are stored and relayed as
before. All statistics in the bundled stores pass the date and volume checks. A node whose clock is
more than one day behind does not use new statistics until its clock is corrected and it is
restarted.

## Not covered

Rules for storing and relaying trade statistics.
