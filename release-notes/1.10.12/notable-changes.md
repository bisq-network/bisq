# Bisq 1.10.12 Notable Changes

> **Draft. The maintainer completes it before the release** (see the list at the top of `full-release-notes.md`):
> it covers `release-candidate-v1.10.12` through `2b4b82de6c`. The commits are named by their hashes on that branch;
> link them to `https://github.com/bisq-network/bisq/commit/<hash>`.

Bisq 1.10.12 adds stablecoins and custom payment methods to the fiat markets, computes the BSQ price from recent
trades, makes mediated payouts complete without a second click and hardens the trade protocol, the dispute handling,
the P2P network and the DAO against malformed or hostile data. It lowers the fee rates, requires a password for the
API and switches the Java runtime to Amazon Corretto.

## Stablecoins

- Stablecoins are payment methods of their peg currency, now USD: one coin pays one USD of the trade volume, whatever
  its market price. The existing altcoin markets of stablecoins, such as USDT-E, stay as they are.
- 18 methods: `USDT_ERC20`, `USDT_TRC20`, `USDT_BEP20`, `USDT_SPL`, `USDC_ERC20`, `USDC_SPL`, `USDC_BEP20`,
  `USDS_ERC20`, `USDE_ERC20`, `DAI_ERC20`, `RLUSD_ERC20`, `USDG_ERC20`, `USDG_SPL`, `PYUSD_ERC20`, `PYUSD_SPL`,
  `U_ERC20`, `U_BEP20` and `USDF_ERC20`, on Ethereum, Tron, BNB Smart Chain and Solana.
- The account holds the receiving address. It is checked for its network: EIP-55 checksums on Ethereum and BNB Smart
  Chain, and new validators for Tron and Solana.
- The trade period is 24 hours. The trade limit is the highest limit class, independent of account age and signing,
  and a taker does not need the account age witness of the maker. The local trade limit of the user still applies.
- The trade steps show the coin, the network and the token contract. The account creation and the creation or taking
  of an offer show the risk that the issuer freezes the coins.
- Revealed payment account data is accepted only if its payment method is the one which the peer named in the
  contract, for the buyer and for the seller. The buyer also refuses seller account data of another stablecoin rail
  than the one of the offer. The buyer step, the trade details and the API hide such data of trades of earlier builds
  of this release, and the API refuses the payment confirmation for such a trade.
- Versions up to 1.10.11 show such offers as USD offers with an unknown payment method and cannot take them. They
  cannot read accounts, trades, contracts or disputes with stablecoin data, so mediators and refund agents update
  before the first such trade.

Related commits: `b61c4c9d2f`, `4318ef9ed2`, `ef1b806f4f`, `6729ea9b98`, `52abfd8a37`, `6b14531b80`, `8b99ddca82`,
`4378608316`.

## Custom Payment Method

- The payment method "Custom" (`CUSTOM`) covers payment methods which Bisq does not support, in every fiat currency.
  The account holds a free name of up to 20 characters and free account data of up to 1000 characters.
- The offer carries the name in its extra data. Only accounts with the same normalized name and currency match.
  Names of Bisq payment methods, also of removed ones, are refused.
- The trade period is 4 days. The trade limit is the limit of high-risk methods, at most 0.01 BTC, in every currency
  and independent of the DAO trade limit.
- The user confirms warnings: nobody vets the method, chargebacks are possible and mediators may not be able to help.
- The seller's account data is mandatory in the deposit message of a custom trade.
- Versions up to 1.10.11 show such offers with an unknown payment method and cannot take them.

Related commits: `a8906941a8`, `a68bfacb9c`, `ef34634a1a`, `4c1568565c`, `b040b6f5e5`, `bc480f6a83`, `dfdedbbecd`.

## Account Creation and Market Views

- National currency accounts are created with three selectors: the payment method list, a searchable stablecoin list
  and a checkbox for a custom payment method.
