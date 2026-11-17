# BSQ offer price range

## Scope

Which offers the BSQ offer book of the desktop application hides because their price is far from the
average BSQ price of the last 90 days, the preference that sets the range, and the hint shown when the
range hides every offer.

## Rules

1. The BSQ offer book (the BSQ tab of the "Buy" and of the "Sell" screen) hides an offer if its price is above
   the reference price times the factor, or below the reference price divided by the factor. An offer with a
   price equal to one of the two bounds is shown. With the factor 4, an offer is hidden if its price is more
   than 4 times or less than a quarter of the reference price.
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
5. The reference price and the factor in use are those at the last filtering of the offer book: when the
   tab is selected or the offer book is opened, when a filter of the offer book changes, and after each new
   block. A changed factor applies when the offer book is opened again.
6. When the BSQ tab is selected and the offer book shows no offer, but at least one offer passes all other
   filters of the offer book and only the range hides it, the user gets a hint: the range may need to be
   adjusted, with a button that opens the preferences. There is no hint when the offer book has no offer for
   the selection at all, or when other filters hide every offer.
   - The hint depends on the settled state: it appears only if the condition still holds about one second
     after it began. The offer book passes through intermediate states, for example when an edited offer is
     replaced, when offers arrive one by one, or while the screen opens.
   - It appears at most once per visit: from opening the BSQ tab, or the screen with the BSQ tab selected,
     until the user switches to another tab or screen. The user can turn it off with "Don't show again".
7. The range changes only what the BSQ offer book shows. It does not change offers, taking an offer, the
   API, or other views.

## Trust model

The reference price comes from trade statistics. A trade statistic is an unauthenticated claim which any
peer can publish (see the trust model of
[`../trade/trade-statistics-validity.md`](../trade/trade-statistics-validity.md)), and enough invented
statistics move the average of 90 days. The average is weighted by volume, and a statistic may state up to
2 BTC, so a few invented statistics move it far when the BSQ volume of the 90 days is small.

A moved average moves the range: offers with a fair price fall outside and are hidden, while offers near
the moved average stay visible. The offer book is then not empty, so the hint of rule 6 does not appear.
The percentage which the offer book shows next to the price of a BSQ offer refers to the average of 30 days
of the same statistics, so it does not reveal the move either. The range therefore protects against offers
with absurd prices only while the trade statistics are honest. It is a display filter, not a check: the user
still has to verify the price of an offer before taking it.

Rule 4 keeps a missing reference price from hiding offers. Rule 6 keeps a reference price which is far from
the market from emptying the offer book without explanation.

## Reasons

- Rules 1 and 2: the offer book drops an offer with a fixed price far from the market price only if the
  price feed has a recent price for the currency. A price which the node derives from trade statistics
  does not count. As long as the price providers deliver no BSQ price, the offer book shows fixed price BSQ
  offers at any price, and the trade statistics are the only reference for BSQ. An honest trade far off the
  market is small compared to the volume of 90 days, so it moves the average little. This does not hold for
  invented statistics (see the trust model).
- Rule 1, bounds: the range is symmetric in ratio, not in difference: a price 2 times the reference and a
  price half the reference are equally far from it.
- Rule 3, default: a factor of 2 keeps offers visible when the market price moved within the 90 days, and
  hides offers at several times or a fraction of the market price.
- Rule 3, maximum: a factor of 1000 hides practically no offer. It takes the place of a switch to turn the
  range off.
- Rule 4, own offers: a maker sees and manages their own offers in the offer book, whatever their price.
- Rule 6: an empty offer book looks like a market without offers. The hint names the range as the reason
  only when it is the reason, so that it never asks the user to change a setting which would not show any
  offer.

## Compatibility

The preferences store the factor in a new field. A version without the preference ignores the field. A data
directory of such a version yields 0, which rule 3 replaces with the default, so that after the update the
range hides offers outside the default range without any action of the user. No network message, network
data or API changes.

## Open decisions

- `one-sided-range`: hide only offers whose price is worse for the user: above the reference price times the
  factor where the user buys BSQ, and below the reference price divided by the factor where the user sells
  BSQ. A moved average could then hide offers, but never leave only offers with a price worse than the
  market visible. Offers far below the market where the user buys BSQ would stay visible. The current rule
  hides both sides, as requested.

## Known gaps

- The offer book does not show how many offers the range hides, only the hint of rule 6 when it hides all of
  them.
- Trade statistics which the node receives while the offer book is open count from the next filtering
  (rule 5).

## Not covered

The offer book chart of the market view, the offer counts of the currency selection, the offers of the API,
and the take offer screen.
