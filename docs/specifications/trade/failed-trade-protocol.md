# Protocol of a failed trade

## Scope

This specification defines what the protocol of a Bisq v1 trade does when the trader moves the trade
to the failed trades, from the pending trades view or with the `failtrade` API call, and when the
trade returns to the pending trades.

## Rules

A trade in the failed trades is not driven by its protocol. When the trade is moved there, its
protocol stops:

- It runs no further task of a task sequence that is under way, and it starts no new task or other
  action, such as a change of the trade state before a task sequence. So it publishes no
  transaction and sends no message for the trade.
- It cancels its tasks. A task that sends a message again stops sending, also if the message
  arrives or fails after the move. A task that watches the wallet or the state of the trade stops
  watching. A task that started an operation before the move, such as the broadcast of a
  transaction, still records the result of that operation.
- It reports no error of a canceled task. The trade keeps the error message it had.
- It stops its timeout.
- It stops listening for messages of the trade, direct and from the mailbox. A message in the
  mailbox stays there while the application runs. At the next start, the mailbox messages of failed
  trades are removed, as before.
- It stops waiting for the wallet to publish the mediated payout, and it no longer handles a
  signature of the peer that waits for the mediation result.

A trade that returns to the pending trades gets a new protocol, by the trader, by the
`unfailtrade` API call or by a dispute that the peer opens. The new protocol starts as after a
restart of the application: it sends again the messages that it sends again at a restart, and it
handles the messages in the mailbox.

## Compatibility

No message, persisted data or protocol change. The node does not handle the messages of the peer
for a failed trade, so the peer gets no acknowledgement and sends again what it sends again when an
acknowledgement is missing.

## Not covered

- A trade moved to the closed trades keeps its protocol, as before.
- Services outside the protocol keep following a failed trade, for example the automatic
  confirmation of XMR payments. A service that asks for the protocol of the trade gets the stopped
  protocol, which runs nothing, as long as the application runs.
- The move does not undo what was sent or published before it.