- The buy and sell offer books get the payment method filter "Show stablecoins only". Market > Trades gets the
  selector "Fiat and stablecoins / Fiat only / Stablecoins only"; custom payment methods count as fiat.
- A popup at the start introduces the stablecoins and the custom payment method once, with their risks.
- The API cannot create stablecoin or custom accounts, but it can use accounts created in the desktop application.
- The API `takeoffer` refuses a payment account which does not match the offer in payment method, currency, country
  or bank, as the desktop application does.
- After a new account was canceled, choosing the same payment method again shows its form again. Before, the form
  stayed empty.
- Market > Trades keeps the selected payment method filter when the same currency is selected again.

Related commits: `0b533c72e8`, `b1d9a1a185`, `5f493719be`, `449e892917`, `5eb4db3d70`, `5cd1fe6ba7`, `e25b682eb5`,
`75d9abfb9b`.

## BSQ Price and BSQ Offer Book

- No price feed delivers BSQ. The BSQ market price is now the volume weighted average price of the BSQ trades of the
  24 hours up to the newest BSQ trade, from at most the newest 200 trade statistics. Before, it was the price of the
  newest BSQ trade. The label says "Average price of recent Bisq trades", and the DAO dashboard explains the source.
- A trade statistic which arrives late no longer replaces the price of a newer trade, in every currency. Statistics
  received in one bundle update the prices once.
- The BSQ offer book and its chart hide offers of other users whose price is worse for the taker than the 90-day
  average BSQ trade price times a factor: sell offers above the average times the factor, buy offers below the
  average divided by it. The factor is set in Settings > Preferences, DAO options: default 2, above 1 up to 1000,
  where 1000 turns the range off. If every offer is hidden, a hint links to the setting and says that any peer can
  publish trade statistics. Own offers and offers without a price are never hidden.

Related commits: `6b2124e2e7`, `ec4170fd11`, `d6ed434326`, `5c3ffb48a0`, `efa5c1987f`, `df11fcf477`, `d19b462ba8`,
`14bf6f6d70`, `d871aadb6b`, `5ecafb8a77`, `dbbeaf1012`.

## Mediated Payout

- If both traders accepted the mediation result before they received the signature of the peer, both waited and the
  payout was published only when one of them accepted again. Now the node which has both signatures publishes the
  mediated payout itself, whichever signature came last, also at the start.
- Without a click, the node publishes only with a synced wallet and enough peers, at the start only after the
  mailbox messages, and never once the delayed payout transaction is published. A failed automatic attempt is logged
  and repeated at the next start.
- After a trader accepted, the "Reject" button of the mediation result is disabled, as the signature cannot be
  withdrawn.
- The node reuses its stored signature only if it still matches the payout amounts and addresses, and it checks both
  signatures of the payout before it stores and broadcasts it.
- After a restart, a trader who accepted the mediation result watches the wallet for the payout of the peer again.
  Before, the trade did not complete if the message of the peer was lost.
- A peer signature which arrives before the trader's own copy of the mediation result is kept and checked once the
  result is applied. Before, it was rejected and the trade showed an error.
- During a refund request, a mediated payout is accepted only when the wallet has it confirmed.
- During a wallet rescan, a trade whose deposit transaction the wallet has not reached yet watches its payout address
  as the scan advances, so a payout which the scan finds completes the trade. Before, the setup of that watch
  failed.

Related commits: `604380732a`, `3183c9dd2d`, `e3cd39bf9d`, `2c7ae7a08e`, `21c131a763`, `62ab4e8068`, `bda91b9706`,
`82ead81bf5`, `6fd76365b1`, `716562d829`, `1d15cb703c`, `819fdc6d33`, `11f62b7dd9`, `2f55ee4fc0`, `72bcabdd9a`,
`44c003d28e`, `df428a0d30`.

## Disputes

- The first mediation result fixes the payout. The mediator's window locks the payout once a result was sent to a
  trader, and the mediator's node refuses another payout. A trader refuses a later result with another payout; a
  system message tells the trader that the first result stays valid. The protection is complete when the mediators
  run 1.10.12.
