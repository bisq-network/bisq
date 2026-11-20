# Offers with the same ID in the offer book

## Scope

The maker chooses the ID of its offer, and the P2P storage keys offers by the hash of their payload,
so two offers can have the same ID. This specification covers how the offer book of the desktop
application lists such offers.

## Rules

- A new offer replaces a listed offer with the same ID only if both have the same owner key, as after
  the owner edited the offer.
- An offer with the ID of a listed offer of another owner is listed next to that offer.
- A removed offer takes out the listed offer with its ID and its payload hash.

## Rationale

The storage accepts an offer, its refresh and its removal only from the owner of the key in the
offer. Replacing every listed offer with the same ID let anyone take an offer of another maker out of
the offer book of the nodes that received the copy later, by publishing an offer with the same ID
(Woodser B6 in GHSA-vmx9-8xwh-hmcw).

## Not covered

Other parts of the application that look up an offer by its ID only, for example to take it, are not
covered.
