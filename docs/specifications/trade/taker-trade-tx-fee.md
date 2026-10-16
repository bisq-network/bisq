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

The desktop taker estimates the vsize of its taker fee transaction from its wallet, or assumes
233 vbytes if the wallet cannot fund the estimate. It adds 70 vbytes if it is set to pay the taker
fee in BSQ, averages the result with the 233 vbytes of the deposit transaction and uses at least
233 vbytes. The fee is that vsize times its fee rate. If the wallet is empty, the fee is 233
vbytes times the fee rate. The API taker always uses 212 vbytes.

The desktop taker fixes the fee when it shows the funding screen and does not update it afterwards.

The estimated vsize grows with the number of wallet inputs the taker fee transaction needs. With
equal fee rates on both sides, a taker fee transaction from 617 vbytes on, about 8 or more inputs,
results in a fee above what the maker accepts.

## Rule

Before the taker contacts the maker, it checks its stated fee with the same check the maker
applies, using its own current fee rate. If the check fails, the taker does not take the offer and
tells the user to consolidate the wallet funds into fewer inputs, by sending them to an address of
the own Bisq wallet, and to take the offer again after that transaction is confirmed. No taker fee
is paid and the maker's offer is not affected.

## Compatibility

The rule is local to the taker. It changes no message, contract or persisted data, and it only
stops take offer requests that a maker with the same fee rate would reject.

## Not covered

- The fee rates of taker and maker can differ, so a request that passes the taker's check can
  still be rejected by the maker, and a request the taker stops could have been accepted.
- A large change of the fee rate after the desktop taker fixed its fee also fails the check. The
  user then has to open the offer again instead of consolidating.
- The maker still drops an invalid take offer request without a reply.
- The taker's fee is not reduced to fit the maker's range. Paying the miner fee of the taker fee
  transaction separately from the stated fee would remove the need to consolidate, but is a
  separate change.
