# Chat message order

## Scope

This specification defines the order and the times in which the Bisq v1 desktop shows the messages
of the trader chat and of the dispute chat, and which of them it shows as messages of the peer, on
the side of a trader and of a dispute agent.

## Stored order

A message carries the date of its sender's clock. The clocks of the two sides can differ, and either
clock can be changed during a chat. The stored list of a chat is in the order in which the node
added the messages: own messages and messages that the node creates itself when they are created,
received messages when they are processed. The node adds messages only at the end of this list. What
a user had seen when writing a message is therefore stored before that message, whatever the clocks
show.

## Receive time

The node records the local time at which it processes and stores a message from the peer, on every
path such a message arrives: as a chat message directly or from the mailbox, as the message of a
dispute result, and as the messages of a dispute that the opener sent to the agent or that the agent
sent to the other trader. A receive time that the peer put into the message is replaced. A copy of a
message that the node has stored already, for example the same message directly and from the
mailbox, is not stored again and does not change the receive time. A copy of a dispute result is
only acknowledged and not applied again, also after the cleanup of sensitive data removed the
message of the result from the chat, as the stored result keeps it. Own messages and messages that
the node creates itself, such as system messages, have no receive time.

The node processes a received message only when it is ready: its services are initialized, the P2P
network is bootstrapped, the wallet download is complete and it has enough Bitcoin peers to
broadcast a transaction. The last condition can become false at any time. A message that arrives
meanwhile waits and gets the time at which it is processed: in a dispute chat when the node is ready
again, in the trader chat when the next message for the node arrives, or, for a message from the
mailbox, at the next start. The order is not affected, as the message is shown only when it is
stored.

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

## Author

A message of the peer, as defined above, is shown as a message of the peer: in the style of the
peer, on the side of the peer and with the label "Received" and the name of the peer. Every other
message that is not a system message is shown as an own message, with the label "Sent". A received
message is therefore never shown as an own message, whatever sender the peer put into it. The side
of a message is the side of its author: the client of the chat, which is the taker in the trader
chat and the trader in a dispute chat, or the other side. The copy of a dispute chat to the
clipboard and the text export of the disputes of an agent name the author by the same rule.

A system message is shown in the style of a system message. A message that the peer sends on its
own, a chat message or the message of a dispute result, is a message of the peer and never a system
message, whatever the peer put into it, in the trader chat and in a dispute chat. The notice of a
trader about a change of the disputed trade therefore shows as a message of that trader. The
messages that come with a dispute keep their flags, such as the opening message of the trader and
the message of the agent in the dispute that it sends to the other trader. The node also clears the
displayed flag of a message that the peer sends on its own, as only the node knows whether it showed
the message.

The summary that an agent signs for an option trade includes the second message of the dispute only
if the agent's node added it as a system message: the mediator's price information, or at the refund
agent the mediator's summary that the opener's request carries or a notice of the refund agent's
node. A message received by this version is never included. The agent's node adds the mediator's
summary from the opener's request only to a refund dispute.

## Compatibility

The receive time is a new field of the chat message (field 17), stored with the trade or the
dispute. Older versions ignore it and drop it when they store the trade or the dispute again. A
message stored by an older version, also after a downgrade, has no receive time and shows its date,
as before. Older versions stored the chat in the same order, so a chat stored by an older version is
shown by the same rule instead of by date. The field is also part of a chat message that a node
sends, as the other local fields of the message are, for example when an agent sends the dispute of
the opener again. A receiver always replaces it, and older versions ignore it in a received message.
No hash or signature covers the field. The signature of a dispute summary covers the text of a chat
message, not its stored form.

A message stored by an older version has no receive time, so its sender decides its author, as
before. A message that an older version received as a system message in a chat stays one, and in a
dispute stored by an older version a received system message counts as one that the node added, so
the summary of an option trade includes it as before. The format of messages and of stored data does
not change. The node only stops trusting the sender of a received message, the system flag and the
displayed flag of a message that the peer sends on its own, and, in the summary of an option trade,
the system flag of a received message, and it adds the mediator's summary from the opener's request
only to a refund dispute.

## Not covered

- A message of the peer that arrives late, for example from the mailbox or after a slow connection
  to the peer, is shown where it arrived if an own message was written meanwhile, after messages
  that the peer wrote later. Its receive time shows when it arrived.
- Within messages of the peer that follow each other, the peer's dates decide the order. A peer that
  changes its clock or chooses its dates can change the order of these messages, but cannot move a
  message across an own message. The receive times shown for these messages can then go backwards.
- The trader chat processes a waiting message only when the next message for the node arrives, not
  when the node is ready again. A message that arrived directly and still waits when the node stops
  is lost, as the sender saw it arrive. Reacting to the readiness, as the dispute chats do, would
  remove most of this.
- The messages that come with a dispute keep their flags, as the opening message of the trader and
  the message of the agent are system messages. Either side can therefore add a message that the
  other side's chat shows as a system message, the trader in its dispute and the agent in the
  dispute it sends to the other trader.
- The refund agent's node shows the mediator's summary that the opener's request carries as a
  system message of its own, and its summary for an option trade includes it, as before. The opener
  can change that text.
- The price information is included only when it is the second message. When the price feed had no
  price while the dispute arrived, it is added later or not at all, and the summary of an option
  trade does not include it.
- The counters of unread messages, which in a dispute also decide whether it is shown as open and
  whether a closed dispute is reopened at the start, and the agent's check of the health of a
  dispute still decide by the sender flag that the peer put into a message, and for the messages
  that come with a dispute also by the displayed flag. A peer can change only how its own messages
  count.
