# Bisq 1.10.12 Release Notes

> **Draft. The maintainer completes these points before the release and then removes this block:**
>
> - These notes cover `release-candidate-v1.10.12` from `v1.10.11` (`ea9d5dff90`) through `2b4b82de6c`. The local
>   tag `v1.10.11` points to another commit and was not used. The tag `v1.10.12` (`f594669f4c`) is `v1.10.11` with
>   only the version bump; it is not this branch. Move the tag before the release.
> - Confirm the version number `1.10.12` (set by `32c1fa0fbc`), and set the release date and the "Generated on" date.
> - `rc-pr-numbers-private-repo`: the pull requests #182 to #324 are pull requests of the private repository
>   `bisq-network/bisq-security`. Readers of the public repository cannot open them. Decide whether the notes name
>   them, and link the commits to `https://github.com/bisq-network/bisq/commit/<hash>` once the branch is public.
>   The hashes change if the branch is rebased.
> - `dao-op-return-zero-disclosure`: `docs/release-checklist.md` says to publish the fix of the OP_RETURN type byte
>   `0x00` (`6946e7bdf0`) only after the seed nodes run it. These notes name it only vaguely, in "DAO", and the commit
>   table leaves out its subject. Fill in the details once the seed nodes are updated.
> - `security-fix-disclosure-level`: the notes describe the effect of the security fixes, not how to exploit them.
>   Decide whether that level of detail may be published at the release, or only after most nodes updated. This
>   applies in particular to the BSQ swap request admission (`b68424a864`), whose description names the offers of
>   makers which did not update, and to the DSA key bounds (`d0b47cd142`, `82549c6cf1`).
> - `corretto-cve-id-unconfirmed`: confirm the CVE ID `CVE-2026-114938` before the notes name it.
> - `stablecoin-contracts-unverified`: the specification of the stablecoins asks to check the token contracts
>   against the documentation of the issuers before the release.
> - `fee-pricenode-floor-deployment`: the minimum fee rate of 2 sat/vB relies on price nodes which apply the same
>   minimum. Confirm that the price nodes run it.
> - `reproducible-build-tag-default`: `reproducible-build/rpm/Dockerfile` defaults to `TAG_NAME=v1.10.12`, and
>   `replace_version_number.sh` updates it. `reproducible-build/deb/Dockerfile` still defaults to `TAG_NAME=v10.0.4`,
>   which is no Bisq 1 tag. Builders of the DEB package pass `--build-arg TAG_NAME=v1.10.12`.
> - `sochain-selected-explorer`: the renamed SoChain explorer has no migration of a stored selection. Check whether
>   users who selected "SoChain. Wow." keep the old URLs.

These release notes cover the changes after `v1.10.11` (`ea9d5dff90`) through `2b4b82de6c` of
`release-candidate-v1.10.12`. The branch is `master` with the 1.10.11 release merged in, plus the pull requests of
the release candidate. The `tor` submodule is pinned at `2cd94b971b` of the Tor library.

- Total commits: 592
- Non-merge commits: 454, of which 14 shipped already in 1.10.10 or 1.10.11 under other hashes (DAO resources of
  height 971800, renewed GPG keys, netlayer 0.7.12, "Set version 1.10.10" and two Dependabot updates). They are left
  out below.
- Merge commits: 138
- Files changed: 637
- Generated on: TODO (draft of 2026-11-19)

## Compatibility and Operator Notes

- The application and packaging version is `1.10.12`. P2P, local database and trade protocol version numbers remain
  unchanged.
- The protobuf definitions gain `StableCoinAccountPayload` (field 42 of `PaymentAccountPayload`),
  `CustomPaymentMethodAccountPayload` (field 43) and the preference `bsq_offer_price_range_factor` (field 75).
  Versions up to 1.10.11 show offers with stablecoin or custom payment methods with an unknown payment method and
  cannot take them. They cannot read accounts, trades, contracts or disputes with such data, so **mediators and
  refund agents update before the first such trade.**
- **Seed nodes update first.** A 1.10.12 node closes the connection on a message above 10 MiB and removes the peer
  from its saved peers. Nodes of 1.10.11 and earlier answer a request of DAO state hashes from the DAO genesis with
  about 12 MB and of accounting blocks from the genesis with about 17 MB. Updated seed nodes keep their answers below
  the limit.
- **All seed nodes run 1.10.12 before the next DAO RESULT block** (block 978 897 for the cycle which started at
  974 227, unless a vote changes the phase durations). From 1.10.12 on, a proposal without an accept or reject vote
  is no longer part of the vote result. A seed node of an earlier release reports another DAO state hash at that
  block, and a resync of the other nodes does not resolve the conflict. A seed node which processed the RESULT block
  969 537 with an earlier release must be restarted once (`docs/release-checklist.md`).
- After the seed nodes run 1.10.12, require it for the DAO with the filter field `disableDaoBelowVersion`
  (`docs/release-checklist.md`, consensus fixes without activation height).
- **Alerts:** 1.10.12 shows an alert only with the new alert signature over all its fields. Alerts published with
  1.10.11 or earlier are ignored. Publish alerts, including the update alert of this release, with 1.10.12, and
  publish alerts that are still needed again.
- **Mediators update first.** A 1.10.12 trader refuses a second mediation result with another payout, but a 1.10.11
  mediator can still send one, and a 1.10.11 trader applies it.
- Dispute messages of releases before 1.10.4 and disputes of trades whose contract was made before 1.10.4 are
  refused. Lite nodes before 1.10.4 get no usable DAO blocks from updated full nodes.
- **Daemon:** the daemon does not start without a non-blank `--apiPassword`. The gRPC API listens on `127.0.0.1` by
  default; the new option `--apiHost` sets another address. Up to 1.10.11 it listened on all interfaces.
- The daemon shuts down at the start if the Bitcoin node on localhost cannot serve the wallet (pruned, no witness
  data, no bloom filters, or another service on the port). Fix the node or start with `--ignoreLocalBtcNode=true`.
- Seed nodes now shut down when they lose all connections a second time since their start, also when the network
  monitor asks for their inventory in between. They need an automatic restart, such as the systemd unit.
- On Linux and macOS, Bisq sets its data directory to owner-only (`700`) at every start. Backup or monitoring jobs
  under another account lose access to it.
- The Tor log files `tor-stdout.log`, `tor-stdout.log.1` and `tor-stderr.log` are replaced by `tor.log` and
  `tor.log.1`. The old files are deleted at the start.
- A malformed `--torrcOptions` value or an unreadable `--torrcFile` now stops the start of Tor with an error. Up to
  1.10.11, Bisq started Tor without the custom entries of the user. A line of the torrc file with a tab between key
  and value now reaches Tor as an entry; up to 1.10.11 it was skipped without a message, and an invalid such line now
  stops the start.
- The hard cap of the trade amount in the code rises from 0.25 BTC to 0.5 BTC. The DAO parameter of the maximum
  trade limit stays 0.25 BTC. Before a DAO vote raises it above 0.25 BTC, set the filter field
  `disableTradeBelowVersion` to `1.10.12` before the activation height of the vote, because 1.10.11 limits trades to
  0.25 BTC.
- Builds, the installers and the seed node install script use Amazon Corretto 21, at least 21.0.13.1, instead of
  Azul Zulu. `verifyReleaseEnvironment` requires the vendor "Amazon.com Inc.". Seed node operators who build from
  source install Corretto 21.
- Operators of the seed node Docker image rebuild it; it runs on Java 21 now.
- The trusted BSQ block provider list replaces the bridge `hn6qahy7...onion` with `iyiudcaq...onion` and a new key.
  The default price nodes gain `bsqprcgpyw7z337btcbjxvvtmud4mr6k552piui4xq62qdeqej2cgryd.onion`.
