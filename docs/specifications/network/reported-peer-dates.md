# Reported peer dates

## Scope

This specification defines the date of a peer in the peer lists of a BISQ 1 node: the reported peers
and the persisted peers, which the node fills with the peers that other nodes report during peer
exchange.

## Rules

1. A node uses the date of a peer only to age and to order its peer lists. A peer whose date is more
   than 14 days old is removed, and among the reported and persisted peers, data requests try peers
   with a newer date first.
2. A peer which another node reports during peer exchange, in a request or in a response, gets the
   local time of reception as its date. The date sent by the other node is not used. A peer which is
   already in a peer list keeps its entry and date when it is reported again.
3. When the persisted peer list is read, each date is limited to the range from 0 to the local time.
   A later date is replaced with the local time. A negative date is replaced with 0, so the peer is
   removed at the next age check.

## Rationale

- Rule 2: an honest node reports only peers which it has seen within the last 30 minutes, so the
  date it sends adds nothing to the time of reception.
  Repeated reports do not delay the removal of a peer: it is removed at the first age check more
  than 14 days after it entered the lists, and enters them again only with a later report.
- Rule 3: earlier versions stored the dates sent in requests. Limiting them when the list is read
  lets those peers age out as usual, and the age computation of rule 1 cannot overflow, as
  [`peer-timestamp-validation.md`](peer-timestamp-validation.md) requires for every peer-controlled
  timestamp.

## Compatibility

No message and no persisted data format change. Peers reported in a request get the local time as
their date, as peers reported in a response already did. A persisted peer list written by an
earlier version is limited when it is read and written back at the next persistence.

## Not covered

Other rules for the peer lists.