- A trader refuses a dispute opened by the peer which is not addressed to the trader, which follows a result or an
  arbitration, or whose contract, deposit transaction ID or delayed payout transaction ID does not match the trader's
  own trade. The contract may differ only in payment account data which one trader has not revealed yet. The trader
  is told when a case of the peer was refused, so that the trader can open one.
- Mediators and refund agents accept only disputes addressed to themselves.
- Every dispute message must carry the signature key of its sender, and dispute contracts must name the keys of the
  mediator and the refund agent. The exceptions for trades before August and September 2026 are removed, so disputes
  of releases before 1.10.4 are refused.
- A dispute result message without its chat message is dropped.
- The dispute summary used the buyer's deposit for the payout "seller gets trade amount minus penalty". It uses the
  seller's deposit now.
- The trader chat, mediation, refund and arbitration each queue only the messages of their own type and apply each
  message on its own. Before, a message whose handling failed stopped the others and was applied again with every
  later message, a message which arrived while the queue was applied was lost, and every manager removed the messages
  and acks of the others from the mailbox. A mailbox message is removed once it was handled; one whose handling fails
  is tried again at the next start.
- A dispute opened by the peer with the ID of a canceled offer is ignored. Before, it failed with an error which
  blocked the other messages of the manager. A closed offer alone is never reopened as a trade.

Related commits: `1b0c574a4c`, `80720310c3`, `bc36df2840`, `a946885fba`, `f243b5efc3`, `be372b2dec`, `12114fac16`,
`40cbf241e4`, `4742515e0b`, `fe13288006`, `5dd6fbf5f5`, `7613682aa1`, `b5b6b5699e`, `c1c54cddcd`, `4f30e91b3c`,
`1aa779e110`.

## Trade Protocol

- The taker signs its deposit inputs only if the transaction which it built has the transaction ID of the maker's
  prepared deposit transaction. Every deposit input must have the final sequence number `0xffffffff`. The check runs
  before the taker fee is published and again before signing.
- The taker refuses a deposit transaction whose maker change is dust (below 546 sat) or not P2WPKH. The maker
  refuses a trade amount which leaves a remaining offer amount below 546 sat.
- The maker spends only the reserved output of its maker fee transaction. Before, coins sent to the reserved address
  were spent too, and every taker rejected the trade.
- While a take handshake is pending (up to 120 seconds), the maker answers availability requests with "offer taken"
  and ignores further take requests. Before, a second taker could take the offer after 60 seconds and reset the first
  trade after its taker fee was paid.
- When a maker refuses a take request, the take offer screen names the reason. A maker who ignores the user makes the
  maker's offers untakeable for one hour after the last refusal. Availability answers count only when they are signed
  with the key of the offer.
- Offers with a buyer or seller security deposit below 0.0003 BTC cannot be taken.
- A seller who confirms the payment again reuses the stored payout transaction. After a mediation result without a
  penalty of the seller, the seller confirms only until the mediated payout is signed or published.
- The "payment started" and "payment received" confirmations act only on the trade for which they were opened.
- A buyer who confirms "payment started" again, for example after a failed send, no longer sets the trade back to
  "deposit confirmed". The message is sent again from the current state, and a restart still resends a message
  whose send failed. The confirmation shows "Sending confirmation..." for at most 10 seconds and then offers the
  next retry, also when the state did not change.
- The maker accepts a BSQ swap request only for a BSQ swap offer and only with the request type of the offer's
  direction: a buyer's request for a sell offer, a seller's request for a buy offer. It checks this before it reserves
  the offer. Before, a taker could take a fiat or altcoin offer with a fixed price with a BSQ swap request, and the
  maker's node settled it in BSQ at a price set for another currency without a confirmation of the user. A request of
  the wrong direction kept the offer reserved for 60 seconds.
- The buyer cannot confirm "payment started" for a trade with an error and is told not to pay. A stale timeout error
  is cleared once the deposit transactions arrived and were checked.