- The first start after the update tries again to decrypt mailbox entries of the last 15 days which it had ignored,
  because the ignore list is now keyed by the hash of the entry. It is stored in the new file
  `IgnoredMailboxPayloadHashes`; the old file `IgnoredMailboxMap` is no longer read.
- A node verifies a DSA signature only with a key of at most 600 bytes whose modulus is positive and has at most
  2048 bits. No unmodified node creates another key, but nodes up to 1.10.11 accept entries and messages signed with
  one, and 1.10.12 rejects them. Such keys are still read, so stored trades and disputes with such a peer key load.
- A trade statistic above 256 bytes is neither stored nor relayed, and each trade statistics store holds at most
  100,000 records. The shipped data stays far below both limits. Nodes up to 1.10.11 relay larger statistics, so
  receiving one is no rule violation of the peer.
- A trade statistic of a new payment method (`CUSTOM` and the stablecoins) which 1.10.12 creates carries the
  position of the method in the payment method list of the statistics. 1.10.11 keeps and relays it and shows the
  number instead of the name. 1.10.12 checks a received statistic as 1.10.4 to 1.10.11 do, so it also accepts such a
  statistic with the name spelled out, which those versions relay.
- The reproducible RPM of `:desktop:reproducibleRpm` is called `bisq-reproducible`, so the RPM which the release
  ships is the only package called `bisq`.

## Notable Changes

See `notable-changes.md` for the changes grouped by topic, with their commits.

### Payment Methods

- **Stablecoins** become payment methods of the USD market: one coin pays one USD of the trade volume, whatever its
  market price. There are 18 methods: USDT on Ethereum, Tron, BNB Smart Chain and Solana; USDC on Ethereum, Solana and
  BNB Smart Chain; USDS, USDe, DAI, RLUSD and USDF on Ethereum; USDG and PYUSD on Ethereum and Solana; U on Ethereum
  and BNB Smart Chain. The account holds an address, checked for its network. The trade period is 24 hours. The trade
  limit is the highest limit class, without account age or signing, and the taker needs no account age witness of the
  maker. The trade steps show the coin, the network and the token contract. The risk that the issuer freezes coins is
  shown at the account creation and before an offer is made or taken.
- **Custom payment method** (`CUSTOM`) for payment methods which Bisq does not support, in every fiat currency. The
  account holds a name of up to 20 characters and account data of up to 1000 characters. Only accounts with the same
  name and currency match an offer. Names of Bisq payment methods are refused. The trade period is 4 days; the trade
  limit is at most 0.01 BTC. The user confirms warnings about the missing vetting and the risk of chargebacks.
- The API cannot create stablecoin or custom accounts but can use accounts created in the desktop application.
- New national currency accounts offer three selectors: payment methods, a searchable stablecoin list and a custom
  payment method. The offer books get a filter "Show stablecoins only", and Market > Trades separates fiat and
  stablecoin trades. A popup at the start introduces both features once.
- The API `takeoffer` refuses a payment account which does not match the offer (payment method, currency, country or
  bank), as the desktop application does.
- The revealed payment account data of the peer is accepted only with the payment method named in the contract. A
  buyer also refuses seller account data of another stablecoin rail than the offer's. The API and the trade details
  hide such data of trades of earlier builds, and the API refuses the payment confirmation for them.
- After a new account was canceled, choosing the same payment method again shows its form again.
- Market > Trades keeps the payment method filter when the same currency is selected again.

### BSQ Market

- The BSQ market price is the volume weighted average of the BSQ trades of the 24 hours up to the newest BSQ trade,
  from at most 200 trade statistics, instead of the price of the newest trade. The label says "Average price of
  recent Bisq trades". A late trade statistic no longer replaces the price of a newer trade, in every currency.
- The BSQ offer book and its chart hide offers of other users whose price is much worse for the taker than the 90-day
  average BSQ trade price. The factor is set in Settings > Preferences (default 2, up to 1000; 1000 turns it off). A
  hint with a link to the setting appears when every offer is hidden. Own offers are never hidden.

### Trade Protocol

- When both traders accepted a mediation result, the node with both signatures publishes the mediated payout itself,
  also at the start, once the wallet is synced. After acceptance, "Reject" is disabled. The node watches for the
  payout of the peer again after a restart. It reuses its stored signature only if it matches the accepted payout,
  and it checks both signatures before it broadcasts.
- A peer's mediated payout signature which arrives before the mediation result is kept and checked when the result
  arrives, instead of failing the trade.
- The first mediation result fixes the payout: the mediator cannot send another payout, and a trader refuses one.
- A trader accepts a dispute opened by the peer only if it is addressed to the trader and its contract and
  transaction IDs match the trader's own trade. The contract may differ only in payment account data which is not
  revealed yet.
- A seller who confirms the payment again reuses the stored payout transaction. Payment confirmations act only on
  the trade for which they were opened.
- A buyer who confirms "payment started" again no longer sets the trade back to "deposit confirmed"; the message is
  sent again from the current state. The confirmation shows its status for at most 10 seconds and then offers the
  next retry.
- During a wallet rescan, a trade whose deposit transaction the wallet has not reached yet watches its payout address,
  so a payout found by the scan completes the trade.
- The maker accepts a BSQ swap request only for a BSQ swap offer, and only with the request type of the offer's
  direction, before it reserves the offer. Before, a fiat or altcoin offer with a fixed price could be settled in BSQ
  at a price set for another currency.
- The buyer cannot confirm "payment started" for a trade with an error and is told not to pay. A stale timeout error
  no longer blocks the confirmation once the deposit transactions arrived.
- A payout seen in a block completes the trade even without witness data.
- The taker signs the deposit transaction only if it has the transaction ID of the maker's prepared transaction, and
  every input must have the final sequence number. The taker refuses a dust or non-P2WPKH change of the maker.
- The maker spends only the reserved output of the maker fee transaction, so other coins sent to the address no
  longer break every take attempt.
- While a take handshake is pending (up to 120 seconds), the maker refuses further take requests for the offer.
- The take offer screen names the maker's reason for a refusal. A maker who ignores the user blocks the maker's
  offers for one hour; answers count only when they are signed with the offer key.
- Offers with a security deposit below 0.0003 BTC cannot be taken.
- The dispute summary uses the seller's deposit for the "seller gets trade amount minus penalty" payout.
- Each support manager queues only the messages of its own support type and applies each message on its own. A
  message whose handling fails no longer blocks or repeats the other messages, and a message which arrives while the
  queue is applied is no longer lost. A dispute opened by the peer for a canceled offer is ignored instead of failing.

### Fees

- The fee rate stays at least 2 sat/vB and at most 600 sat/vB. The fallback rates before the fee service answers drop
  to 5 sat/vB. The minimum fee rate of the price node is no longer overwritten by the default at the start.
- A maker accepts the trade transaction fee of a taker within a factor of 4 of its own (before 2), with a minimum of
  424 sat for new take requests. The taker checks its own fee with the factor of 2 before it contacts the maker, so
  that makers of 1.10.11 accept it, and tells the user what to do when the fee is outside the range.

### DAO

- A proposal without an accept or reject vote is no longer part of the vote result. The vote result view shows it as
  not accepted with no counted votes.
- Full nodes answer block requests with signed blocks only, and lite nodes drop new block broadcasts without a signed
  block.
- Full nodes no longer stop parsing at a script type which their Bitcoin Core does not know.
- A further parsing fix of BSQ transactions keeps full nodes in sync (details after the update of the seed nodes).
- Block and state hash responses stay below 5 MiB and 50 000 hashes. A full node stops signing blocks once a
  response is full. The accounting lite node waits for the wallet and requests blocks until it reaches the chain
  height.
