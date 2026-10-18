# Taker trade transaction fee

## Scope

This specification covers the trade transaction fee the taker states when taking an offer of the
Bisq v1 trade protocol, and when the taker must not take an offer because of it. BSQ swaps are out
of scope.

## The trade transaction fee

The taker states one miner fee, the trade transaction fee, in its take offer request. The taker
pays it three times: as the miner fee of its taker fee transaction, and as the miner fee of the
deposit transaction and of the payout transaction, which it funds in its deposit input.

The maker accepts the stated fee only if it is between 250 and 360,000 satoshi and differs by at
most a factor of 2 from the fee rate of the maker's fee service times 212 vbytes, the average of
a typical taker fee transaction (192 vbytes) and a deposit transaction (233 vbytes). The maker
drops a request that fails this check without a reply, so the taker would only see the trade
protocol timeout.

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

Before it sends the take offer request, the taker checks its stated fee with the same check the
maker applies, using its own current fee rate. If the check fails, the taker does not take the
offer: no taker fee is paid, no trade is created and the maker's offer is not affected. The message
depends on the direction:

- Above the range: the wallet holds many small amounts, or the fee rate has dropped since the fee
  was fixed. The user is told to open the offer again if the fee rate has dropped, and otherwise to
  send the funds to an address of the own wallet in one transaction and to open the offer again
  after that transaction is confirmed.
- Below the range or below 250 satoshi: the fee rate has risen since the fee was fixed, or it is
  very low. The user is told to open the offer again, or to try again later.

The message names no desktop screen, as API users get it as well. An API taker gets the refusal as
the only answer to its take offer call. The API passes on only the last line of an error message, so
the message below the range, which is the one an API taker gets in practice, is a single line.

In the desktop, a refusal before the trade exists ends the take offer attempt as an offer that is no
longer available does: the confirmation window closes, the warning offers to go to "Funds/Send
funds", and the take offer screen closes, without a further warning that the offer was removed. The
other checks before the request, for a banned currency, payment method, offer or maker, a required
update and an offer that was already used in a trade, end the attempt in the same way.

A buyer as taker checks the same fee again, with its fee rate at that time, when it verifies the
delayed payout transaction, after its taker fee is published. The check before the request prevents
most of these late failures, which cost the taker fee.

## Compatibility

The rule is local to the taker. It changes no message, contract or persisted data. Makers apply the
same check since v1.10.0, and the GitHub release notes of v1.10.9 state that updating to that
version is required to continue trading, so the taker only stops take offer requests that a maker
with the same fee rate would reject.

## Not covered

- The fee rates of taker and maker can differ, so a request that passes the taker's check can
  still be rejected by the maker, and a request the taker stops could have been accepted.
- A large change of the fee rate after the desktop taker fixed its fee also fails the check. The
  user then has to open the offer again.
- The estimate uses the coins of the wallet, also when the user funds the trade from an external
  wallet, so a wallet with many small amounts can cause a refusal although that trade would need one
  input.
- If a later version relaxes the maker's rule, takers of this version keep the stricter check until
  they update.
- The maker still drops an invalid take offer request without a reply.
- The taker's fee is not reduced to fit the maker's range. Paying the miner fee of the taker fee
  transaction separately from the stated fee would remove the need to consolidate, but is a
  separate change.