- A payout seen in a block completes the trade also without witness data, as filtered blocks carry none. The inputs
  and outputs are still checked.
- When the delayed payout transaction is published, the coins of the multisig output are unlocked only after the
  broadcast.

Related commits: `42184f113e`, `88a6f696a2`, `8f3b01ca2e`, `3d46344482`, `3536d6e575`, `bd7855b13c`, `559f569e86`,
`4398841e75`, `0ac1978040`, `03a28868f5`, `0aa91ff4c8`, `af9a2b5183`, `852d9c7abb`, `ed06cba64d`, `c47b10b71e`,
`267cdd0fe0`, `e2eb36490d`, `9cbacdbe7f`, `9985ee8914`, `94a366530c`, `d68ff63eb6`, `0b32c7b0cd`, `00528474b1`,
`514c5c7c71`, `a388cf97ad`, `b68424a864`, `bfeba866c7`.

## Fees

- The fee rate which the client uses stays at least 2 sat/vB and at most 600 sat/vB. The fallback rates before the
  fee service answers drop from 50 and 15 to 5 sat/vB.
- At the start, the minimum fee rate of the price node was overwritten by the default until the next price request.
  It is kept now.
- A maker accepts the trade transaction fee of the taker within a factor of 4 of its own expected fee, before 2. The
  minimum for new take requests rises from 250 to 424 sat (2 sat/vB times 212 vB). Trades loaded from disk keep the
  minimum of 250 sat. BSQ swaps keep the factor of 2.
- Before it contacts the maker, the taker checks its own fee against a factor of 2, which makers of 1.10.11 accept.
  If the fee is too high, the user is told to consolidate the wallet; if it is too low, to try again. No taker fee is
  paid.

Related commits: `0e6dcca504`, `cad47159e6`, `fc14d55ace`, `fa5ff6d4bb`, `efbdb2bdef`, `26c19237f8`, `a0f5ae5ec1`,
`bdd3e5ad82`, `fd9d0be723`, `dc85f8b13a`, `9e3f5895c9`.

## Trade Limits

- The hard cap of the trade amount in the code rises from 0.25 BTC to 0.5 BTC. The DAO parameter of the maximum trade
  limit stays 0.25 BTC, so nothing changes until a DAO vote raises it. Before such a vote activates, the filter field
  `disableTradeBelowVersion` must be set to `1.10.12`.

Related commits: `5424ffb9be`, `8cea22038e`, `17a7949e81`.

## DAO

- A proposal which got no accept or reject vote is no longer recorded as rejected with zero votes. Whether it was
  recorded depended on a display list, so nodes which parsed the RESULT block 969 537 live and nodes which parsed it
  at a resync got different DAO state hashes. The vote result view and its export show such a proposal as not
  accepted with no counted votes. All seed nodes must run 1.10.12 before the next RESULT block.
- Full nodes answer every block request with signed blocks only. Lite nodes ignore responses with only unsigned
  blocks and drop new block broadcasts without a signed block. `--skipBsqBlockProvidersSignatureVerification` only
  skips the verification now.
- A full node no longer stops parsing at a script type which its Bitcoin Core does not know; such types map to
  `witness_unknown` or `nonstandard`, as older Bitcoin Core versions report them.
- A fix of the parsing of BSQ transactions keeps full nodes from stopping at a block. Details follow once the seed
  nodes run 1.10.12.
- Block responses stay below 5 MiB (at least one block), and state hash responses hold at most the 50 000 newest
  hashes. A full node stops signing blocks once the response is full. The accounting lite node waits for the
  synced wallet and requests blocks until it reaches the chain height.
- A lite node ignores the broadcast of a block which is 1024 or more blocks behind its DAO tip; such blocks come with
  the block requests. It remembers the accepted broadcasts of the blocks within that window, so a signed old block
  can no longer circulate again. Before, the history of accepted broadcasts grew without a limit.
