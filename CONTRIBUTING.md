# Contributing to Bisq

Read on to find out if taking part in the development of Bisq is for you and how to do it!

## Are you "the guy"?

Java God, Bitcoin Native, Marketing Genius, Cypherpunk, Testing Machine, or simply someone who likes the idea of Bisq? Anyone is welcome to contribute! However, Bisq is no classic organization, there is no boss, there is no working hours and there is no classic payment. In short, Bisq is not for everyone.

**What is there to work on?** You have to be able to find and work a problem on your own. In general, we do not assign stuff to people, there is no specification to be implemented, there is no given way forward. You yourself have to find, choose and pick something to work on. If you get stuck, we are keen to help, but please do not wait for us to take your hand. Also, you can spend as much (or little) time as you want and when you want to - Bisq still is an open source project.

**Will you get paid?** Well, yes and no. With Bisq, there is no classic funding or investors, so there is no payout of fiat money.
- Yes, because there is the DAO. The DAO allows for compensation requests to be filed and voted on. If such a voting goes in your favor, you receive BSQ, a colored bitcoin token. Sell your BSQ and you get your fiat eventually.
- No, because the market is still very small. We can sustain a few full-timers (paying rent, tax, small stuff) but do not expect to get rich fast and easy just now.

The more correct way to see payment is "compensation". By earning BSQ, you invest in the Bisq project (just like other startups do it). But make no mistake, the DAO has only been operating for a few months now. Eventually, Bisq will create serious revenue.

**Tools?** If you want to develop code, we expect you to know your ways around Git and Java. Then comes testing, JavaFX, Gradle, Github, ... As for non-functional skills, we love to see people knowing their ways around P2P networks, cryptography, security, privacy, ...

**Carreer?** Start small, go bigger. If we like your work, we might just ask you to take on a more official role. Maybe someday you are the guy improving this document (again).




Still here? Nice. You are hired. On to the grind.

# Getting started

Join Gibhub if you haven't already, fork the Bisq repository, configure your local git ([username](https://help.github.com/articles/setting-your-username-in-git/), [commit signing](https://help.github.com/articles/signing-commits-with-gpg/)), clone your Bisq-fork to disk, do a compile run `./gradlew build`.

Now you are ready to roll. Pick something off the [Good First Issue List](https://github.com/bisq-network/bisq/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22), pick any other issue, do some testing, spot a bug, report it and fix it, maybe there is something in the UI that needs cosmetic work.

Now that you have picked yourself something to work on, be vocal about it! Claim good first issues, or any issues, by simply leaving a comment saying that this is being worked on, join [Keybase](https://keybase.io/team/bisq). Once there, introduce yourself in *#introductions*, get help in *#dev-onboarding* if your setup fights you, use *#dev* to see if the task you picked is a good way forward (you do not have to do that for good first issues) and join other channels if you want to.

Go on and do the actual work.




This document provides an overview of how we work. If you're looking for somewhere to start contributing, see the [good first issue](https://github.com/bisq-network/bisq/issues?q=is%3Aopen+is%3Aissue+label%3A"good+first+issue") list.


## Communication Channels

Most communication about Bisq happens in the [Bisq Slack workspace](https://bisq.network/slack-invite).

Discussion about code changes happens in GitHub issues and pull requests.

Discussion about larger changes to the way Bisq works happens in issues the [bisq-network/proposals](https://github.com/bisq-network/proposals/issues) repository. See https://docs.bisq.network/proposals.html for details.


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

Bisq is not a company, but operates as a _decentralized autonomous organization_ (DAO). For any work merged into Bisq's `master` branch, you can [submit a compensation request](https://docs.bisq.network/dao/phase-zero.html#how-to-request-compensation) and earn BSQ (the Bisq DAO native token). Learn more about the Bisq DAO and BSQ [here](https://docs.bisq.network/dao/phase-zero.html).


## Style and Coding Conventions

### Configure Git user name and email metadata

See https://help.github.com/articles/setting-your-username-in-git/ for instructions.

### Write well-formed commit messages

From https://chris.beams.io/posts/git-commit/#seven-rules:

 1. Separate subject from body with a blank line
 2. Limit the subject line to 50 characters
 3. Capitalize the subject line
 4. Do not end the subject line with a period
 5. Use the imperative mood in the subject line
 6. Wrap the body at 72 characters
 7. Use the body to explain what and why vs. how

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

 - [contributor checklist](https://docs.bisq.network/contributor-checklist.html)
 - [developer docs](docs#readme) including build and dev environment setup instructions

