# Chat message order

## Scope

This specification defines the order and the times in which the Bisq v1 desktop shows the messages
of the trader chat and of the dispute chat, on the side of a trader and of a dispute agent.

## Stored order

A message carries the date of its sender's clock. The clocks of the two sides can differ, and either
clock can be changed during a chat. The stored list of a chat is in the order in which the node
added the messages: own messages and messages that the node creates itself when they are created,
received messages when they are processed. The node adds messages only at the end of this list. What
a user had seen when writing a message is therefore stored before that message, whatever the clocks
show.

## Receive time

The node records the local time at which it receives a message from the peer, on every path such a
message arrives: as a chat message directly or from the mailbox, as the message of a dispute result,
and as the messages of a dispute that the opener sent to the agent or that the agent sent to the
other trader. A receive time that the peer put into the message is replaced. Own messages and
messages that the node creates itself, such as system messages, have no receive time.

## Order and times

- Messages are shown in the stored order. No date and no receive time moves a message across a
  message that is not from the peer.
- Messages of the peer that follow each other without another message between them are shown in the
  order of their sender's dates. This orders messages that arrive together or out of order, for
  example from the mailbox after the node was offline, or at startup, when messages that arrived
  directly are processed before the ones from the mailbox.
- A message of the peer is a message with a receive time, or a message without one that is not a
  system message and whose sender is the other side. All messages of the peer in a chat come from
  one node: the trading peer in the trader chat, the agent on the side of a trader and the trader on
  the side of the agent in a dispute chat. A system message without a receive time, such as the
  rules message of the trader chat, is never ordered with the messages of the peer.
- A received message shows its receive time. A message without a receive time shows its date.
- The copy of the chat to the clipboard uses the same order and times.

The stored list keeps its order. Only the display is ordered.

## Compatibility

The receive time is a new field of the chat message (field 17), stored with the trade or the
dispute. Older versions ignore it and drop it when they store the trade or the dispute again. A
message stored by an older version, also after a downgrade, has no receive time and shows its date,
as before. Older versions stored the chat in the same order, so a chat stored by an older version is
shown by the same rule instead of by date. No hash or signature covers the field. The signature of a
dispute summary covers the text of a chat message, not its stored form.

## Not covered

- A message of the peer that arrives late, for example from the mailbox or after a slow connection
  to the peer, is shown where it arrived if an own message was written meanwhile, after messages
  that the peer wrote later. Its receive time shows when it arrived.
- Within messages of the peer that follow each other, the peer's dates decide the order. A peer that
  changes its clock or chooses its dates can change the order of these messages, but cannot move a
  message across an own message.
- A message that arrives while the node is still starting gets the time at which the node processes
  it.
