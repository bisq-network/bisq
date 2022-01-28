# Contributing to Bisq

Anyone is welcome to contribute to Bisq. This document provides an overview of how we work. If you're looking for somewhere to start contributing, check out [critical bugs](https://bisq.wiki/Critical_Bugs) or see [good first issue](https://github.com/bisq-network/bisq/issues?q=is%3Aopen+is%3Aissue+label%3A"good+first+issue") list.


## Communication Channels

Most communication about Bisq happens on [Matrix](https://bisq.chat), follow [instructions in the wiki](https://bisq.wiki/Matrix_bisq.chat) to join the Bisq space; there is also a [forum](https://bisq.community/) and a [subreddit](https://www.reddit.com/r/bisq/).

Discussion about code changes happens in GitHub issues and pull requests.

Discussion about larger changes to the way Bisq works happens in issues the [bisq-network/proposals](https://github.com/bisq-network/proposals/issues) repository. See https://bisq.wiki/Proposals for details.


## Contributor Workflow

All Bisq contributors submit changes via pull requests. The workflow is as follows:

 - Fork the repository
 - Create a topic branch from the `master` branch
 - Commit patches
 - Squash redundant or unnecessary commits
 - Submit a pull request from your topic branch back to the `master` branch of the main repository
 - Make changes to the pull request if reviewers request them and __**request a re-review**__

Pull requests should be focused on a single change. Do not mix, for example, refactorings with a bug fix or implementation of a new feature. This practice makes it easier for fellow contributors to review each pull request on its merits and to give a clear ACK/NACK (see below).


## Reviewing Pull Requests

Bisq follows the review workflow established by the Bitcoin Core project. The following is adapted from the [Bitcoin Core contributor documentation](https://github.com/bitcoin/bitcoin/blob/master/CONTRIBUTING.md#peer-review):

Anyone may participate in peer review which is expressed by comments in the pull request. Typically reviewers will review the code for obvious errors, as well as test out the patch set and opine on the technical merits of the patch. Project maintainers take into account the peer review when determining if there is consensus to merge a pull request (remember that discussions may have been spread out over GitHub, mailing list and IRC discussions). The following language is used within pull-request comments:

 - `ACK` means "I have tested the code and I agree it should be merged";
 - `NACK` means "I disagree this should be merged", and must be accompanied by sound technical justification. NACKs without accompanying reasoning may be disregarded;
 - `utACK` means "I have not tested the code, but I have reviewed it and it looks OK, I agree it can be merged";
 - `Concept ACK` means "I agree in the general principle of this pull request";
 - `Nit` refers to trivial, often non-blocking issues.

Please note that Pull Requests marked `NACK` and/or GitHub's `Change requested` are closed after 30 days if not addressed.


## Compensation

Bisq is not a company, but operates as a _decentralized autonomous organization_ (DAO).

Since our [Q1 2020 update](https://bisq.network/blog/q1-2020-update/) contributions are NOT eligible for compensation unless they are allocated as part of the development budget. Fixes for [critical bugs](https://bisq.wiki/Critical_Bugs) are eligible for compensation when delivered.
In any case please contact the team lead for development (@ripcurlx) upfront if you want to get compensated for your contributions.

For any work that was approved and merged into Bisq's `master` branch, you can [submit a compensation request](https://bisq.wiki/Making_a_compensation_request) and earn BSQ (the Bisq DAO native token). Learn more about the Bisq DAO and BSQ [here](https://bisq.wiki/Introduction_to_the_DAO).


## Style and Coding Conventions

### Configure Git user name and email metadata

See https://help.github.com/articles/setting-your-username-in-git/ for instructions.

### Write well-formed commit messages

From https://chris.beams.io/posts/git-commit/#seven-rules:

 1. Separate subject from body with a blank line
 2. Limit the subject line to 50 characters (*)
 3. Capitalize the subject line
 4. Do not end the subject line with a period
 5. Use the imperative mood in the subject line
 6. Wrap the body at 72 characters (*)
 7. Use the body to explain what and why vs. how

*) See [here](https://stackoverflow.com/a/45563628/8340320) for how to enforce these two checks in IntelliJ IDEA.

See also [bisq-network/style#9](https://github.com/bisq-network/style/issues/9).

### Sign your commits with GPG

See https://github.com/blog/2144-gpg-signature-verification for background and
https://help.github.com/articles/signing-commits-with-gpg/ for instructions.

### Use an editor that supports Editorconfig

The [.editorconfig](.editorconfig) settings in this repository ensure consistent management of whitespace, line endings and more. Most modern editors support it natively or with plugin. See http://editorconfig.org for details. See also [bisq-network/style#10](https://github.com/bisq-network/style/issues/10).

### Keep the git history clean

It's very important to keep the git history clear, light and easily browsable. This means contributors must make sure their pull requests include only meaningful commits (if they are redundant or were added after a review, they should be removed) and _no merge commits_.

### Additional style guidelines

See the issues in the [bisq-network/style](https://github.com/bisq-network/style/issues) repository.


## See also

 - [contributor checklist](https://bisq.wiki/Contributor_checklist)
 - [developer docs](docs#readme) including build and dev environment setup instructions
 - [project management process](https://bisq.wiki/Project_management)

