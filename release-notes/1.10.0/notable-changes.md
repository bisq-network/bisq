# Bisq 1.10.0 Notable Changes

This file filters the full release notes down to the most relevant engineering and product changes. Each section gives a short summary first and then lists representative related commits.

## Trade And Transaction Validation Hardening

Bisq v1 trade handling now validates peer-supplied data much earlier and across more protocol steps. The release adds and reorganizes validation for deposit transactions, payout transactions, delayed payout transactions, mediated payouts, trade amounts, trade prices, maker/taker fees, miner fees, multisig public keys, serialized transactions, raw inputs, signatures, payment-account hashes, and canonical transaction structure.

Related commits:
- [797836cc9d](https://github.com/bisq-network/bisq/commit/797836cc9d3ef0891a1bd349e18127fe252eb860) - Validate peer deposit inputs during maker/taker deposit setup.
- [89171806e8](https://github.com/bisq-network/bisq/commit/89171806e8d3ae7a74c34fe6e40948245e1bf761) - Merge the initial exploit-fix validation patch.
- [3b5ae98849](https://github.com/bisq-network/bisq/commit/3b5ae98849d93ec1570066da8c289ec627a4cb9b) - Require peer inputs to use the expected P2W format.
- [844aec628f](https://github.com/bisq-network/bisq/commit/844aec628f1c80d6f53fc9dbf1098395ecb04b33) - Prevent invalid delayed payout transactions.
- [04c28c3ee7](https://github.com/bisq-network/bisq/commit/04c28c3ee7e52a7d61bfdceae866ff6a028d4298) - Add integrity validation for trade messages.
- [8b1975e761](https://github.com/bisq-network/bisq/commit/8b1975e761f607f8cab212ff73e051ccfc4fdf34) - Add the central TradeValidation class.
- [fa6f825ea9](https://github.com/bisq-network/bisq/commit/fa6f825ea9dba62ef5d7b3e269361d2781f094fe) - Expand TradeValidation checks.
- [5d92623db6](https://github.com/bisq-network/bisq/commit/5d92623db6f7746c3cf1a81651a35e05ba4826b4) - Apply validation across more trade tasks.
- [51a1b83c54](https://github.com/bisq-network/bisq/commit/51a1b83c54c9756b6dfd228f6f22c94a1cb8d7be) - Add payout transaction validation.
- [c9c88d2157](https://github.com/bisq-network/bisq/commit/c9c88d21572ebe214c7153627a62336a0494cc24) - Add mediated payout transaction validation.
- [87bed257d5](https://github.com/bisq-network/bisq/commit/87bed257d5744977bc9ba78b9aea8a47132e33ae) - Add deposit transaction validation.
- [3b1bdf8d85](https://github.com/bisq-network/bisq/commit/3b1bdf8d854bb004fe28679b33612aebab4164ec) - Reorganize validation classes into focused components.
- [ec7d41a730](https://github.com/bisq-network/bisq/commit/ec7d41a73082b5bcd8361156fef3da460af3a6ec) - Validate canonical deposit transaction fields at final boundaries.
- [0140ca187f](https://github.com/bisq-network/bisq/commit/0140ca187f59413d96c472a754205e07ffc5b0e1) - Add extra multisig public key checks.
- [4557341e1e](https://github.com/bisq-network/bisq/commit/4557341e1e4d9d0f43207ee66420870758b995bb) - Clean up stale validation references and tests.
- [512dbc8c4d](https://github.com/bisq-network/bisq/commit/512dbc8c4d49e89094c6f0ed0824f153e083efc2) - Reject legacy UTXOs at deposit funding.
- [9e5d431e62](https://github.com/bisq-network/bisq/commit/9e5d431e62a9af4130c76390137749d782319132) - Follow up payment-account hash validation review.
- [84c64c2901](https://github.com/bisq-network/bisq/commit/84c64c2901a601481126a4a4fe1dcf4673ec097c) - Clean up mediated payout validation return behavior.
- [7979963544](https://github.com/bisq-network/bisq/commit/79799635441afea5ef2bc2bfe5b4316e356b8038) - Harden payout address validation.

## Offer Safety, Limits, And Trade Failure Handling

The release lowers risk around offer taking and trade bounds. Prices are constrained to a 25% maximum deviation, the maximum trade amount is limited to 0.125 BTC, maker change is validated before taker signing, offer availability responses are checked against the expected peer, BSQ swap trading can be disabled independently by filter, and failed take-offer requests now release the reserved open offer.

Related commits:
- [4877e4b94e](https://github.com/bisq-network/bisq/commit/4877e4b94e8a94d38c8cd3078b37f167cf250785) - Reduce maximum price deviation to 25%.
- [fd4b152d5e](https://github.com/bisq-network/bisq/commit/fd4b152d5ea5e17f2cf820ad452cc7e7474b335d) - Enforce maximum price deviation in offers and trades.
- [0ac4977670](https://github.com/bisq-network/bisq/commit/0ac497767013a1d6403d5ffcdf9548cb1512815c) - Limit maximum trade amount to 0.125 BTC.
- [17771eb9e2](https://github.com/bisq-network/bisq/commit/17771eb9e274c6aaff029a111db9db3d15f9b9e2) - Validate maker change before taker signs the deposit transaction.
- [44d539854d](https://github.com/bisq-network/bisq/commit/44d539854d8cb8e73ab9f9fbd68ed83368bc67fe) - Validate offer availability response peer identity.
- [439947e230](https://github.com/bisq-network/bisq/commit/439947e2309a779908b1efca8a424f82b0d5beb8) - Add BSQ swap kill switch support to the filter.
- [ff0536270b](https://github.com/bisq-network/bisq/commit/ff0536270bf765db2e8fc09a8a4a2e3de2b615f4) - Unreserve an open offer when a take-offer request fails.

## Network, HTTP, And Message-Origin Hardening

P2P messages and acknowledgements now have stronger sender-address and sender-signature checks. HTTP clients have stricter URL safety and fail-closed behavior, with clearnet access limited to explicit development flags. This reduces exposure to malformed or unexpected network inputs.

Related commits:
- [0b938fedfd](https://github.com/bisq-network/bisq/commit/0b938fedfd1c1b78ba7c13f50c95d60920d7cae8) - Verify encrypted message sender addresses at the network layer.
- [5c60120798](https://github.com/bisq-network/bisq/commit/5c601207982f6273794f755f9c37a695db13279a) - Verify sender signature public keys for supported payloads.
- [eb599f732b](https://github.com/bisq-network/bisq/commit/eb599f732b39771feb1714bd3e39847f21587906) - Harden trade message integrity handling.
- [5b3a805762](https://github.com/bisq-network/bisq/commit/5b3a805762240d4700fc926b9619d92ec1faa26b) - Harden HTTP client behavior.
- [69016899a9](https://github.com/bisq-network/bisq/commit/69016899a985492f1a7a8ca30d4a26e1e5146446) - Add controlled clearnet HTTP support for development.
- [b228511853](https://github.com/bisq-network/bisq/commit/b228511853f635f0df9a391d8ab6fc68950ce7ef) - Follow up sender public-key verification review.

## User-Facing UX, Chat, And Support Changes

Several user-visible annoyances and failure modes are addressed. Disk-space warnings no longer repeat, a cold-storage reminder is shown for high wallet balances, the exit warning popup is removed, chat scroll/window sizing issues are fixed, update text is scrollable, trader chat listener cleanup is improved, and dispute chat file/log transfers are removed.

Related commits:
- [488fd0a406](https://github.com/bisq-network/bisq/commit/488fd0a406b60b6fcdd495e1f74bf132dca8caa9) - Show disk-space warning only once.
- [94e3f1a13c](https://github.com/bisq-network/bisq/commit/94e3f1a13c5748e97a063f2e58a0f8cc7c17249e) - Add high-balance wallet cold storage reminder.
- [623ce41c31](https://github.com/bisq-network/bisq/commit/623ce41c31038f2004407f9d13433e8ba1debdc5) - Remove the exit warning popup.
- [2970c7696d](https://github.com/bisq-network/bisq/commit/2970c7696d6d7b1a59a947cdeac0415d93773c65) - Disable dispute log file transfer.
- [dae777386d](https://github.com/bisq-network/bisq/commit/dae777386d7431e82b123e78dcb94e6ca8a79d4d) - Remove dispute log transfer and chat attachments.
- [9cafc7c9f7](https://github.com/bisq-network/bisq/commit/9cafc7c9f7ce7b12859e84e4960fd2c9587fcde7) - Fix chat scroll OOM and window sizing.
- [bd505b7425](https://github.com/bisq-network/bisq/commit/bd505b742599f583b67a65e0a2ec24473de364ea) - Fix trader chat y-position listener cleanup.
- [728654ccb1](https://github.com/bisq-network/bisq/commit/728654ccb102dafb6c0da4293878885bfccbfa54) - Disable the dev-alert remove button when no alert is selected.
- [a4cd0d9a2a](https://github.com/bisq-network/bisq/commit/a4cd0d9a2a3553b45a50465182b135a7ec0f9588) - Show update text in a scroll pane.

## Payment Accounts, Wallet Exports, And Payment Methods

Payment-account handling receives correctness and safety fixes, including deserialization, Swish validation, payout-address validation, BSQ wallet CSV mapping, and payment method/currency list cleanup. The range also includes Bisq 2 account/signature-key export work, while the export button is hidden until the related Bisq 2 rollout is ready.

Related commits:
- [59908094ae](https://github.com/bisq-network/bisq/commit/59908094ae1bc7ffc78e8a5e056931aa6fff8ccd) - Add account and signature-key export support for Bisq 2 import.
- [5d974b4076](https://github.com/bisq-network/bisq/commit/5d974b4076e5982023f3f57410dcb38e8157107d) - Fix payment account deserialization.
- [c077d06e41](https://github.com/bisq-network/bisq/commit/c077d06e4118797372cf65a9e04b96d5741c58d5) - Fix Swish validator initialization.
- [aba66c39d6](https://github.com/bisq-network/bisq/commit/aba66c39d69110834f2c7f9083bf337930f15c61) - Fix BSQ wallet CSV transaction type mapping for swaps and issuance.
- [f882fc3556](https://github.com/bisq-network/bisq/commit/f882fc35560829aa2019f2e785e9a8d5799f9a23) - Remove HRK from active currency lists.
- [80f5ed863c](https://github.com/bisq-network/bisq/commit/80f5ed863cbc9ed88131556ac7905817c764450c) - Deprecate VERSE for new payment-account creation.
- [39e6caa5c1](https://github.com/bisq-network/bisq/commit/39e6caa5c1c4684c0fb49ff5b13f79630e473b5c) - Harden payout address validation.

## DAO, Burning Man, API, And Service Updates

The release adds bridge/gRPC service work, account timestamp support, DAO snapshot/resync fixes, refreshed DAO/network resource snapshots, Burning Man address-list and receiver validation, updated node/mediator/refund-agent metadata, bitcoind RPC changes, and API/CLI cleanup. Version-tagged resource files are omitted for this release so older seed nodes do not trigger avoidable resource requests.

Related commits:
- [397b5a26a9](https://github.com/bisq-network/bisq/commit/397b5a26a91de669e0507831f0d74bab7408a1f7) - Merge the bridge module and gRPC API work.
- [3d4cbe8a83](https://github.com/bisq-network/bisq/commit/3d4cbe8a8347c13abab1c4f081b68a3fb8ca0683) - Add AccountTimestampGrpcService.
- [a49488a3c8](https://github.com/bisq-network/bisq/commit/a49488a3c81f40ffec14eed034f864db99c4a272) - Fix applying DAO snapshots during resync.
- [81d375ea64](https://github.com/bisq-network/bisq/commit/81d375ea643f611a1c8690bd3c58cf57046a1c78) - Add bitcoind v29.2 support.
- [93911b999e](https://github.com/bisq-network/bisq/commit/93911b999e0745b6ea6da89bcad98e93aabc7c83) - Add Burning Man address-list checkpoint support.
- [66f04a4d77](https://github.com/bisq-network/bisq/commit/66f04a4d7756fff5d473ad19b1825a004a352b3d) - Refresh DAO and network resource snapshots.
- [c2a4c8d2de](https://github.com/bisq-network/bisq/commit/c2a4c8d2de4dffd936c76dee8bb62aae78ce64a1) - Remove version-tagged resources for seed-node compatibility.
- [9d89d01a4e](https://github.com/bisq-network/bisq/commit/9d89d01a4ecfb5fdbf00f4d222c383ba7f98935d) - Use payment-account-id parameter naming consistently.
- [72e178920a](https://github.com/bisq-network/bisq/commit/72e178920ac7e63413784674948b5bf969694697) - Improve CLI take-offer available-offer lookup.
- [be58e349c5](https://github.com/bisq-network/bisq/commit/be58e349c5f1faf0987dc71a562af2fa93e3c4b7) - Allow filtering BTC addresses with positive balances.
- [3803c3c7e3](https://github.com/bisq-network/bisq/commit/3803c3c7e3d27fd8dd390615a199d205633a22db) - Allow multiple btcNodes command-line values.
- [93469ebd5f](https://github.com/bisq-network/bisq/commit/93469ebd5fe21f5f1c3184efa8830f9a1d5ca7c4) - Remove deprecated getmyoffer API/CLI command.

## Runtime, Dependencies, And Network Stack

The build/runtime stack moves to Java 21 with JavaFX 21 and broad dependency updates. bitcoinj, bitcoind, netlayer/Tor, Kotlin, logging, protobuf, Jackson, Guava, Lombok, Jersey, JUnit, Hamcrest, and other libraries are updated or aligned. macOS releases support both Apple Silicon and Intel Macs with architecture-qualified DMGs and release hash files, the in-app updater selects the matching macOS installer architecture, JavaFX variants publish native architecture attributes, and JavaFX/JFoenix packaging receives follow-up fixes.

Related commits:
- [cb0c7ba2f1](https://github.com/bisq-network/bisq/commit/cb0c7ba2f1437ec6c835108f7f13a8680d273fe9) - Merge broad dependency and JavaFX update work.
- [b6170834c2](https://github.com/bisq-network/bisq/commit/b6170834c285ea8f18452c67e8e52d4dde9201e9) - Update bitcoinj to include the P2WPKH verification fix.
- [936eeb2e4f](https://github.com/bisq-network/bisq/commit/936eeb2e4f25fed634e43b5c041c037b9e7896e4) - Use updated bitcoinj and metadata.
- [5af6c77c06](https://github.com/bisq-network/bisq/commit/5af6c77c062da543c6ecbed8e58cde75ccc0bdd5) - Update netlayer/Tor target.
- [d122fdd733](https://github.com/bisq-network/bisq/commit/d122fdd73347344114010dadea9c33e972c0cc48) - Align Kotlin version and dependency verification metadata.
- [5970e84f75](https://github.com/bisq-network/bisq/commit/5970e84f75a55751525c5e81b121e7f9e12271a2) - Update logback/slf4j and bitcoind target.
- [62642fb2cd](https://github.com/bisq-network/bisq/commit/62642fb2cd7d745ba09f4f0970d239dfd922cc3f) - Update Guava and Lombok.
- [7f952fb1e8](https://github.com/bisq-network/bisq/commit/7f952fb1e8a4537acc6ae7fb380baa7ae03f4f87) - Update bitcoind and verification metadata.
- [1f6ebda67a](https://github.com/bisq-network/bisq/commit/1f6ebda67a9bdc98c913626d6b9acb873a5bde2f) - Fix packaged macOS JavaFX/JFoenix exports.
- [081124c57f](https://github.com/bisq-network/bisq/commit/081124c57f87e600c2b4c865d8b31c41dab10b9c) - Update netlayer to the latest target in this range.
- [3fce2c0bf5](https://github.com/bisq-network/bisq/commit/3fce2c0bf5106a053daaddff0555b213518ad2df) - Add dual-architecture macOS release packaging support.
- [3ad2682a8c](https://github.com/bisq-network/bisq/commit/3ad2682a8c4606a93907a5600fc46ccbd8d58f4e) - Publish native attributes for JavaFX variants.
- [370b48b37c](https://github.com/bisq-network/bisq/commit/370b48b37c1c1cd57a9c85d78f3b24b07df180aa) - Select the correct macOS installer architecture in the updater.

## Release Integrity And Reproducible Builds

Release verification is expanded substantially. The release process now includes Java payload and installer manifests, evidence bundles, Gradle wrapper verification, dependency signature reporting, checksum fallback controls, pinned CI actions and runner images, a Linux release-builder image, installer evidence workflows, deterministic Linux package handling, reproducible-build documentation, deterministic macOS release finalization fixes, and a manual GitHub release-readiness check for assets, download URLs, and signer keys.

Related commits:
- [46cf4a096d](https://github.com/bisq-network/bisq/commit/46cf4a096d2dabce553c5d3fba7d965811dbfc8b) - Add Gradle dependency signature verification reporting.
- [570bd90ad5](https://github.com/bisq-network/bisq/commit/570bd90ad5e5312320ac238acf86c1859ddd0d25) - Improve reproducible build system checks.
- [03b23a1ac3](https://github.com/bisq-network/bisq/commit/03b23a1ac32ed55bf426f94fcd73aaf1df4c9c1e) - Trust and verify the Gradle source distribution signature.
- [d9ac709d12](https://github.com/bisq-network/bisq/commit/d9ac709d126c0dad3fba6434feaf64c877eea39e) - Add CVE scan task.
- [0fb6e72c53](https://github.com/bisq-network/bisq/commit/0fb6e72c532b075fd2d667880c7fbe90204c3bb1) - Merge reproducible Debian/RPM release-builder work.
- [cf70be1d98](https://github.com/bisq-network/bisq/commit/cf70be1d98668a2ee9cb612cf60020205d4e6a6c) - Add repository SECURITY.md.
- [5022ee6cc5](https://github.com/bisq-network/bisq/commit/5022ee6cc5184ff7f8eee27c673dd739298c9866) - Add Java payload release manifest generation.
- [6296ac5c65](https://github.com/bisq-network/bisq/commit/6296ac5c65304445e8a755b243b7950fdf681101) - Add release payload manifests and verification.
- [a6210067e1](https://github.com/bisq-network/bisq/commit/a6210067e164fab5ab7f42efe884aeef7fda27dd) - Add aggregate release verification task.
- [55b732a343](https://github.com/bisq-network/bisq/commit/55b732a3435aff46001ab0c1f150b3abaf0a9092) - Add release evidence bundle support.
- [fc7f809585](https://github.com/bisq-network/bisq/commit/fc7f809585b75076a6d6ea7be9ea86e624032db6) - Add installer manifest verification.
- [423a2f5986](https://github.com/bisq-network/bisq/commit/423a2f5986932def92c4ecd2653d2ac2868eb6bd) - Compare release evidence bundles.
- [31a7cad15f](https://github.com/bisq-network/bisq/commit/31a7cad15f41677c40b5748757142ab18bdda8c1) - Repack Linux RPM installers deterministically.
- [f245f8e46a](https://github.com/bisq-network/bisq/commit/f245f8e46a300bb9481fb75e54c3d1a91bbd7e5b) - Use deterministic source and target paths for macOS DMG renaming.
- [544b0337b6](https://github.com/bisq-network/bisq/commit/544b0337b676edd6d06c6330c008a6f15dc8ce6d) - Reconcile the dependency checksum fallback allowlist.
- [c2850685c5](https://github.com/bisq-network/bisq/commit/c2850685c5c92762d32554b441134b9547f94913) - Add the GitHub release-readiness check.
