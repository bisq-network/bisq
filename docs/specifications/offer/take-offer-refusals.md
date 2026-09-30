# Take offer refusals

## Scope

What the taker is told when the maker answers an availability request with anything other than
"available", and what the client remembers about the maker afterwards.

## Rules

The maker's answer is the reason, and the taker sees it. "The offer was already taken by another
trader" is shown only when the maker said so, or when no answer exists. Every other answer has its
own localised message, for example: the maker has put the taker on their ignore list, the trade
price is outside the maker's tolerance, or the maker has no market price. A new kind of answer
needs a new message. The maker's own English wording is never shown to the user.

An answer that this version does not know counts as an unknown failure.

The reason belongs to the current answer only. When the offer state changes for any other reason,
for example for a new request or after a timeout, the previous reason is removed.

A refusal produces one message, both when the take screen opens and when the user clicks to take
the offer. The take screen reports it, and no second popup repeats the maker's text.

A timeout is not an answer. It is still reported as the maker being offline.

## A maker who ignores the taker

A maker's ignore list is private to the maker, and the offer book does not show it. The node learns
that a maker has closed their offers to it only when one of its availability requests is answered
"user ignored". That answer applies to all offers of that maker, not only to the offer in the
request.

After such an answer, all offers of that maker are treated like other offers that the user cannot
take: they are shown dimmed, a click explains the reason, and they are hidden when the offer book
shows only "Offers matching my accounts". The API refuses getoffer and takeoffer for them with the
reason IS_IGNORED_BY_MAKER.

The node keeps this in memory for one hour after the last refusal. After that time it asks the
maker again when the user takes one of the maker's offers. An "available" answer from that maker
ends it at once. The node does not send requests to find such makers; it only records answers to
requests that the user made.