- While the DAO state needs a resync, the warning appears when it is first seen and at most once per new block.
  Creating, cloning and taking offers, DAO transactions and BSQ sends are blocked meanwhile; BTC withdrawals, trades
  and disputes stay available.
- The dust error of parameter proposals shows the limit in the unit of the field.
- Bundled DAO resources up to block 976 481, state hash checkpoints from 972 000 to 976 000, and the Burning Man
  address list v0008.

Related commits: `f74f08413e`, `058643a557`, `f75a2a3e3d`, `c7b40f72e2`, `8b2f951ed1`, `089429a7be`, `3937241c4f`,
`728efc004a`, `72781e0fa5`, `cd164b1600`, `65928c7b3d`, `d37b496ba0`, `473e22ecf6`, `89586ced12`, `432231703d`.

## Network and Data Validation

- The node checks the length of each incoming message before it reads it. A message announced above 10 MiB, or with
  an invalid length, is not read and the connection is closed. The message must arrive within 4 minutes.
- A message above the size limit of its type (200 KiB, or 10 MiB for data, block and state hash responses and
  bundles) is always dropped. Each element of a bundle passes the checks of a single message; unparsable elements
  count as rule violations, and elements of unknown newer types are dropped without one.
- A seed node leaves out only an item which does not fit into a data response, instead of every item after it, and
  keeps the response below 10 MiB in total. The broadcaster sends a bundle before it would exceed 10 MiB.
- Mailbox entries which cannot be decrypted are ignored by the hash of the entry instead of the UID chosen by the
  sender. The HMAC of messages is compared in constant time.
- Storage entries whose signature failed are remembered (up to 10 000), and their log lines no longer print the
  payload.
- A listed offer is replaced only by an offer with the same ID of the same owner. Cached trade limit checks of offers
  are keyed by the hash of the offer.
- Whether an account shows as banned is decided by the key known to be the owner's, not by the key inside a signed
  witness.
- Trade statistics dated more than one day ahead, without volume or with a price above 10^18 are ignored. Sums and
  averages of statistics no longer overflow. The trigger price of an offer uses only a price feed price of the last
  30 minutes.
- Peers reported by other nodes get the local time of reception as their date.
- Alerts carry a second signature over all their fields and are shown only with it. Update alerts with an invalid
  version number are ignored.
- At the start, witnesses are taken from the first seed node response which carries any, instead of the first
  response at all.
- A failing listener of the data storage no longer stops the others. An out-of-memory error in a connection shuts a
  headless node down.
- Own offers are no longer backdated when the connection to the own onion address closes.
- The ignore list of mailbox entries is stored in the new file `IgnoredMailboxPayloadHashes`, because the old file
  holds UIDs chosen by the senders, which can look like payload hashes.
- A DSA signature is verified only with a key of at most 600 bytes, with its parameters, whose modulus is positive
  and has at most 2048 bits. The cost of a verification grows with the square of the modulus, and a key with a large
  modulus can have the size of a usual key. A key outside the bounds counts as an invalid signature. The bounds apply
  at the verification and not where a key is read, because nodes up to 1.10.11 accept such keys: an entry with such
  an owner key can be relayed by honest nodes, and a trade or a dispute with such a peer key can be stored. A
  verification which fails because of key parameters that no node creates counts as invalid too.
- A trade statistic may have at most 256 bytes, and each trade statistics store holds at most 100,000 records. A
  statistic is stored only if it is within the size limit and passes the validity rules except the check of its
  currency, and only a stored statistic is announced and relayed. A statistic of a currency which this version does
  not know is stored and relayed, as by earlier versions, but not used. At capacity a node keeps its records and
  refuses new ones.
- A node counts a received statistic which it cannot parse as a rule violation of the peer which relayed it, and it
  cannot tell an honest relay from the creator. So a received trade statistic is parsed and checked by its hash as
  1.10.4 to 1.10.11 do: a statistic above the size limit is parsed and then not stored, and of the payment method
  names only those which 1.10.11 knows are replaced by their positions before the hash is checked. A statistic of a
  new payment method (`CUSTOM` and the stablecoins) is accepted with the position, as 1.10.12 creates it, and with
  the name spelled out, as 1.10.11 relays it. 1.10.11 shows the number of a position which it does not know.
