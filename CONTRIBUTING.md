# Contributing to Bisq

Read on to find out if taking part in the development of Bisq is for you and how to do it!

## Are you "the guy"?

Java God, Bitcoin Native, Marketing Genius, Cypherpunk, Testing Machine, or simply someone who likes the idea of Bisq? Anyone is welcome to contribute! However, Bisq is no classic organization, there is no boss, there is no working hours and there is no classic payment. In short, Bisq is not for everyone.

**What is there to work on?** You have to be able to find and work a problem on your own. In general, we do not assign stuff to people, there is no specification to be implemented, there is no given way forward. You yourself have to find, choose and pick something to work on. If you get stuck, we are keen to help, but please do not wait for us to take your hand. Also, you can spend as much (or little) time as you want and when you want to - Bisq still is an open source project.

**Will you get paid?** Well, yes and no. With Bisq, there is no classic funding or investors, so there is no payout of fiat money.
- Yes, you can ask for compensation. If granted, you receive BSQ, a colored bitcoin token. Sell your BSQ and you get your fiat eventually.
- No, because the BSQ market is still bootstrapping. The more correct way to see payment is "compensation". By earning BSQ, you invest in the Bisq project (as you might in a more traditional startup). But make no mistake, the DAO has only been operating for a few months now. Over time, the BSQ market should be able to stay ahead of compensation requests.

**Skills?**
If you want to develop code, we expect you to know your ways around Git and Java. Then comes testing, JavaFX, Gradle, Github, ... If you want to do something else you are good at: marketing, user support, management process optimizations, you are most welcome as well!
If you are good at something Bisq does not even realize it is in need for, well, come on in!

**Carreer?** There is no boss in Bisq - there is only stakeholders. Stakeholders have invested their time, gained some experience with the project and thus, are granted influence on the project accordingly. As soon as you receive your first couple of BSQ, you are a stakeholder too.

