# BSQ offer price range

## Scope

Which offers the BSQ offer book and the BSQ market of the offer book chart of the desktop application hide
because their price is far from the average BSQ price of the last 90 days, the preference that sets the
range, and the hint shown when the range hides every offer of one of these views.

## Rules

1. The BSQ offer book (the BSQ tab of the "Buy" and of the "Sell" screen) and the offer book chart of the
   market view with BSQ selected hide an offer if its price is worse for the taker than the range around the
   reference price: an offer to sell BSQ (its maker buys BTC) if its price is above the reference price times
   the factor, and an offer to buy BSQ (its maker sells BTC) if its price is below the reference price
   divided by the factor. A price on the other side of the reference price is better for the taker and never
   hidden. The chart leaves a hidden offer out of the depth chart and of the two offer lists below it. An
   offer with a price equal to its bound is shown. With the factor 4, an offer to sell BSQ is hidden if its
   price is more than 4 times the reference price, and an offer to buy BSQ if its price is less than a
   quarter of it.
2. The reference price is the average BSQ/BTC price of the BSQ trades of the last 90 days, which the node
   computes from its trade statistics. It is the average which the DAO dashboard shows as the 90 days
   average BSQ/BTC trade price, with the outlier threshold of the preferences.
3. The factor is a preference of the user, which the user enters in the DAO options of the preferences. It
   is a number above 1 and at most 1000. The default is 2. A stored factor outside this range is replaced by
   the default; this includes the 0 which a data directory of a version without the preference yields.
4. The range never hides:
   - any offer while the node has no reference price, because it holds no BSQ trade statistic of the last
     90 days or the average cannot be computed (rule 5 of
     [`../trade/trade-statistics-validity.md`](../trade/trade-statistics-validity.md));
   - an offer without a price, such as an offer with a price relative to the market price while the node
     has no recent market price for BSQ;
   - the user's own offers.
5. The reference price and the factor in use are those at the last filtering:
   - in the offer book: when the tab is selected or the offer book is opened, when a filter of the offer book
     changes, and after each new block;
   - in the chart: when the chart is opened, when BSQ is selected, and when a BSQ offer is added or removed.

   A changed factor applies when the view is opened again.
6. When the BSQ tab is selected and the offer book shows no offer, but at least one offer passes all other
   filters of the offer book and only the range hides it, the user gets a hint: the range may need to be
   adjusted, with a button that opens the preferences. There is no hint when the offer book has no offer for
   the selection at all, or when other filters hide every offer.
   - The hint depends on the settled state: it appears only if the condition still holds about one second
     after it began. The offer book passes through intermediate states, for example when an edited offer is
     replaced, when offers arrive one by one, or while the screen opens.
     If other popups delay the hint, its condition is checked again when it can be displayed.
   - It appears at most once per visit: from opening the BSQ tab, or the screen with the BSQ tab selected,
     until the user switches to another tab or screen. The user can turn it off with "Don't show again".
   - The chart with BSQ selected shows the same hint when neither of its offer lists shows an offer, but the
     range hides at least one BSQ offer. It appears only while the offer book tab of the market view is
     selected, after the same wait, and at most once until the user switches to another tab of the market
     view or leaves it. Selecting another currency and BSQ again within that time does not show it again.
   - "Don't show again" turns the hint off in both views.
7. The range changes only what the BSQ offer book and the chart show. It does not change offers, taking an
   offer, the API, or other views.

## Trust model

The reference price comes from trade statistics. A trade statistic is an unauthenticated claim which any
peer can publish without limit (see the trust model of
[`../trade/trade-statistics-validity.md`](../trade/trade-statistics-validity.md)), so any peer can move the
average of 90 days. One statistic at a price far below the market is enough: the average divides the BTC
amounts by the BSQ volumes, and the BSQ volume of a statistic is its BTC amount divided by its price. The
outlier threshold does not remove it, as ten standard deviations of the BSQ prices reach below zero. On the
941 BSQ statistics of the 90 days to November 2026 (average 1,724 satoshis per BSQ), one statistic of 2 BTC at
10 satoshis per BSQ moves the average to 29 satoshis.

A moved average moves the range. As only prices worse for the taker are hidden (rule 1), every offer which
stays visible on one side of the book is better for the taker than every offer which the range hides there.
An average moved below the market hides the fair offers to sell BSQ and keeps the offers to sell BSQ below
the market, which are better for the taker. An average moved above the market does the same with the offers
to buy BSQ. A moved average can therefore empty one side of the book, and the hint of rule 6 then names the
range, but it cannot leave only offers at prices worse than the market visible. The percentage which the
offer book shows next to the price of a BSQ offer refers to the average of 30 days of the same statistics,
and the market price of the main window to the statistics of the last day
([`../trade/trade-statistics-market-price.md`](../trade/trade-statistics-market-price.md)), so neither
reveals a moved average. The range is a display filter, not a check: the user still has to verify the price
of an offer before taking it.

Rule 4 keeps a missing reference price from hiding offers. Rule 6 keeps a reference price which is far from
the market from emptying the offer book or the chart without explanation.

## Reasons

- Rules 1 and 2: the offer book drops an offer with a fixed price far from the market price only if the
  price feed has a recent price for the currency. A price which the node derives from trade statistics
  does not count. As long as the price providers deliver no BSQ price, the offer book shows fixed price BSQ
  offers at any price, and the trade statistics are the only reference for BSQ. An honest trade far off the
  market is small compared to the volume of 90 days, so it moves the average little. This does not hold for
  invented statistics (see the trust model).
- Rule 1, one side: any peer can move the reference price (see the trust model). If the range also hid prices
  better for the taker, a moved reference price could hide all fair offers of one side of the book and leave
  an offer near the moved price, far worse for the taker than the market, as the only one visible.
- Rule 1, bounds: the bounds are ratios, not differences: a price 2 times the reference and a price half the
  reference are equally far from it.
- Rule 3, default: a factor of 2 keeps offers visible when the market price moved within the 90 days, and
  hides offers at several times or a fraction of the market price.
- Rule 3, maximum: a factor of 1000 hides practically no offer. It takes the place of a switch to turn the
  range off.
- Rule 1, chart: the chart and the offer book show the same BSQ offers, so that the market view does not
  present offers which the offer book hides. The chart has its own display limit: it leaves out of the depth
  chart, but not of the offer lists, buy offers at a third or less of the highest buy price and sell offers at
  3 times or more of the lowest sell price. The range applies before it.
- Rule 4, own offers: a maker sees and manages their own offers in the offer book, whatever their price.
- Rule 6: an empty offer book or chart looks like a market without offers. The hint names the range as the
  reason only when it is the reason, so that it never asks the user to change a setting which would not show
  any offer. Both views share the hint and its "Don't show again", as it says the same about the same
  setting.

## Compatibility

The preferences store the factor in a new field. A version without the preference ignores the field. A data
directory of such a version yields 0, which rule 3 replaces with the default, so that after the update the
range hides offers outside the default range without any action of the user. No network message, network
data or API changes.

## Open decisions

- `two-sided-range`: the range was requested to hide offers on both sides of the reference price. Rule 1
  hides only prices worse for the taker, because one invented statistic can move the reference price (see
  the trust model and rule 1, one side). Offers far better for the taker than the market stay visible.

## Known gaps

- The offer book and the chart do not show how many offers the range hides, only the hint of rule 6 when it
  hides all of them.
- Trade statistics which the node receives while the offer book is open count from the next filtering
  (rule 5).

## Not covered

The offer counts of the currency selection in the offer book and in the chart, the spread view of the
market view, the offers of the API, and the take offer screen.
