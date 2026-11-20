# Take offer requests during a pending handshake

## Scope

This specification covers which take offer requests (`InputsForDepositTxRequest`) the maker of the
Bisq v1 trade protocol accepts for an offer for which it has already started a trade, and what the
maker answers to an availability request for that offer. BSQ swaps are out of scope.

## The take offer handshake

When the maker accepts a take offer request, it reserves the offer, starts a trade whose trade ID is
the offer ID, and answers with its data for the deposit transaction. The taker then publishes its
taker fee transaction and sends its next message. With that message the maker removes the offer.
Until then the offer stays in the offer book, and the maker cannot know whether the taker continues.

The maker waits 120 seconds, counted from the request, for the taker's next message. After that the
maker's trade gets a timeout error. The error is not final: if the taker's message arrives later and
the maker has not accepted another request for the offer in the meantime, the trade continues.

The handshake of a request is pending while all of the following hold:

- the trade of the request is in the maker's pending trades;
- the trade has no error;
- the system clock shows a time less than 120 seconds after the request and less than 120 seconds
  before it.

## Rules

1. The maker ignores a take offer request for an offer while the handshake of an earlier request for
   that offer is pending. It starts no trade and does not reserve the offer. As for every take offer
   request that the maker refuses, it sends no reply, and the taker's attempt ends with the timeout
   of the taker. That taker has not published its taker fee transaction.
2. Rule 1 holds whatever the state of the offer is. The reservation of the offer ends 60 seconds
   after the request, at a restart of the maker, and with any other change of the offer state.
3. A trade whose handshake is not pending does not block the offer: a trade with an error, for
   example after the maker's validation of the request failed, and a trade whose 120 seconds have
   passed, also if it has no error. Another taker can then take the offer.
4. While the handshake of a request for an offer is pending, the maker answers an availability
   request for that offer with "offer taken", as it does for a reserved offer. A taker asks for the
   availability before it sends a take offer request, so it learns at once that it cannot take the
   offer, and it sends no request that the maker would ignore. What the taker is told is specified in
   [`../offer/take-offer-refusals.md`](../offer/take-offer-refusals.md).

## Rationale

The reservation ends 60 seconds after the request, but the maker waits 120 seconds for the taker.
Without rule 1 the maker accepts the take offer request of another taker in the second half of that
time. The new trade gets the same trade ID and resets the protocol of the first trade, so the maker
drops the next message of the first taker without a reply. The first taker has published its taker
fee transaction by then and is left with a trade that cannot continue. Over Tor a handshake that
takes more than 60 seconds is realistic, so this happens between two honest takers as well.

A trade stays in the maker's pending trades when its taker does not continue or when it fails, until
the user moves it to the failed trades. So the existence of a trade for the offer cannot be the
condition: the offer would stay published without anybody being able to take it. The 120 seconds are
the time after which the maker's own protocol reports the trade as timed out.

A failure of the maker's first step frees the offer at once, so that the taker or another taker can
try again without waiting. That stays as it is, as the trade has an error then.

The condition uses the data of the trade and not the reservation or the timer of the protocol. So it
also holds after a restart, where the reservation is gone and no timer runs. A message of the taker
in the wrong phase stops the timer of the maker's protocol without an error; it does not end the
handshake.

A system clock that was set back shows a time before the request. If every such time counted as
pending, the handshake would block the offer for as long as the clock was set back, for example one
hour, when no timer of the protocol sets the error of the trade before: after a restart, after the
timer was stopped, or on a node without a graphical user interface, whose timers use the system
clock as well. If no such time counted, a small correction of the clock during a handshake would end
it early: the timer of the desktop application does not follow the system clock and sets the timeout
error 120 seconds after the request, but after the reservation another request would be accepted
before that. So a time less than 120 seconds before the request counts as pending, like a time less
than 120 seconds after it. A clock set back by less than 120 seconds keeps the handshake pending. A
clock set back further blocks the offer only while it shows a time within 120 seconds of the
request, not for the whole time it was set back.

## Compatibility

No message and no persisted data change. "Offer taken" is the answer that makers of all versions
give for a reserved offer, and takers of all versions know it. A taker whose take offer request is
ignored sees what it sees for every refused take offer request. Makers of earlier versions accept a
second request once the reservation of 60 seconds has ended.

A taker whose attempt fails on its own side after the maker accepted its request, for example
because its taker fee transaction could not be published, waits longer before it can take the offer
again. The maker's trade gets its error only with the timeout, so the maker answers "offer taken" to
that taker as well until the 120 seconds have passed. With makers of earlier versions the taker
could take the offer again after the 60 seconds of the reservation.

## Not covered

- Once the handshake is not pending any more, the maker accepts another request for the offer, as
  before. The first trade then stays in the pending trades with the same trade ID as the new trade,
  marked with an error, until the user moves it to the failed trades. A lookup of a trade by its ID
  returns the older trade. Retiring the replaced trade, or refusing every further request for an offer
  whose first request was answered, would remove this, but it changes what happens to the trades and
  offers of honest users after a failed attempt and needs a decision of the maintainers.
- The rule protects a first taker who keeps to the protocol. It does not protect the maker from a
  taker with a changed client: such a taker can wait for the 120 seconds, or can make the first trade
  fail with an invalid message after the maker's answer, and then send a second request.
- A first taker who needs more than 120 seconds is replaced by the next request, as before, and
  loses its taker fee.
- A failure of the maker's first step sets the error of the trade when a task of the step reports
  it. If the step fails outside of its tasks, the trade has no error, and the offer is free again
  only when the 120 seconds have passed.
- The 120 seconds are measured with the system clock, so a change of the clock makes the time
  shorter or longer. While the clock shows a time 120 seconds or more before a request, for example
  after it was set back by more than 120 seconds, the handshake of that request is not pending, and
  a second request can replace the first trade, as before. When the clock reaches the time 120
  seconds before the request again, a trade that still has no error blocks the offer again until the
  clock shows 120 seconds after the request.
- Every take offer request that the maker accepts and whose taker does not continue leaves a trade
  in the maker's pending trades.
- Offers which share one maker fee transaction have different IDs. A request for one of them is not
  refused because of the handshake of another one.
- BSQ swaps. A swap trade is stored only when it is completed, and the timeout of its handshake
  (40 seconds) is shorter than the reservation of the offer.