Start with something small, get yourself familiar with how things are done. Like it? Take on bigger tasks, be it development work, [roles](https://github.com/bisq-network/roles/issues) or other things you believe Bisq is in need for.

All in all, there is no boss, there is no annual raise of salary, there is nobody to take your hand, there is just stakeholders and their passion for the Bisq project.




Still here? Nice. You are hired. On to the grind.

## Getting started

Join Gibhub if you haven't already, fork the Bisq repository, configure your local git ([username](https://help.github.com/articles/setting-your-username-in-git/), [commit signing](https://help.github.com/articles/signing-commits-with-gpg/)), clone your Bisq-fork to disk and do a compile run `./gradlew build`.

Now you are ready to roll. Pick something off the [Good First Issue List](https://github.com/bisq-network/bisq/issues?q=is%3Aopen+is%3Aissue+label%3Ais%3Apriority), a [priority task](https://github.com/bisq-network/bisq/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22) or pick any other [issue](https://github.com/bisq-network/bisq/issues), do some testing, spot a bug, report it and fix it, maybe there is something in the UI that needs cosmetic work. The possibilities are endless. However, make sure the task you pick is of some importance, otherwise it might cause some inconvenience later on.

Now that you have picked yourself something to work on, be vocal about it! Claim good first issues, or any issues, by simply leaving a comment saying that this is being worked on, join [Keybase](https://keybase.io/team/bisq). Once there, register your github with keybase, introduce yourself in the channel *#introductions*, get help in *#dev-onboarding* if your setup fights you, use *#dev* to see if the task you picked is a good way forward (you do not have to do that for good first issues though) and join other channels if you want to. Furthermore, check our [event calendar](https://bisq.network/calendar) and tune in on [dev-calls](hhttps://github.com/bisq-network/events/issues?q=Dev-Call), growth-calls and daily standup calls.

Go on and do the actual work. And do not forget to enjoy yourself!


## Pull request rules

Please honor the list of rules below for your pull request to get merged. Due to very limited resources, we are forced to enforce these quite rigorously.

### Honor Git best practice
 - Create a topic branch off master
 - Commit little, commit often
 - Maintain a clean commit history (no merge commits!, use rebase and force-push if necessary)
 - Use meaningful commit subjects AND description
   - how did stuff work before you touched it, what has been bad back then
   - how does the stuff work now and why is that better
 - Do so in a well-formed manner (from https://chris.beams.io/posts/git-commit/#seven-rules)
   1. Separate subject from body with a blank line
   2. Limit the subject line to 50 characters
   3. Capitalize the subject line
   4. Do not end the subject line with a period
   5. Use the imperative mood in the subject line
   6. Wrap the body at 72 characters
   7. Use the body to explain what and why vs. how

### Respect Bisq's coding style
 - The .editorconfig settings in this repository ensure consistent management of whitespace, line endings and more. Most modern editors support it natively or with plugin. See http://editorconfig.org for details. See also bisq-network/style#10.
 - Also, please honor the issues in the [bisq-network/style](https://github.com/bisq-network/style/issues) repository
 - Do not commit import reorgs or any other non-changes!


### Test your code!
 - Create automated tests for your work. JUnit is available. If you are fixing a bug, create a test that demonstrates the bug by failing. Then fix the bug.
 - If automated testing is not feasible (we know it can be hard - working on it ...), provide a textual testing procedure (which we can include in our manual testing checklist): setup, prerequisits, steps, expected results
 - If that is not possible either, provide some info (numbers, screenshots, testing procedure) on how you tested your stuff
 - If all is lost, state why you have not been able to do the above.

### Create a pull request
- Pull requests have to be focused on a single change. Do not mix unrelated stuff.
- Use meaningful title and description
  - provide some context, how did stuff work before, how does it work now, why is that better
  - are there some introduced/mitigated risks, maybe for backwards compatibility
  - how you tested your stuff (so we can do so as well if we feel so)
  - and of course, reference issues (if applicable) with the use of [keywords](https://help.github.com/en/github/managing-your-work-on-github/closing-issues-using-keywords)
- Wait for your pull request to be reviewed

### Pull request review prodecure
Bisq follows the review workflow established by the Bitcoin Core project. The following is adapted from the [Bitcoin Core contributor documentation](https://github.com/bitcoin/bitcoin/blob/master/CONTRIBUTING.md#peer-review): 
 - `ACK` means "I have tested the code and I agree it should be merged";
 - `NACK` means "I disagree this should be merged", and must be accompanied by sound technical justification. NACKs without accompanying reasoning may be disregarded;
 - `utACK` means "I have not tested the code, but I have reviewed it and it looks OK, I agree it can be merged";
 - `Concept ACK` means "I agree in the general principle of this pull request";
 - `Nit` refers to trivial, often non-blocking issues.

In case you receive a NACK and/or `change requested`
 - react within 30 days, or else, your pull request will be rejected automatically
 - **re-request a review** after adressing a change request (because we almost always only look at "review required" pull request because of resource constraints)

### Compensation

Your pull request got merged? great, congrats and thanks for making Bisq more better.

For any work merged into Bisq's `master` branch, you can [submit a compensation request](https://docs.bisq.network/dao/phase-zero.html#how-to-request-compensation) and earn BSQ (the Bisq DAO native token). Learn more about the Bisq DAO and BSQ [here](https://docs.bisq.network/dao/phase-zero.html).


## Further reading/other resources worth checking

 - tech session on [youtube](https://www.youtube.com/watch?v=ulmUVh3XjRg&list=PLFH5SztL5cYOtcg64PntHlbtLoiO3HAjB)
 - dev-calls on [youtube](https://www.youtube.com/watch?v=YnTA3p-5v00&list=PLFH5SztL5cYN1m9v_NvpXxvP7_XIKF3At)
 - [contributor checklist](https://docs.bisq.network/contributor-checklist.html)
 - [developer docs](docs#readme) including build and dev environment setup instructions

