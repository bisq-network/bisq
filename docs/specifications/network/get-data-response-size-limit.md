# Size limit of a GetDataResponse

## Scope

A node answers a `GetDataRequest` with the data of its P2P storage that the requester does not have:
append-only payloads and protected storage entries. This specification defines how the size limit
of such a response decides which items it holds, and when the response is marked as truncated, so
that the requester repeats its request. It does not define which data a node stores, and not the
limits for the number of items in a response.

## Rules

- The size limit of a response is 60% of the largest message (10 MB), or the value of the option
  `p2pDataStorageMaxSize`. The append-only payloads get 25% of it, the protected storage entries
  75%.
- The limit applies to the items of the priority LOW. These are all items but the account age
  witnesses and signed witnesses (MID) and the filters, alerts and dispute agents (HIGH). Items of
  the priorities MID and HIGH do not count for the limit.
- The LOW items are added in the order of the storage while their total size stays within the
  limit. The first item that exceeds the limit and all items after it are left out, and the response
  is marked as truncated. The requester then repeats its request with the hashes of the items that it
  has received.
- An item which alone is larger than the limit fits into no response. The node leaves it out of
  every response. It does not count for the limit, the items after it are added as without it, and
  it does not mark the response as truncated.
- Trade statistics are LOW items which are added after the other LOW items, and only if those did
  not exceed the limit.

## Rationale

The limit keeps a response below the largest message that a node reads. A response is built from
all stored items that the requester lacks, and any peer can add items to the storage.

An item above the limit made the check of the limit fail at this item: the node left it out together
with all items after it and marked the response as truncated. The requester never got the hash of
that item, so each repeated request had the same result. One signed offer of 5 MB, whose extra data
has no size limit, so kept all offers and mailbox messages behind it in the storage order out of
the responses of a seed node. Its sender can choose its hash so that it comes first, and a node
that started without data then ended with an empty offer book after its 30 requests.

Leaving the item out without a mark is the only result that ends the requests: no later response
can hold it. An honest item is far below the limit. An offer with 36 accepted countries and 1500
characters of extra information has 3.6 kB, the limit for the protected storage entries is 4.7 MB.

## Compatibility

No message and no persisted data change. Only the node that answers a request changes, for
requesters of all versions. An item above the limit was not part of a response before either. A
node of an earlier version still answers as described in the rationale.

## Known gaps

- Items of the priorities MID and HIGH do not count for the limit, and any peer can add large ones
  to the storage: an entry of a payload type with the priority HIGH, as only the nodes that use a
  filter, an alert or a dispute agent check its privileged signature, and a signed witness, as the
  storage checks neither its signature nor the size of its key fields. A few such items can make a
  response larger than 10 MB. The requester does not read a message above 10 MB and closes the
  connection ([`received-messages.md`](received-messages.md)), so it gets no data from this node.
- The storage has no size limit of its own for an item. A single message above 200 kB is dropped,
  but an element of a bundle of messages and an entry of a `GetDataResponse` are not checked.
  Several entries which are large but below the limit of a response each use up most of one
  response.
- The number of requests of a requester is limited, to 30 for a start. Enough items in front of the
  honest ones in the storage order, large or small, use up these requests. A node has no quota for
  the items of one owner.