- A lite node ignores the broadcast of a block which is 1024 or more blocks behind its DAO tip, and it remembers the
  accepted broadcasts of the blocks within that window, so an old signed block no longer circulates again.
- While a DAO resync is needed, the warning appears at most once per block, and offers, DAO transactions and BSQ
  sends are blocked.
- Bundled DAO resources and state hash checkpoints up to block 976 000 and Burning Man address list v0008.

### Network and Data Validation

- The length of each incoming message is checked before it is read, with a limit of 10 MiB. Messages above the limit
  of their type are always dropped. Every element of a bundle passes the checks of a single message.
- Responses to data requests leave out only an item which does not fit, and stay below 10 MiB in total.
- Mailbox entries which cannot be decrypted are ignored by their hash. Invalid signatures of stored entries are
  remembered so that they are not checked again.
- Offers are replaced only by offers of the same owner, and cached trade limit checks are keyed by the offer hash.
- Banned accounts are decided by the key of the owner. Trade statistics with dates more than one day ahead, without
  volume or with absurd prices are ignored, and trigger prices use only the price feed.
- Peer dates from other nodes are replaced with the local time. Alerts need a signature over all fields and a valid
  version number.
- At the start, witnesses are taken from the first seed node response which carries any.
- A DSA signature is verified only with a key within the size and modulus bounds; a key outside them counts as an
  invalid signature. A verification which fails because of key parameters that no node creates counts as invalid too.
- Trade statistics have a size limit of 256 bytes and a limit of 100,000 records per store. A statistic is stored
  and relayed only if it is within the size limit and passes the validity rules except the check of its currency. A
  statistic of a currency which this version does not know is stored and relayed, but not used.
- A received trade statistic is parsed and checked by its hash as 1.10.4 to 1.10.11 do, so a statistic which those
  versions relay is no rule violation of the peer which relayed it.
- The caches of currency lookups hold at most 1024 codes, so trade statistics with invented currencies no longer grow
  them.

### Tor

- Tor 0.4.9.16 of Tor Browser 16.0.1, lyrebird 0.10.0 and sandy 1.2.0.
- When Tor cannot run in the sandbox, or on Windows with pluggable transports, a popup offers to turn the sandbox off
  in `bisq.properties` and shut down.
- The output and errors of Tor go to `tor.log`, and a failed start quotes it.
- On macOS, a sandboxed Tor starts when the data directory is reached through a link.
- A tab between key and value of a line of the torrc file is accepted, as Tor accepts it.
- "Zip log files" leaves out the excerpt of `tor.log` which a node log quotes for a failed Tor start, as it can begin
  in the middle of a bridge identity.

### Desktop Application

- The update download window shows the result of the download and verification itself.
- Errors of a failed broadcast from the BSQ wallet and of the vote result export are shown.
- The user agreement and the trade rules can be copied to the clipboard.
- "(localhost)" in the footer only when the wallet uses the local Bitcoin node; an unusable local node is reported.
- Own offers no longer expire when the connection to the own onion address closes.
- Translations in 15 languages; broken placeholders and apostrophes fixed.

### API

- `getmarketprice` answers from the price cache, or fails at once with `UNAVAILABLE`; the daemon requests prices
  every minute.
- `confirmpaymentstarted` and `confirmpaymentreceived` fail with `FAILED_PRECONDITION` when the dispute state forbids
  them.
- A failed `unlockwallet` no longer leaves the wallet unlocked. The role of a BSQ swap is shown after a restart.
- `confirmpaymentstarted` and `confirmpaymentstartedxmr` fail with `FAILED_PRECONDITION` once payment started was
  confirmed, instead of reporting success without sending anything. The XMR variant no longer replaces the stored
  payment proof in that case.

### Build

- Amazon Corretto 21 (at least 21.0.13.1) instead of Azul Zulu 21.0.13, which lacks the fix of CVE-2026-114938.
- Reproducible RPM packages (`:desktop:reproducibleRpm`, package `bisq-reproducible`) and DEB and RPM packages
  built from an app image. The RPM builder uses Corretto and the release tag, and takes the build date from
  `SOURCE_DATE_EPOCH`.
- The Gradle build cache is no longer enabled by the project. CodeQL and hardened GitHub workflows.

## Known Issues

- The Bitcoin connections drop the SOCKS credentials of `--torStreamIsolation`, so they share one isolation group.
- A full node of 1.10.11 or earlier still stops at a block with a script type which its Bitcoin Core does not know.
- Trade statistics can be published by any peer. The BSQ market price and the BSQ offer price range rely on them.
  Invented statistics can fill the 100,000 records of the writable store, and then a node stores and relays no new
  statistic until a release bundles the store.
- Data responses are cut to fit 10 MiB, but account age witnesses, signed witnesses, filters, alerts and dispute agent
  entries do not count toward the size limit of a response. This is a mitigation.
- The block of offers of a maker who ignores the user is kept in memory and ends with a restart.
- Trade limits still follow the owner key of each signed witness (`docs/specifications/account/banned-accounts.md`).

## Tests and Documentation

New specifications describe the taker trade transaction fee, the admission of take requests, the received message
limits, the mediated payout finalization and detection, the peer-opened dispute validation, the alert admission, the
vote result validation, the local Bitcoin node, the BSQ market price, the trade statistics validity and storage, the
stablecoin and custom payment methods, the BSQ swap request admission, the repeated payment started confirmation and
the DSA key bounds of signature verifications. Docker end-to-end tests cover the API refusals of the new accounts,
the 0.5 BTC cap, wrong passwords, mailbox delivery to a restarted seller, vote results after a restart, the fee rate
defaults and a compatibility run with the release on the maker side, and the e2e daemons get their fee rates from a
local stub. This release note summarizes source changes; it does not claim that release builds or tests were run for
this note.

## Commit Summary

The commits of `release-candidate-v1.10.12` after `v1.10.11`, without merge commits and without the 14 commits which
shipped in 1.10.10 or 1.10.11 already, in the order of their pull requests.

