# Sender key of dispute messages

## Scope

Three dispute messages carry the signature public key of their sender: the message that opens a
dispute (OpenNewDisputeMessage), the message with which the agent creates the dispute of the other
trader (PeerOpenedDisputeMessage), and the message with the result (DisputeResultMessage). This
specification defines how a node checks that key when such a message arrives, directly or from the
mailbox.

## Rules

- The sender sets the key: the opener's trader signature key in a new dispute, the agent's signature
  key in the dispute for the other trader and in a result.
- A node accepts the message only if the key is present and is the key that signed the sealed
  message. A message without the key or with another key is dropped before the dispute handling.
- The rule applies to every dispute, whatever the trade date, and to all three messages.
- The dispute handling then checks that the signer may send this message for the trade, for example
  that a result comes from the agent of the trade. That check is not part of this specification.

## Rationale

With the key in the message, the receiver can check that the content names the same sender that
signed the sealed message, as it checks the sender node address against the connection.

## Compatibility

Senders set the key from release 1.10.4 on. Until 1 September 2026, and afterwards for disputes of
trades from before that date, a missing key was accepted; for results it was always accepted. These
exceptions were removed: nodes before release 1.10.10 cannot trade anymore, and no dispute is expected
more than two months after the trade started.

In the wire format the field stays optional. A message without it is read and then dropped by the
check above.
