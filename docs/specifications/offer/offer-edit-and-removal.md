# Offer edit and removal

## Scope

This specification defines the completion contracts of editing and removing open offers, and the
preconditions they impose on the destructive flows built on top of them: replacing the wallet,
emptying the wallet and republishing a BSQ swap offer under a new id.

## Edit

Editing deactivates the offer on the offer book, applies the edit locally and republishes the
edited offer. A failed deactivation reports the failure and clears the edit state, so the offer
does not stay stuck in edit mode. A publish failure before the P2P network is bootstrapped does
not fail the edit: the edit succeeds once the offer is edited locally, and the offer is published
by the republish that follows the bootstrap. An edit-success report therefore means the offer is
edited locally and queued for publication, not that it is already visible on the offer book.

## Removal

A successful removal has removed the offer locally, marked it canceled and enqueued the removal
broadcast; it does not confirm that the network has processed it. A failed removal leaves the
offer locally persisted and alive on the offer book, where the periodic refresh keeps it from
expiring.

Removing all open offers aggregates the individual outcomes: the result is success only when
every single removal succeeded, and the aggregated outcome is decided only after a drain delay
which gives every removal the chance to report. Any failure yields the error outcome, naming the
offers which could not be removed.

## Preconditions of destructive flows

Replacing the wallet from seed words and emptying the wallet promise the user that the open
offers are removed first. Both must abort on the error outcome of the aggregated removal, because
proceeding would leave offers on the network whose wallet no longer backs them. An offer which is
currently being edited refuses removal, so these flows abort in that case as well.

A BSQ swap offer whose proof of work became invalid is replaced by a republish under a mutated
id. The replacement may only be minted and published once the removal of the old offer succeeded;
otherwise the old offer stays alive next to the replacement as a duplicate. On a failed removal
the old offer stays in the local list and the next activation retries the redo.

Fire-and-forget removal, reporting a failure only to the log, is acceptable only for callers
whose subsequent behaviour does not depend on the removal having succeeded.
