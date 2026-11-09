# Taker trade transaction fee

## Scope

This specification covers the trade transaction fee the taker states when taking an offer of the
Bisq v1 trade protocol, and when the taker must not take an offer because of it. BSQ swaps are out
of scope.

## The trade transaction fee

The taker states one miner fee, the trade transaction fee, in its take offer request. The taker
pays it three times: as the miner fee of its taker fee transaction, and as the miner fee of the
deposit transaction and of the payout transaction, which it funds in its deposit input.

The maker accepts the stated fee only if it is between 424 and 360,000 satoshi and differs by at
most a factor of 4 from the fee rate of the maker's fee service times 212 vbytes, the average of
a typical taker fee transaction (192 vbytes) and a deposit transaction (233 vbytes). Maker and
taker can get quite different fee rates, as their pricenodes average different mempool instances
at different times. Makers up to v1.10.11 allow a factor of 2. The maker drops a request that fails
this check without a reply, so the taker would only see the trade protocol timeout.

424 satoshi is 2 sat/vbyte for those 212 vbytes, so the trade transactions pay at least that rate.
Makers up to v1.10.11 accepted down to 250 satoshi. Loading a trade from disk checks its fee only
against these 250 satoshi, because one trade that fails a check makes the whole trade file
unreadable.

## Fee rate

Maker and taker use the trade fee rate that their pricenode delivers, kept between 2 and 600
sat/vbyte. 2 sat/vbyte is the lowest rate whose trade transaction fee makers accept: 2 x 212 = 424
satoshi. The client raises a lower delivered rate to 2 sat/vbyte itself, so it never states or
expects a fee that makers refuse, whatever a pricenode delivers.

The pricenode raises its estimate to the same floor, `MIN_FEE_RATE_FOR_TRADING` of bisq-pricenode.
Versions up to v1.10.11 use the delivered rate as it is, so for them the pricenode floor is the only
one. It must not be below the client floor: at 1 sat/vbyte the API taker states 212 satoshi and the
desktop taker 233 satoshi unless its taker fee transaction is large, both below the minimum of 424
satoshi.

The client floor must not be above the pricenode floor. A client with a higher floor, such as the
network default min fee rate of 5 sat/vbyte, would state and expect fees outside the factor of 2
that makers up to v1.10.11 allow whenever the pricenode delivers less, and both sides would refuse
each other's take offer requests. So the client floor changes only together with the minimum that
makers accept, and a higher floor for all versions can only come from the pricenode.

A pricenode without an estimate from any of its sources must not present its floor as an estimate:
it sends its fee data with timestamp 0. Clients ignore fee data with timestamp 0 and keep their last
fee rate.

The trade transaction fee also sets the fee rate of the delayed payout transaction: the fee divided
by 278 vbytes, but at least 10 sat/vbyte, as that transaction is published much later and the
traders cannot raise its fee. Seller, buyer and refund agent derive its outputs independently, so
that minimum must not change without a version that both traders negotiate and the contract
records.

## How the taker derives the fee

The desktop taker derives the fee from an estimate of the vsize of its taker fee transaction, which
grows with the number of wallet inputs that transaction needs (TxFeeEstimationService). The API
taker states the fee that the maker expects at its own fee rate.

The desktop taker fixes the fee when it shows the funding screen and does not update it afterwards.
With an empty wallet it uses 233 vbytes and the fee rate from when it opened the take offer screen.

With equal fee rates on both sides, an estimated taker fee transaction from 617 vbytes on gives a
fee above what the maker accepts: with native segwit inputs that is about 8 inputs, with legacy
inputs about 4. The estimate is normally within 20 % of the size that BitcoinJ reports for it; the
transaction that is built later can differ more.

## Rule

Before it sends the take offer request, the taker checks its stated fee with the check of makers up
to v1.10.11, a factor of 2, using its own current fee rate. Differences between fee providers play
no role in this check, and a request that passes it is accepted by makers of all versions with the
same fee rate. If the check fails, the taker does not take the
offer: no taker fee is paid, no trade is created and the maker's offer is not affected. The message
depends on the direction:

- Above the range: the wallet holds many small amounts, or the fee rate has dropped since the fee
  was fixed. The user is told to take the offer again if the fee rate has dropped, and otherwise to
  send the funds to an address of the own wallet in one transaction and to take the offer again
  after that transaction is confirmed.
- Below the range or below 424 satoshi: the fee rate has risen since the fee was fixed, or it is
  very low. The user is told to take the offer again, or to try again later.

The message names no desktop screen, as API users get it as well. An API taker gets the refusal as
the only answer to its take offer call. The API passes on only the last line of an error message, so
the message below the range, which is the one an API taker gets in practice, is a single line.

In the desktop, a refusal before the trade exists ends the take offer attempt as an offer that is no
longer available does: the confirmation window closes, the warning offers to go to "Funds/Send
funds", and the take offer screen closes, without a further warning that the offer was removed. The
other checks before the request, for a banned currency, payment method, offer or maker, a required
update and an offer that was already used in a trade, end the attempt in the same way.

A buyer checks the fee again, with its fee rate at that time, the maker's factor of 4 and the
minimum of 424 satoshi, when it verifies the delayed payout transaction, after the taker fee is
published and before the deposit transaction is published. The check before the request prevents
most of these late failures, which cost the taker fee.

## Compatibility

The rule is local to the taker. It changes no message, contract or persisted data. Makers from
v1.10.0 to v1.10.11 apply the same check, and the GitHub release notes of v1.10.9 state that
updating to that version is required to continue trading, so the taker only stops take offer
requests that such a maker with the same fee rate would reject.

The maker's factor of 4 is local to the maker as well: a later maker accepts more requests, and a
maker up to v1.10.11 behaves as before. The taker keeps the factor of 2 until makers up to v1.10.11
can no longer trade; then it can use the factor of 4 as well.

The minimum of 424 satoshi refuses no taker of an earlier version at the pricenode floor of
2 sat/vbyte: the API taker states 424 satoshi there and the desktop taker at least 466 satoshi.

The client floor of 2 sat/vbyte equals the pricenode floor, so it changes no fee as long as the
pricenodes deliver at least that rate.

## Not covered

- The fee rates of taker and maker can differ, so a request that passes the taker's check can
  still be rejected by the maker, and a request the taker stops could have been accepted.
- A large change of the fee rate after the desktop taker fixed its fee also fails the check. The
  user then has to take the offer again.
- The estimate uses the coins of the wallet, also when the user funds the trade from an external
  wallet, so a wallet with many small amounts can cause a refusal although that trade would need one
  input.
- The taker stops a request whose fee is between 2 and 4 times its expected fee, although a later
  maker would accept it.
- A trade below 424 satoshi that a maker up to v1.10.11 accepted fails the buyer's check of the
  delayed payout transaction if the buyer updates during the take. An honest taker states such a
  fee only at a fee rate below the pricenode floor.
- The maker still drops an invalid take offer request without a reply.
- The taker's fee is not reduced to fit the maker's range. Paying the miner fee of the taker fee
  transaction separately from the stated fee would remove the need to consolidate, but is a
  separate change.