- The caches of currency lookups hold at most 1024 codes. Before, every trade statistic with an invented currency
  code added entries.

Related commits: `2e1f37614a`, `d8649caeae`, `c67c87cea2`, `16d9dce426`, `b0ce053af8`, `8b84d90942`, `c222dd7258`,
`3132ea8e38`, `cf61f73f63`, `12b7391778`, `8c2730974b`, `e862479265`, `2320dd22f4`, `c6dc817bc7`, `27d5795b5e`,
`8320366abb`, `25dd2e1734`, `23ba911378`, `8df0ac94c6`, `38e92e249a`, `b7ca9b7a93`, `3737576f63`, `6dfc69cbf0`,
`f801dc4679`, `58aaafb77e`, `91b099732e`, `d0b47cd142`, `82549c6cf1`, `87ce788258`, `962172ffe5`, `04ba4630b4`,
`e49e5231dd`, `c0dccbf7df`, `2ab7dafb5e`.

## Tor

- The Tor library brings tor 0.4.9.16 of Tor Browser 16.0.1, lyrebird 0.10.0 and sandy 1.2.0.
- When Tor cannot run in the sandbox of the system, a popup offers to shut down or to turn the sandbox off in
  `bisq.properties` and shut down. On Windows, the warning about bridges with pluggable transports offers the same.
  The button is missing when `--useTorSandbox` is given on the command line.
- The output and the errors of Tor go in order into `tor.log` in the Tor directory, rotated at 10 MiB, and a failed
  start quotes it. "Zip log files" includes it.
- On macOS, a sandboxed Tor no longer fails when the data directory is reached through a symbolic link.
- A malformed `--torrcOptions` value or an unreadable `--torrcFile` stops the start of Tor. The error names the
  position of the bad part, not its text.
- A line of the torrc file is split at its first whitespace, as Tor does. Before, a line with a tab between key and
  value, such as `ExcludeNodes<TAB>{ru}`, was skipped without a message, and Tor started without the restriction.
- "Zip log files" leaves out the excerpt of `tor.log` which a node log quotes for a failed Tor start, as it can begin
  in the middle of a bridge identity. The files on disk keep it.

Related commits: `cb569986b3`, `e85f8f086c`, `172f50554c`, `6f8852f36c`, `6e028ee341`, `34b7b200aa`, `a106cec8f9`,
`073d2e512f`, `7abfdc3936`, `ebffc0ed74`, `5a8204640c`.

## Bitcoin Wallet and Local Node

- A Bitcoin node on localhost is used only if it serves the full chain with witness data and bloom filters. The check
  waits at most 5 seconds. Otherwise the desktop application names what the node lacks and offers to shut down or to
  continue with other nodes; the daemon shuts down.
- The footer shows "(localhost)" only when the wallet uses the local node.
- At the shutdown, the wallet stops the Bitcoin peers before it saves the wallets and closes the chain file. A failed
  stop aborts a restore from seed words.

Related commits: `e1edd89441`, `00907333e9`, `d6a3abd484`, `17df5f929c`, `6627a75429`, `0393465f53`, `c8aaad8500`,
`47d6949fed`.

## API and Daemon

- The daemon does not start without a non-blank `--apiPassword`. The gRPC API listens on `127.0.0.1` by default; the
  new option `--apiHost` sets another address and logs a warning for a non-loopback address, as the API has no TLS.
- A failed `unlockwallet` no longer leaves the wallet counted as unlocked.
- `getmarketprice` answers from the price cache with prices of the last 30 minutes, or fails at once with
  `UNAVAILABLE`. The daemon and the REST node request prices every minute.
- `confirmpaymentstarted` and `confirmpaymentreceived` fail with `FAILED_PRECONDITION` when the dispute state forbids
  the confirmation, instead of reporting success.
