# Take offer refusals

## Scope

What the taker is told when the maker answers an availability request with anything other than
"available".

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