| Pull request | Commit | Summary |
| --- | --- | --- |
| #8081 | `aedca7b607` | Target reproducible bitcoind submodule |
| #8084 | `4b971177a5` | desktop: Re-enable dist tar generation for RPM builds |
| #8085 | `9482788165` | Rename deb-packager to linux-packager |
| #8086 | `b4a2924183` | Rename bisq.deb_packager to bisq.packager.deb |
| #8087 | `603c5437a7` | Create Packager interface abstraction |
| #8090 | `893a8a9aa1` | Reject confirm calls the dispute state forbids |
| #8090 | `d3645f9014` | Test the real confirmPermitted rule |
| #8091 | `03a28868f5` | Name the maker's reason for refusing a take |
| #8099 | `ce0bb34f6b` | Use the node key ring for the BSQ swap role |
| #8101 | `ed06cba64d` | Keep the payout tx on a repeated confirmation |
| #8101 | `76d0d9b7ab` | Refactoring: Extract isMediatedPayoutPublished |
| #8101 | `c47b10b71e` | Refuse confirmation after a mediated payout |
| #8101 | `3a809c7c58` | fixup! Refuse confirmation after a mediated payout |
| #8101 | `267cdd0fe0` | Refuse confirmation once mediated payout is signed |
| #8101 | `e1985056cf` | fixup! Refuse confirmation once mediated payout is signed |
| #8102 | `9f72aa8762` | Refactoring: Reuse Overlay hyperlink handling |
| #8102 | `339131101c` | Show the update download result in the window |
| #8103 | `b6264493cd` | Show the error popups that were never shown |
| #8103 | `e3d22aa64a` | Show a readable BSQ send broadcast failure |
| #8110 | `637ec56a7d` | Keep bitcoin-cli stderr out of its return value |
| #8111 | `94a366530c` | Accept a confirmed payout without witness data |
| #8128 | `bfc3b79b3f` | packager: Implement SpecBuilder for rpm build |
| #8129 | `20e8582412` | packager: Implement RpmPackager |
| #8106 | `72781e0fa5` | Replace 30 s DAO resync popup with action gates |
| #8106 | `ad10f848aa` | Check queued popups before the resync reminder |
| #8106 | `cd164b1600` | Recheck the DAO state before publishing DAO txs |
| #8106 | `5066e0356f` | Queue one resync popup behind other popups |
| #8106 | `66cdc27847` | Show the resync popup also in dev mode |
| #8106 | `65928c7b3d` | Block cloning an offer while a resync is needed |
| #8106 | `ba0e7e8a69` | Explain the resync with a wrong seed node |
| #8106 | `60d8b706b2` | Check the DAO state when a clone is confirmed |
| #8118 | `11f62b7dd9` | Keep a mediation signature until the result |
| #8118 | `2f55ee4fc0` | Skip a kept mediation signature once one is stored |
| #8092 | `0aa91ff4c8` | Treat a maker who ignores us as closed for an hour |
| #8107 | `291172e020` | Answer the API market price from the cache |
| #8107 | `7abe861f29` | Start no price request after shutdown |
| #8107 | `0e3526d057` | Test that the price requests repeat |
| #8107 | `36665215b0` | Drop the redundant shutdown check in retries |
| #8107 | `706cd9acd5` | Note the retried start request in the spec |
| #8109 | `0393465f53` | Show localhost in the footer only for a used node |
| #8109 | `28aa89ae5c` | Show (localhost) in the footer on regtest too |
| #8108 | `e1edd89441` | Skip a local Bitcoin node the wallet cannot use |
| #8108 | `00907333e9` | Limit the wait for the local node's answer |
| #8108 | `d6a3abd484` | Check the local node on its own thread |
| #8108 | `17df5f929c` | Ask before replacing an unusable local node |
| #8108 | `6627a75429` | Treat another service on the node port as unusable |
| #8105 | `f74f08413e` | Do not record proposals without counted votes |
| #8105 | `3003837b6b` | Add a control proposal to the all-ignore test |
| #8105 | `52c8056994` | Specify the rollout of the vote result rule |
| #8120 | `819fdc6d33` | Watch for the mediated payout after a restart |
| #8120 | `23184dd67e` | Test the mediated payout listener in the wallet |
| #8120 | `e65f54d107` | Update the payout specs for the restart watch |
| #8133 | `9bc4ce3ea1` | Wait for the applied price in the repeat test |
| #8124 | `c8aaad8500` | Stop PeerGroup before closing SPV store |
| #8124 | `47d6949fed` | Fail restore shutdown if PeerGroup stop fails |
| #8127 | `d37b496ba0` | Fix dust limit error message for param proposals |
| #8088 | `bdd3e5ad82` | Check trade tx fee before contacting the maker |
| #8088 | `f626f6f8ef` | Keep DAO fee parameters neutral in the test |
| #8088 | `fd9d0be723` | Name the direction of a refused trade tx fee |
| #8088 | `dc85f8b13a` | Answer a refused API take offer once |
| #8088 | `9e3f5895c9` | End the take offer attempt on a refusal |
| #8088 | `071149990c` | Complete the taker trade tx fee specification |
| #8088 | `80e7772d79` | Say take the offer in the take offer texts |
| #8140 | `4ce72f1c36` | Include bridge node in seed node monitors |
| #8141 | `292d0d83e9` | Do not reset numAllConnectionsLostEvents when GetInventoryRequest is received. |
| #8117 | `604380732a` | Refactoring: Extract mediated payout msg phases |
| #8117 | `3183c9dd2d` | Accept mediated payout msg after own payout |
| #8117 | `e3cd39bf9d` | Publish the mediated payout when both signed |
| #8117 | `2c7ae7a08e` | Disable rejecting a signed mediation result |
| #8117 | `dc10240d1e` | Refactoring: Reuse the both-signed check |
| #8117 | `0d5f5c1837` | Link the finalization and confirmation specs |
| #8117 | `21c131a763` | Ignore the payout message without own payout |
| #8117 | `62ab4e8068` | Publish the mediated payout only before step 4 |
| #8117 | `ccb8a06147` | Refactoring: Share the mediation test helpers |
| #8117 | `7ebb2c4ba9` | Refactoring: Call back after mailbox entries |
| #8117 | `bda91b9706` | Signal the end of the startup mailbox |
| #8117 | `82ead81bf5` | Publish automatically only with a synced wallet |
| #8117 | `6fd76365b1` | Keep failed automatic payouts out of trade errors |
| #8117 | `716562d829` | Refuse a mediated payout during a refund |
| #8117 | `1a224d3aa6` | Specify a replaced mediation result |
| #8117 | `bc19c4a53b` | Point to the restart detection of the payout |
| #8117 | `1d15cb703c` | Apply the refund rule to the payout in the wallet |
| #8145 | `32a6cbfbe6` | Refactoring: Extract the copy handler of popups |
| #8145 | `1c85326415` | Copy the user agreement to the clipboard |
| #8145 | `1b26090522` | Copy the trade rules to the clipboard |
| #8145 | `12f1b415cc` | Check every legal section body in the copy test |
| #8143 | `058643a557` | Show proposals without counted votes |
| #8143 | `b14a989f2f` | Pass the validated proposals to the presentation |
| #8136 | `80bb397516` | Keep the payment account observables across init |
| #8136 | `3d8cb2ed49` | Show filter reasons before the maker's ignore |
| #8136 | `188275cf1b` | Log when a maker's offers close or reopen |
| #8136 | `af9a2b5183` | Accept availability answers only from the maker |
| #8136 | `79f146d746` | Specify how long a maker's ignore lasts |
| #8149 | `ca020b378a` | Resolve seed node image classpath at execution time |
| #8149 | `7e45648ba7` | Run the seed node Docker image on Java 21 |
| #8153 | `116746085c` | packager: Make rpm build reproducible |
| #8154 | `29acccc6ef` | Create and register reproducibleRpm Gradle task |
| #8155 | `b5a01a3e2f` | Move deb builder Dockerfile into subdirectory |
| #8156 | `92dbe91a72` | Add Dockerfile for reproducible RPM builds |
| #8142 | `90f2356f3d` | Build the Linux packages from an app image |
| #8146 | `04bad0f806` | Fix the Linux installer evidence bundle |
| #8158 | `02fb94da10` | Update desktop translations in 15 locales |
| #8158 | `dc7d9947a0` | Clarify reviewed desktop translation wording |
| #8158 | `ed9d1e7dc7` | Keep legal-section bodies in English |
| #8158 | `53c9c0b27e` | Fix Japanese and Portuguese review findings |
| #229 | `d125348211` | docs(core): drop the stale minimum fee rate note of the BSQ swap fee |
| #229 | `1c01413cea` | refactor(core): drop the planned network minimum of the fee rates |
| #229 | `dbdd273a1a` | refactor(core): name the average vsize of the trade tx fee |
| #229 | `0e6dcca504` | feat(core): lower the default fee rates to 5 sat/vbyte |
| #229 | `cad47159e6` | fix(core): accept a trade tx fee within a factor of 4 of the expected |
| #229 | `fc14d55ace` | feat(core): require 2 sat/vbyte for the trade tx fee of a take request |
| #229 | `fa5ff6d4bb` | feat(core): keep the fee rate at least the minimum for trading |
| #229 | `f9508f1724` | docs(spec): state the fee rate rules of the trade tx fee |
| #229 | `62d17a0232` | docs(core): note that the buyer's delayed payout tx check uses 424 sat |
| #229 | `efbdb2bdef` | fix(core): keep the factor of 2 for the fee rate of BSQ swaps |
| #229 | `26c19237f8` | fix(core): use the stored trade minimum in the delayed payout tx check |
| #232 | `c592fd291f` | ci: add a CodeQL workflow that builds with the pinned JDK |
| #234 | `aee0d3057a` | build(docker): base the release builder on the Java 21 tag of Azul |
| #234 | `a5ea8d2eca` | build: accept every Azul Java 21 build at or above a minimum version |
| #234 | `ffaabadfa4` | ci: name the release evidence after Java 21 instead of the update |
| #234 | `a1279c2085` | docs: describe the minimum Java version instead of the exact build |
| #235 | `c4cb4368de` | ci: replace the deprecated gradle-build-action with setup-gradle |
| #235 | `2a6f1507ee` | ci: install the newest Zulu build of Java 21 for CodeQL |
| #236 | `a17e03d10d` | ci: give the e2e workflows a read-only token |
| #236 | `258c17957e` | ci: drop the bitcoind pre-pull that the image build does not use |
| #236 | `6d0bc85cf2` | build(docker): pin the e2e base images by digest |
| #236 | `9927665614` | ci: pin the WiX Toolset version of the Windows installer evidence |
| #236 | `3ceb2cc6bb` | ci: wait 7 days before Dependabot proposes a new version |
| #231 | `d7a3ad9821` | Update bridge nodes for inventory monitor and update pub key |
| #237 | `6dea11b5ed` | chore: tell first-time contributors that reviews of their PRs are paused |
| #237 | `e85f8f086c` | Update tor to 4751f83f03 |
| #237 | `d7ba763fa8` | Init submodules when installing a seednode from source |
| #237 | `64c85d7529` | docs: describe the minimum Java version instead of the exact build |
| #228 | `6e028ee341` | feat: offer to turn the Tor sandbox off when Tor cannot run in it |
| #228 | `34b7b200aa` | feat: offer to turn the Tor sandbox off for transports on Windows |
| #228 | `a106cec8f9` | fix: keep the Tor sandbox popup from defaulting to turning it off |
| #228 | `fff69b582a` | Improve i18n strings |
| #238 | `bcbea5288a` | ci: let CodeQL read the workflow run in private repositories |
| #238 | `c450658921` | ci: analyze only Java and the workflows with CodeQL |
| #223 | `72bcabdd9a` | Keep mediated payout signatures bound to accepted payout |
| #221 | `21d9df14da` | refactor(desktop): extract the result lock |
| #221 | `7f4a5af6f3` | fix(core): tell own and peer payout signatures apart |
| #221 | `44c003d28e` | fix(core): verify both mediated payout signatures |
| #221 | `1b0c574a4c` | feat(core): keep the payout of a sent mediation result |
| #221 | `a946885fba` | feat(desktop): lock the sent mediation payout |
| #221 | `80720310c3` | feat(core): refuse a changed mediation payout |
| #221 | `be372b2dec` | fix(core): refuse a peer dispute that reopens mediation |
| #221 | `4401440a9a` | fix(core): set mediation amounts only at first acceptance |
| #221 | `0904fe6f18` | fix(core): keep a saved mediation result unsent after a restart |
| #221 | `9bd41637ee` | refactor(core): simplify the sent mediation result checks |
| #221 | `f243b5efc3` | fix(desktop): lock the mediation payout when the result is sent |
| #221 | `bc36df2840` | fix(core): refuse to change the payout of a sent mediation result |
| #221 | `451b4a6fd0` | docs: state the rollout and legacy limits of mediation payout finality |
| #221 | `12114fac16` | feat(core): report a refused peer dispute that leaves the trader without a case |
| #221 | `40cbf241e4` | feat(desktop): tell the trader about a refused peer dispute |
| #222 | `8320366abb` | Decide a ban by the key of the account owner |
| #222 | `99225c738b` | Do not show a banned account as signed |
| #222 | `b4d501bf73` | docs(spec): state that limits still follow the witness owner key |
| #224 | `53c603d772` | Refactoring: Extract the trade volume computation |
| #224 | `25dd2e1734` | Ignore trade statistics with impossible values |
| #224 | `03135b6b66` | Trigger offers only on a price feed price |
| #224 | `1babb9deaf` | docs(spec): state the trust model of trade statistics |
| #224 | `860650ab0c` | docs(spec): list the asset listing among the known gaps of trade statistics |
| #225 | `7ff975bf54` | Refactoring: Read reported peers in Peer |
| #225 | `8df0ac94c6` | Ignore peer dates sent in peer exchange |
| #225 | `549513feb6` | docs(spec): state that a re-reported peer keeps its date |
| #227 | `b7ca9b7a93` | Ignore update alerts with an invalid version |
| #226 | `58aaafb77e` | Do not backdate own offers on self-ping disconnect |
| #226 | `b290e48c2b` | docs(spec): do not limit own-address dials to the self-ping test |
| #203 | `38e92e249a` | Sign all fields of an alert |
| #203 | `2c7a7d2d73` | docs(spec): state that earlier versions still receive rejected alerts |
| #207 | `e5f526e1ff` | Do not fail on averages outside the long range |
| #207 | `23ba911378` | Reject trade statistics priced above 10^18 |
| #207 | `7c281a1f39` | fix(core): show a BSQ fee without fiat value if the conversion overflows |
| #207 | `e79ef0a4be` | refactor(core): make the asset trade volume sum testable |
| #207 | `773c7f82d5` | fix: keep sums over trade statistics from wrapping around |
| #183 | `d763a6beb0` | Do not enable the Gradle build cache by default |
| #182 | `1b247694f0` | Make installer evidence and signing tasks configuration cache compatible |
| #239 | `aebf716e78` | refactor: remove the passed small trade amount activation date |
| #239 | `cafe9b4bcf` | refactor: remove the passed Tor v3 node address activation date |
| #239 | `0695ba7fee` | refactor: remove the expired refund legacy claim grace period |
| #239 | `b5b6b5699e` | fix(dispute)!: require the sender key in all dispute messages |
| #239 | `c1c54cddcd` | fix(dispute)!: reject dispute contracts without dispute agent keys |
| #239 | `c7b40f72e2` | fix(dao)!: stop relaying unsigned new block broadcasts |
| #239 | `f75a2a3e3d` | fix(dao)!: answer block requests with signed blocks only |
| #239 | `8ad830d303` | fix(trade): publish trade statistics without the pre-1.4.0 peer check |
| #240 | `27a1bc8509` | test(trade): cover the deposit tx binding of the buyer |
| #240 | `9cbacdbe7f` | fix(desktop): refuse to confirm payment started for a trade with an error |
| #240 | `7613682aa1` | fix(dispute): accept only disputes addressed to this agent |
| #240 | `cb2fbc2ba1` | docs(spec): state that consumed refund receipts are local to each agent |
| #240 | `bb18e99fbe` | refactor(trade): drop the stale strict BSQ swap fee rate check |
| #240 | `c279d27506` | docs: mark the compatibility code for v1.10.11 and earlier |
| #242 | `cb569986b3` | build(tor): update the tor library to Tor Browser 16.0.1 and sandy 1.2.0 |
| #242 | `9b4de21e79` | (docs): Update build.md |
| #243 | `bd7855b13c` | Check own deposit inputs before sending them |
| #243 | `3536d6e575` | Spend only the reserved output in maker deposit |
| #243 | `7ba88506cf` | test: Cover maker deposit reserve lifecycle |
| #243 | `559f569e86` | Find the shared maker fee reserve by the fee tx output |
| #244 | `38d3874aae` | Log failed and skipped tests with full exceptions |
| #244 | `2a8bb49299` | Improve BSQ below-min-amount error message |
| #244 | `642740330e` | Update SoChain block explorer URLs |
| #246 | `fde56f0017` | test(common): cover the hmac check of the payload decryption |
| #246 | `e862479265` | fix(common): compare the mailbox hmac in constant time |
| #245 | `8f3b01ca2e` | fix(core): reject a dust or non-P2WPKH maker change in the deposit tx |
| #245 | `3d46344482` | fix(core): reject a trade amount that leaves a dust offer remainder |
| #245 | `aeb6477ead` | docs(spec): specify the maker change of the deposit tx |
| #247 | `0e29f08e8c` | fix(daemon): refuse to start the gRPC API without a password |
| #247 | `bdb202b81d` | fix(daemon): bind the gRPC API to loopback by default |
| #247 | `43ee477af8` | docs(spec): specify access to the daemon gRPC API |
| #248 | `8b2f951ed1` | fix(dao): map unknown Bitcoin Core script types instead of stopping |
| #249 | `073d2e512f` | fix(tor): stop the start on a part of torrcOptions which is no entry |
| #249 | `7abfdc3936` | fix(tor): stop the start when the torrc file cannot be read |
| #250 | `32c1fa0fbc` | Set version 1.10.12 |
| #251 | `3b78030c72` | refactor(common): share the owner-only mode helper of KeyStorage |
| #251 | `595ac9cf53` | fix(api): keep the wallet locked after a failed unlockwallet |
| #251 | `d68ff63eb6` | fix(trade): reset the multisig coin lock after the DPT broadcast |
| #251 | `c6dc817bc7` | fix(desktop): replace a listed offer only with an offer of its owner |
| #251 | `9dcb5448d4` | fix(common): make the data directory owner-only at every start |
| #251 | `6dfc69cbf0` | fix(p2p): notify each data storage listener on its own |
| #251 | `13d27022d0` | fix(core): count the inventory data at most once in ten seconds |
| #251 | `f801dc4679` | fix(p2p): pass an OutOfMemoryError of a connection on |
| #252 | `4316aedc93` | fix: update release version replacement targets |
| #253 | `4742515e0b` | fix(dispute): refuse a peer dispute that is not about our trade |
| #254 | `16d9dce426` | fix(p2p): drop an oversized message instead of delivering it |
| #255 | `089429a7be` | fix(dao): keep a state hash response below the maximum message size |
| #255 | `3937241c4f` | fix(dao): keep block responses below the maximum message size |
| #257 | `4f30e91b3c` | fix(desktop): use the seller deposit for the seller penalty payout |
| #258 | `8c2730974b` | fix(p2p): key the ignored mailbox list by the payload hash |
| #259 | `e2eb36490d` | fix(desktop): bind payment confirmations to the confirmed trade |
| #260 | `852d9c7abb` | fix(offer): refuse to take an offer with a dust security deposit |
| #261 | `27d5795b5e` | fix(offer): key the trade limit caches by the offer payload hash |
| #262 | `9985ee8914` | fix(trade): clear a stale timeout error after the deposit tx is verified |
| #256 | `2e1f37614a` | fix(p2p): check the message size before reading the message |
| #256 | `d8649caeae` | fix(p2p): enforce limits on received bytes |
| #267 | `b4717b0131` | Move price node ro7nv73awqs3ga2qtqeqawrjpbxwarsazznszvr6whv7tes5ehffopid |
| #266 | `a0f5ae5ec1` | fix(core): keep the delivered min fee rate at startup |
| #263 | `172f50554c` | build(tor): update the Tor library to write one Tor log |
| #264 | `471c0b7ff4` | test(desktop): fix and extend the payment confirmation test |
| #268 | `cb860d9837` | test(offer): give the mock offers a valid security deposit |
| #268 | `d364090791` | test(offer): give the mock offer a payload hash |
| #289 | `8e2b3c0cf0` | fix(build): allow repeated reproducible RPM packaging |
| #289 | `728efc004a` | fix(dao): wait for wallet sync before completing accounting history |
| #289 | `fe13288006` | fix(dispute): compare committed contract data across account reveals |
| #286 | `85d9380283` | fix(dao): treat cycle index 0 as a valid past cycle |
| #287 | `cc5b064969` | test(p2p): cover the size limit of a GetDataResponse |
| #287 | `b6245f9b49` | refactor(p2p): share the size limit check of a GetDataResponse |
| #287 | `3132ea8e38` | fix(p2p): leave an item above the size limit out of a GetDataResponse |
| #290 | `57d5f137de` | test(dispute): keep the wallet service and the failed trades of the mediation test |
| #290 | `5dd6fbf5f5` | fix(dispute): accept a peer dispute for a closed or failed trade from disk |
| #290 | `cf61f73f63` | fix(p2p): cut a GetDataResponse above the largest message to fit |
| #285 | `3737576f63` | fix(p2p): do not use up the seed-only data guard without such data |
| #285 | `71d57b2e38` | docs(p2p): refer the process-once marker to its specification |
| #285 | `82206565a0` | docs(p2p): state the remaining gaps of the process-once guards |
| #291 | `45a37647bb` | refactor(p2p): declare the envelope reader with the fields set in init |
| #291 | `21e76e57a2` | test(p2p): pin the removal of a persisted peer after a rule violation |
| #291 | `e631660535` | docs(p2p): state that seed nodes are updated before the other nodes |
| #291 | `c67c87cea2` | fix(p2p): end without a rule violation when the stream ends in a prefix |
| #273 | `2320dd22f4` | fix(p2p): verify an invalid storage entry signature only once |
| #273 | `2995c10225` | docs(spec): state how easily the failed signature cache is bypassed |
| #273 | `531584b078` | fix(p2p): log a failed signature verification without the payload |
| #273 | `ae74e4922d` | fix(p2p): log failed owner checks of storage entries without the payload |
| #273 | `be5841f217` | fix(p2p): log the key check of a stored storage entry with key hashes |
| #271 | `c222dd7258` | fix(p2p): count an element of a bundle which cannot be parsed |
| #271 | `7e8d148246` | test(p2p): use an element of a removed type as one which cannot be parsed |
| #271 | `549a95d346` | fix(p2p): drop a bundle element of an unknown type without a violation |
| #271 | `71b5acdd2b` | fix(p2p): count a bundle element with a payload of an unknown type |
| #271 | `892b1739a2` | docs(p2p): require a capability for new types nested in a payload |
| #282 | `42184f113e` | fix(trade): sign only the maker's prepared deposit tx as taker |
| #282 | `88a6f696a2` | fix(trade): require the final sequence number on deposit tx inputs |
| #282 | `af36b49d8d` | test(trade): name the deposit sequence tests after the final sequence rule |
| #280 | `7f9c8123c0` | refactor(trade): name the timeout of the maker's take offer handshake |
| #280 | `4398841e75` | fix(trade): ignore a take offer request during an earlier handshake |
| #280 | `0ac1978040` | fix(offer): answer "offer taken" during a take offer handshake |
| #280 | `a18742b32d` | docs(trade): state that a failed taker waits for the handshake timeout |
| #280 | `85491bc39e` | fix(trade): count a take offer handshake as timed out after a clock set back |
| #280 | `d3df33af6b` | fix(trade): keep a take offer handshake pending after a small clock step back |
| #269 | `e7711169c6` | refactor(p2p): define the size limit of a message type in one place |
| #269 | `b0ce053af8` | fix(p2p): check each element of a bundle like a single message |
| #269 | `d3669e675a` | refactor(p2p): remove the sender address check of a bundle element |
| #269 | `9c1c88a1b5` | docs(p2p): state that an oversized block broadcast in a bundle is dropped |
| #269 | `ffc239f0cd` | fix(p2p): make the rule violation of a connection volatile |
| #269 | `8b84d90942` | fix(p2p): drop a bundle element which fails a check without a violation |
| #269 | `12e519da3a` | docs(p2p): separate the bounds of the parse and of the element checks |
| #295 | `2ac6959cc9` | Revert "Remove price node ro7nv73awqs3ga2qtqeqawrjpbxwarsazznszvr6whv7tes5ehffopid" |
| #295 | `3bc1aab9a7` | Add new price node from suddenwhipvapor bsqprcgpyw7z337btcbjxvvtmud4mr6k552piui4xq62qdeqej2cgryd |
| #296 | `ec4170fd11` | fix(price): keep the price of the newest trade when an older statistic arrives |
| #296 | `6b2124e2e7` | feat(price): show the volume weighted BSQ price of the last day |
| #296 | `ab1e0abfee` | docs(spec): specify the market price derived from trade statistics |
| #296 | `7f3dbc3773` | docs(spec): note that one large statistic moves the average BSQ price |
| #296 | `9c23bef438` | Fix layout |
| #297 | `5cd1fe6ba7` | fix(api): check that the payment account matches the offer which is taken |
| #297 | `a68bfacb9c` | feat(payment): add the rules for the texts of a custom payment method |
| #297 | `a8906941a8` | feat(payment): add the custom payment method |
| #297 | `ef34634a1a` | feat(trade): check the revealed account of a custom payment method trade |
| #297 | `4c1568565c` | feat(desktop): add the form and the views of the custom payment method |
| #297 | `43d2eeb627` | docs(spec): specify the custom payment method |
| #297 | `46dbfa369a` | Improve i18n strings |
| #297 | `e902dcb6f8` | fix(i18n): correct the reworded texts of the custom payment method |
| #297 | `414fbe3162` | fix(i18n): double the apostrophe in texts with arguments |
| #297 | `85e8df8d75` | docs(spec): record the decision on the buyer limit and the new warning |
| #297 | `bc03b9ef4b` | feat(desktop): search the currency of a custom payment method account |
| #297 | `1d5562e712` | fix(desktop): restore custom currency selection |
| #297 | `b040b6f5e5` | fix(trade): require the custom seller account |
| #299 | `efa5c1987f` | feat(preferences): store a factor for the price range of BSQ offers |
| #299 | `df11fcf477` | feat(desktop): set the BSQ offer price range factor in the preferences |
| #299 | `53c9b08072` | feat(price): check a price against a range around an average price |
| #299 | `d19b462ba8` | feat(offerbook): hide BSQ offers outside the price range |
| #299 | `14bf6f6d70` | feat(offerbook): hint when the price range hides every BSQ offer |
| #299 | `5f6ce1afdb` | docs(spec): specify the BSQ offer price range |
| #299 | `a98bda3f39` | fix(offerbook): decide the BSQ price range hint on the settled state |
| #299 | `438d61519b` | fix(desktop): show the stored factor when the factor field loses focus |
| #299 | `19c1b6248e` | test(preferences): cover the factor of older data directories |
| #299 | `94078306ad` | docs(spec): correct the BSQ offer price range after the review |
| #299 | `dde1710a7b` | refactor(price): share the predicate of the BSQ offer price range |
| #299 | `9574f95190` | feat(market): apply the BSQ offer price range to the offer book chart |
| #299 | `de23b8b3c2` | docs(spec): extend the BSQ offer price range to the offer book chart |
| #299 | `53254e7542` | refactor(offerbook): move the BSQ price range hint into its own class |
| #299 | `c41efb248b` | feat(market): hint when the price range hides every BSQ offer of the chart |
| #299 | `8d6fe4bbaa` | docs(spec): specify the hint of the BSQ offer price range in the chart |
| #299 | `c21c2b4f7e` | fix(price): keep decimal BSQ bounds inclusive |
| #299 | `cac4bf99b3` | fix(offerbook): defer BSQ hints while popups wait |
| #300 | `f6ee161440` | Update RC translations in 15 locales |
| #298 | `6b14531b80` | fix(trade): check the payment method of revealed payment account data |
| #298 | `ef1b806f4f` | feat(assets): add Tron and Solana address validators |
| #298 | `4318ef9ed2` | feat(payment): add a registry of stablecoins and their networks |
| #298 | `b61c4c9d2f` | feat(payment): add stablecoins as payment methods |
| #298 | `6729ea9b98` | feat(desktop): add the account form and trade steps of stablecoin payment methods |
| #298 | `c86190b0f2` | docs(spec): specify stablecoin payment methods |
| #298 | `52abfd8a37` | feat(offer): take a stablecoin offer without the maker's account witness |
| #298 | `b1a850d9aa` | fix(desktop): do not show the name of a new account as invalid at the start |
| #298 | `18856da7b1` | feat(desktop): search the payment methods when creating an account |
| #298 | `782e68ceb2` | fix(desktop): keep account data on reselection |
| #292 | `edc40cce55` | test(payment): cover the trade limits at a DAO value below the cap |
| #292 | `5424ffb9be` | feat(payment): raise the hard trade amount cap to 0.5 BTC |
| #292 | `8cea22038e` | docs: describe the 0.5 BTC hard cap and the mainnet DAO trade limit |
| #292 | `17a7949e81` | docs: state the order of a release and a DAO vote that raise the trade limit |
| #292 | `ec73ba7456` | docs(trade): update the derivation of the BSQ trade fee ceiling |
| #302 | `5eb4db3d70` | Add popup for new features |
| #303 | `81cef09452` | build: switch Bisq 1 to patched Corretto 21 |
| #303 | `6f8852f36c` | Update tor to 2cd94b971bd77670012b8e9afdaec5d375ab7a43 |
| #304 | `a5127b81f4` | fix(i18n): double the apostrophes of the French texts with arguments |
| #305 | `0b533c72e8` | feat(desktop): select stablecoins and custom payment methods separately |
| #305 | `b1d9a1a185` | feat(desktop): separate the payment method selectors from the account form |
| #305 | `050d68f5cc` | refactor(desktop): share the payment method check of the offer book filters |
| #305 | `5f493719be` | feat(desktop): filter the offer book by all stablecoins |
| #305 | `449e892917` | feat(desktop): filter the trades of a market by fiat and stablecoins |
| #306 | `473e22ecf6` | Update daoStateHash.checkpoints Add bm-addresses-v0008.json Update resources |
| #306 | `b8f0fa7194` | Update denylist |
| #307 | `9586f6d2d1` | fix(i18n): double the apostrophes of Italian, Polish and Thai texts with arguments |
| #307 | `6f052e63c3` | fix(i18n): write the apostrophes of French texts without arguments once |
| #307 | `451302b988` | fix(i18n): give translations the arguments of their English text |
| #307 | `b78bebce05` | test(i18n): check every translation as Res.get shows it |
| #308 | `fc49c56743` | test(dao): audit the newest versioned P2P stores in the resource audit |
| #308 | `dceba5b500` | test(dao): refresh the bundled DAO resource audit for height 976481 |
| #310 | `cb408c4994` | fix(apitest): install xargs in the e2e Bisq image |
| #312 | `bc480f6a83` | Reduce limit for custom payment method to 0.1 BTC |
| #312 | `d6e68e0472` | test(core): follow the custom payment method cap of 0.01 BTC |
| #312 | `dfdedbbecd` | docs: state the custom payment method cap of 0.01 BTC |
| #313 | `6946e7bdf0` | (summary withheld until the seed nodes run 1.10.12) |
| #313 | `bbdd12672c` | (summary withheld until the seed nodes run 1.10.12) |
| #313 | `b1ac314432` | (summary withheld until the seed nodes run 1.10.12) |
| #313 | `f69080670d` | (summary withheld until the seed nodes run 1.10.12) |
| #314 | `d6ed434326` | fix(price): stop a received trade statistic from searching all statistics |
| #314 | `d871aadb6b` | fix(offerbook): hide only BSQ offers whose price is worse for the taker |
| #314 | `12b7391778` | fix(p2p): send a bundle before it exceeds the largest message size |
| #314 | `f5d66b17a4` | docs(spec): state that the buyer's wallet publishes a refused deposit |
| #314 | `08c3afad43` | docs(release): require the seed nodes on v1.10.12 before the next RESULT block |
| #314 | `6b17bb07f9` | docs(spec): state that a substituted key ring hides the peer's dispute |
| #314 | `5f1b50a2f3` | docs(spec): state that the price of a statistic sets its share of the BSQ volume |
| #314 | `5c3ffb48a0` | fix(price): update the price feed once for the statistics of a bundle |
| #314 | `5ecafb8a77` | fix(offerbook): turn the BSQ price range off with the largest factor |
| #314 | `b13646f994` | docs: correct details found in the review of the fix commits |
| #314 | `5539e80623` | test(p2p): send the request after an early bundle with the interval timer |
| #314 | `dbbeaf1012` | fix(offerbook): name the off state of the BSQ price range in its hint |
| #311 | `fbd73384f4` | test(apitest): check that the api refuses custom and stablecoin accounts |
| #311 | `8f0b810014` | test(apitest): check the 0.5 BTC hard cap of an offer |
| #311 | `f1323c558c` | test(apitest): check that takeoffer refuses a mismatched account |
| #311 | `05dd29ad04` | test(apitest): check that the api refuses an incorrect password |
| #311 | `2398da8f62` | refactor(apitest): run docker commands through one helper |
| #311 | `069e8c1d3f` | test(apitest): add helpers to stop and restart a stack daemon |
| #311 | `de59d585ba` | refactor(apitest): split runV1Trade into take and finish steps |
| #311 | `21c8fb6164` | test(apitest): check that a wrong unlock password keeps the wallet locked |
| #311 | `29054b93bf` | test(apitest): check that an edited offer replaces the peer's listing |
| #311 | `b0532d9050` | test(apitest): check that an offer with the minimum deposit is takeable |
| #311 | `1e1f49a905` | test(apitest): check mailbox delivery to a restarted seller |
| #311 | `625644634f` | test(apitest): check that vote results survive a restart and a resync |
| #311 | `d3816b2451` | test(apitest): allow the e2e runner to skip the shared-stack phase |
| #311 | `804a045f7d` | test(apitest): add a compat run with the release on the maker side |
| #311 | `086347c29a` | docs(apitest): describe the shared-stack switch and the compat peers |
| #311 | `041c17bfcb` | test(apitest): retry the ignore-deny-list offer until mediators arrive |
| #311 | `a20290bde3` | fix(apitest): start the daemons after the seed node accepts connections |
| #311 | `276880967c` | test(apitest): check that getbtcprice answers from the cache |
| #311 | `6ab57b9b5b` | test(apitest): check the fee rate defaults and the trading floor |
| #311 | `0950411289` | test(apitest): check that the daemon refuses to start without a password |
| #311 | `25dad50d28` | test(apitest): check that unusual output scripts keep the DAO in sync |
| #316 | `eae0b8099e` | test(price): check the BSQ price with a statistic of 0.01 BTC at 1 sat |
| #316 | `fa68fecf5e` | test(price): check that a received statistic updates only its currency |
| #316 | `cc83f11c4a` | test(price): check that statistics received together are dumped once |
| #316 | `d30fc95c77` | test(price): check that prices from trade statistics keep the feed prices |
| #316 | `ebc2d7b14d` | test(offerbook): check that the BSQ price range hint names the off state |
| #317 | `e25b682eb5` | fix(desktop): discard removed payment account forms |
| #317 | `8b99ddca82` | fix(trade): enforce the stablecoin payment rail for API buyers |
| #317 | `df428a0d30` | fix(trade): watch the payout address while the wallet rescans |
| #317 | `0b32c7b0cd` | fix(trade): retry desktop payment confirmation without rewinding the trade |
| #317 | `91b099732e` | fix(p2p): keep the mailbox hash cache separate from legacy UIDs |
| #317 | `930e5ab1d5` | fix(build): select the release source and Corretto for the RPM builder |
| #317 | `47c00ab573` | fix(build): package the distribution as bisq with a reproducible RPM epoch |
| #317 | `e24ee36e97` | test(apitest): give the e2e daemons fee data from a local stub |
| #317 | `ffcad9d024` | test(dao): check all-ignore vote results against each peer version |
| #317 | `5a8204640c` | fix: omit native Tor startup log excerpts from support archives |
| #317 | `4d3471df40` | test(api): cover seller payment details in trade replies |
| #317 | `89586ced12` | perf(dao): stop signing blocks once a response is full |
| #317 | `e3e15d7dd1` | docs(p2p): describe the bundle limits enforced by the candidate |
| #317 | `4bd4619711` | docs(tor): clarify the separator required in torrc files |
| #317 | `75d9abfb9b` | fix(market): keep the payment filter when reselecting a currency |
| #317 | `00528474b1` | fix(trade): restart the payment message states on a retry after a failed send |
| #317 | `c0533fa12d` | test(apitest): stop the daemon start when the fee stub does not answer |
| #317 | `514c5c7c71` | fix(desktop): offer the next payment confirmation retry after the timeout |
| #317 | `a388cf97ad` | fix(trade): keep the trade state on a retry of the payment confirmation |
| #317 | `8908a604de` | fix(api): refuse a payment confirmation once payment started was confirmed |
| #317 | `4378608316` | fix(desktop): hide seller details of another rail in the trade details |
| #317 | `7334dea69c` | docs(trade): specify the repeated payment started confirmation |
| #317 | `ebffc0ed74` | fix(tor): accept a tab between key and value in the torrc file |
| #318 | `549c053f55` | docs: add the draft release notes of 1.10.12 |
| #319 | `b68424a864` | fix(trade): accept BSQ swap requests only for BSQ swap offers |
| #319 | `46370c0a6c` | docs(trade): correct the BSQ swap request admission spec |
| #319 | `4dcaaa9c27` | test(trade): check the BSQ swap request admission with real offers |
| #319 | `bfeba866c7` | fix(trade): require a sell offer for a buyer's BSQ swap request |
| #321 | `43b70a7e83` | fix(build): update the RPM builder tag default with the release version |
| #321 | `d5e0206016` | fix(build): give the reproducible RPM a package name of its own |
| #322 | `d0b47cd142` | fix: bound DSA public key size accepted from the wire |
| #322 | `82549c6cf1` | fix(crypto): apply the DSA key bounds at the verification, not at parsing |
| #322 | `432231703d` | fix(dao): bound the lite node block broadcast history |
| #322 | `1aa779e110` | fix(support): isolate queued messages and validate closed trade types |
| #322 | `e49e5231dd` | fix(locale): bound currency lookups for historical trade statistics |
| #322 | `87ce788258` | docs(network): specify accepted DSA key bounds |
| #322 | `962172ffe5` | fix(crypto): report a failed DSA verification computation as CryptoException |
| #322 | `04ba4630b4` | fix(statistics): bound persisted records and reject invalid additions |
| #324 | `c0dccbf7df` | fix(trade): check a received trade statistic as earlier versions do |
| #324 | `2ab7dafb5e` | fix(statistics): apply the size limit of a trade statistic at the store |
