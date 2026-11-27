# Contributing to Bisq

Bisq welcomes bug reports and feedback. If you are new to Bisq, please talk to the relevant maintainers before starting development work. Bisq has paused taking on new contributors for paid development and routinely reviewing their pull requests. Maintainers may make exceptions for high-value contributions. See the [announcement on contribution rules and compensation](https://bisq.network/blog/open-source-development-in-the-age-of-ai/).

Report any suspected security issue privately using **Report a vulnerability** on the [Security Advisories page](https://github.com/bisq-network/bisq/security/advisories). Keep security reports and proposed fixes out of public issues, pull requests, and discussions. See the [security policy](SECURITY.md) for what to include in a private report.


## Communication Channels

Most communication about Bisq happens on [Matrix](https://bisq.chat), follow [instructions in the wiki](https://bisq.wiki/Matrix_bisq.chat) to join the Bisq space; there is also a [forum](https://bisq.community/) and a [subreddit](https://www.reddit.com/r/bisq/).

Discussion about non-security code changes happens in GitHub issues and pull requests.

Discussion about larger changes to the way Bisq works happens in issues the [bisq-network/proposals](https://github.com/bisq-network/proposals/issues) repository. See https://bisq.wiki/Proposals for details.


## Contributor Workflow

All Bisq contributors submit changes via pull requests. The workflow is as follows:

 - Fork the repository
 - Create a topic branch from the `master` branch
 - [Sign off on and GPG-sign every commit](#sign-off-on-and-gpg-sign-every-commit)
 - Squash redundant or unnecessary commits, preserving contributors' sign-offs and GPG-signing the resulting commits
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

Bisq's development teams work within budgets set by project priorities. Before starting work for which you expect payment, ask the relevant maintainers whether the work is a priority, whether someone can review it, and whether funding is available.

For agreed, budgeted work, you can [request compensation in BSQ](https://bisq.wiki/Making_a_compensation_request). The [Bisq DAO](https://bisq.wiki/Introduction_to_the_DAO) decides whether to approve each request. A merged pull request or a [critical bug fix](https://bisq.wiki/Critical_Bugs) does not guarantee payment.


## Style and Coding Conventions

### Configure Git user name and email metadata

See https://help.github.com/articles/setting-your-username-in-git/ for instructions.

### Write well-formed commit messages

From https://cbea.ms/posts/git-commit/#seven-rules:

 1. Separate subject from body with a blank line
 2. Limit the subject line to 50 characters (*)
 3. Capitalize the subject line
 4. Do not end the subject line with a period
 5. Use the imperative mood in the subject line
 6. Wrap the body at 72 characters (*)
 7. Use the body to explain what and why vs. how

*) See [here](https://stackoverflow.com/a/45563628/8340320) for how to enforce these two checks in IntelliJ IDEA.

For each nontrivial commit, include a body that helps a future reader understand the change without the pull request or chat history:

- Explain the problem or previous behavior and its consequences.
- Explain what changed and why that approach was chosen.
- Include relevant constraints, tradeoffs, or compatibility effects when supported by the change.
- Describe behavior covered by tests when it helps explain the change.
- Keep the explanation proportional to the change. A subject alone is sufficient for a self-explanatory trivial change.
- Do not invent motivations, test results, or other details. Avoid simply listing changed files or repeating the diff.

See also [bisq-network/style#9](https://github.com/bisq-network/style/issues/9).

### Sign off on and GPG-sign every commit

Each commit you submit must include a `Signed-off-by` trailer and a GPG signature. Use `git commit -s -S` to add both.

- `-s` adds `Signed-off-by: Your Name <you@example.org>` to the commit message using your Git committer name and email. By signing off, you certify that your contribution meets the terms of the [Developer Certificate of Origin 1.1](DCO).
- `-S` signs the commit with your GPG key. See [GitHub's instructions for signing commits](https://docs.github.com/en/authentication/managing-commit-signature-verification/signing-commits).

If your latest local commit is missing either, run `git commit --amend --no-edit -s -S`.

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