- `confirmpaymentstarted` and `confirmpaymentstartedxmr` fail with `FAILED_PRECONDITION` once payment started was
  confirmed. Before, they reported success while nothing was sent, and the XMR variant replaced the transaction ID and
  key of the stored payment proof. The node resends an unacknowledged message itself.
- The role of a BSQ swap is shown after a restart.

Related commits: `0e29f08e8c`, `bdb202b81d`, `595ac9cf53`, `291172e020`, `7abe861f29`, `893a8a9aa1`, `ce0bb34f6b`,
`8908a604de`.

## Desktop Application

- The update download window shows the result of the download and the verification, and the shutdown after it is
  optional.
- A failed broadcast from the BSQ wallet and a failed export of the vote results now show their errors.
- The user agreement and the trade rules can be copied to the clipboard.
- Translations of 15 languages are updated. Texts which showed raw placeholders or broke on apostrophes are fixed in
  French, Italian, Polish, Thai, Portuguese, Persian, Chinese and Spanish; a test checks every translation.
- The SoChain explorer uses its new URLs, and a BSQ amount below the minimum gets a clearer error.

Related commits: `339131101c`, `b6264493cd`, `e3d22aa64a`, `1c85326415`, `1b26090522`, `02fb94da10`, `f6ee161440`,
`a5127b81f4`, `451302b988`, `642740330e`, `2a8bb49299`.

## Operators

- On Linux and macOS, the data directory is set to owner-only at every start.
- Seed nodes count the losses of all connections also when the monitor asks for their inventory, and shut down at
  the second loss.
- The inventory monitor includes the Bisq 2 bridge node, which answers inventory requests, and counts the data at
  most every 10 seconds.
- The trusted BSQ block providers use the new bridge `iyiudcaq...onion` with a new key; the default price nodes gain
  `bsqprcgpyw7z337btcbjxvvtmud4mr6k552piui4xq62qdeqej2cgryd.onion`.
- The seed node Docker image runs on Java 21, and the install script of the seed node initializes the submodules and
  installs Amazon Corretto 21.

Related commits: `9dcb5448d4`, `292d0d83e9`, `4ce72f1c36`, `13d27022d0`, `d7a3ad9821`, `3bc1aab9a7`, `ca020b378a`,
`7e45648ba7`, `d7ba763fa8`.

## Build Tooling

- Builds, CI, the release builder and the installers use Amazon Corretto 21, at least 21.0.13.1, instead of Azul
  Zulu 21.0.13, which lacks the fix of CVE-2026-114938.
- The RPM package is built reproducibly by `:desktop:reproducibleRpm`, with its own Docker builder. DEB and RPM
  packages are built from a jpackage app image, and `generateInstallers` builds both on Linux.
- The project no longer enables the Gradle build cache; release builds pass `--no-build-cache` if a user enabled it.
- The installer evidence and signing tasks work with the configuration cache.
- A CodeQL workflow, `setup-gradle` instead of the deprecated `gradle-build-action`, read-only tokens for the e2e
  workflows, base images pinned by digest and a 7-day delay of Dependabot updates.
- `replace_version_number.sh` updates every version file, also the default release tag of the RPM builder.
- The RPM builder clones the release tag by default, runs on Amazon Corretto 21 and takes the build date of the
  package from `SOURCE_DATE_EPOCH`. The reproducible RPM is called `bisq-reproducible`: it runs on the system Java
  instead of the bundled runtime and has no menu entry, so it must not replace an installation of the shipped `bisq`
  RPM.

Related commits: `81cef09452`, `116746085c`, `29acccc6ef`, `92dbe91a72`, `90f2356f3d`, `04bad0f806`, `d763a6beb0`,
`1b247694f0`, `c592fd291f`, `c4cb4368de`, `a17e03d10d`, `6d0bc85cf2`, `4316aedc93`, `930e5ab1d5`, `47c00ab573`,
`43b70a7e83`, `d5e0206016`.
