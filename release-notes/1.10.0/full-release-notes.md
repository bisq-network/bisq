# Bisq 1.10.0 Release Notes

These notes are written in the same practical style used by Bitcoin Core release notes: user and operator impact first, followed by a complete auditable commit inventory.

Commit range: from `797836cc9d3ef0891a1bd349e18127fe252eb860` through `284fe9d14ab155c6e6d94e19e664c576ae4f405d`, generated with `git log --reverse 797836cc9d3ef0891a1bd349e18127fe252eb860^..HEAD`. This includes the starting commit `797836cc9d` and ends at `284fe9d14a`. The inventory includes merge commits as separate entries because they are part of the release history.

- Total commits: 739
- Non-merge commits: 540
- Merge commits: 199
- Generated on: 2026-05-15

## Compatibility Notes

- Bisq version is set to `1.10.0`.
- Release builds use Gradle `8.9` and an Azul Java `21.0.6` toolchain. JavaFX is updated to `21.0.10`.
- The maximum trade amount is limited to `0.125 BTC` and offer/trade prices are constrained to a `25%` maximum deviation.
- Dispute chat file attachments and dispute log file transfer are removed.
- VERSE is deprecated for new payment-account creation, and HRK is removed from active currency lists.

## Notable Changes

### Trade, Wallet, And Offer Safety

This release contains a large validation hardening pass for Bisq v1 trades. Peer-provided inputs, deposit transactions, payout transactions, delayed payout transactions, mediated payout transactions, fees, trade amounts, prices, multisig public keys, and payment-account hashes are checked earlier and more consistently. Invalid peer data is rejected closer to the point where it enters the trade flow.

The release also rejects legacy UTXOs for deposit funding, bounds and deduplicates peer-supplied inputs, validates canonical deposit transaction shapes at final boundaries, re-verifies multisig outputs before payout signing, hardens BSQ swap arithmetic, hardens Monero transaction proof handling, and unreserves offers when a take-offer request fails.

### Network, HTTP, And Privacy Hardening

P2P message handling now verifies sender addresses and sender signature public keys at the network layer for supported payloads and acknowledgements. HTTP client handling is hardened with stricter URL safety checks and safer fail-closed behavior, while clearnet HTTP remains available only behind explicit development-mode flags.

Dispute log transfer and chat attachments are removed. This reduces the amount of local support data that can be moved through the application and simplifies the dispute chat surface.

### User Interface And Payment Account Fixes

Several visible user-facing fixes are included: disk-space warnings are shown only once, the exit warning popup is removed, high-balance wallets receive a cold-storage reminder, chat scroll/window sizing problems are fixed, update messages are shown in a scroll pane, trader chat listener cleanup is improved, and default user values avoid a startup/test null-pointer failure.

Payment-account handling is safer and more robust. Swish account creation no longer fails due to missing validator initialization, payment-account deserialization is fixed, payout address validation is hardened, BSQ wallet CSV transaction type mapping is corrected, and payment method/currency lists are cleaned up.

### DAO, Burning Man, API, And Services

The release includes bridge module work with gRPC APIs, account timestamp service support, DAO resync/snapshot fixes, refreshed DAO/network resource snapshots, bitcoind RPC migration work, Burning Man address list and delayed payout receiver validation, and updates to mediator/refund-agent/network node metadata. Version-tagged resource files are intentionally not bundled in this release to avoid extra requests against seed nodes that still run older versions.

API and CLI changes include consistent `payment-account-id` naming, improved `findAvailableOffer`, API-level filtering for BTC addresses with positive balances, support for multiple `btcNodes` command-line values, and removal of the deprecated `getmyoffer` command.

### Build, Dependencies, And Release Integrity

The build and runtime stack is updated to Java 21/JavaFX 21 with broad dependency updates, newer bitcoinj/bitcoind/netlayer targets, Kotlin alignment, and updated test/build libraries. CI and packaging receive JVM export fixes for JavaFX/JFoenix on macOS, JavaFX variants publish native architecture attributes, and macOS releases support both Apple Silicon and Intel Macs with architecture-qualified DMGs and hash outputs.

Release verification is significantly expanded. The build now creates and verifies Java payload and installer manifests, records release and installer evidence bundles, pins GitHub Actions and runner/JDK versions, verifies Gradle wrapper inputs, adds dependency signature reporting, adds CVE scanning, documents reproducible release verification, hardens Linux release-builder evidence for Debian/RPM packages, and adds a manual GitHub release-readiness check for uploaded assets, download URLs, and signing keys.

## Complete Commit Inventory

Rows are ordered by `git log --reverse` over the release range. Some commit dates predate the starting commit because those side-branch commits were merged into the release history after the starting point.

| Date | Commit | Type | Summary | Author |
| --- | --- | --- | --- | --- |
| 2025-04-07 | [5bf06998b8](https://github.com/bisq-network/bisq/commit/5bf06998b8f15ff12fe075bd6d628ba92720e086) | Commit | fix: use payment-account-id param name consistently | thecockatiel |
| 2025-04-07 | [318abd907f](https://github.com/bisq-network/bisq/commit/318abd907f4c26a3d5e1fd9dc725779d65517570) | Commit | fix: improve findAvailableOffer | thecockatiel |
| 2025-04-08 | [509f4ec9eb](https://github.com/bisq-network/bisq/commit/509f4ec9eb11470745fa6b325277243292720bab) | Commit | fix: my offer check and bsq offer find | thecockatiel |
| 2025-07-03 | [08e4976399](https://github.com/bisq-network/bisq/commit/08e4976399723526a5cf1380d03fb3f799efb160) | Commit | Update seed nodes for inventory monitor | HenrikJannsen |
| 2025-07-08 | [d7bf611dac](https://github.com/bisq-network/bisq/commit/d7bf611dacb1bcc68cb0ff3cc497de6575561c19) | Commit | API: allow filtering BTC addresses with positive balance | helixx87 |
| 2025-07-06 | [31a03abb29](https://github.com/bisq-network/bisq/commit/31a03abb2950f8f5063bc7a333c3322844ec27f1) | Commit | feat: Update mediator addresses | M. Caviar |
| 2025-07-08 | [e38a505077](https://github.com/bisq-network/bisq/commit/e38a5050771d7ad72dc302dfd5883d2398198377) | Commit | Fix: Allow multiple btcNodes in command-line argument | mustardcaviar |
| 2025-07-28 | [e504c55aee](https://github.com/bisq-network/bisq/commit/e504c55aee49947dc3e8c6321f46837b9d45a66b) | Commit | Add bridgePort to Config | HenrikJannsen |
| 2025-07-28 | [470aef42e3](https://github.com/bisq-network/bisq/commit/470aef42e34b3d85e7db9aa61cf0f077cf4cda0a) | Commit | Add shared bridge utility helpers | HenrikJannsen |
| 2025-07-28 | [93975313c7](https://github.com/bisq-network/bisq/commit/93975313c7bc0a07c525f4576324b39225448442) | Commit | Adjust bridge visibility and override points | HenrikJannsen |
| 2025-07-28 | [e8302aab74](https://github.com/bisq-network/bisq/commit/e8302aab74bd3dabcb50e6bbff671fb33ca645ec) | Commit | Add bridge module and grpc API | HenrikJannsen |
| 2025-07-31 | [45824d0875](https://github.com/bisq-network/bisq/commit/45824d0875d7cd5f69953b078561cba5306cc651) | Commit | Remove node_op_324 btc node | suddenwhipvapor |
| 2025-08-11 | [e495399b05](https://github.com/bisq-network/bisq/commit/e495399b05d089bccdc92d09dc8e2c2930d4d093) | Commit | Bump actions/checkout from 4.2.2 to 5.0.0 | dependabot[bot] |
| 2025-07-28 | [20a8c4ee69](https://github.com/bisq-network/bisq/commit/20a8c4ee696c85a7fb2f8ce6038660f72ab0206f) | Commit | Apply bridge code-review suggestions | HenrikJannsen |
| 2025-08-26 | [2b542e04d9](https://github.com/bisq-network/bisq/commit/2b542e04d93091d855b75664442cf7e2a19bfd86) | Commit | Bump actions/setup-java from 4.7.1 to 5.0.0 | dependabot[bot] |
| 2025-09-08 | [70900e2a76](https://github.com/bisq-network/bisq/commit/70900e2a7624d2840ecf788b9641858101ae9d45) | Commit | Bump actions/stale from 9.1.0 to 10.0.0 | dependabot[bot] |
| 2025-09-17 | [4c32a4fb99](https://github.com/bisq-network/bisq/commit/4c32a4fb9997b8df88cd4f21fd6e6f23ba3cc124) | Merge | Merge PR #7505: v1.9.21 | Alejandro García |
| 2025-09-17 | [223403a7d6](https://github.com/bisq-network/bisq/commit/223403a7d64683f30f9d23a86db6070769624981) | Merge | Merge PR #7476: update seed nodes for inventory monitor | Alejandro García |
| 2025-09-17 | [397b5a26a9](https://github.com/bisq-network/bisq/commit/397b5a26a91de669e0507831f0d74bab7408a1f7) | Merge | Merge PR #7492: add bridge module | Alejandro García |
| 2025-09-17 | [e2eb40f29d](https://github.com/bisq-network/bisq/commit/e2eb40f29dcd9c615ba87adbb943b64c3ad49c69) | Merge | Merge PR #7493: remove node op 324 btcnode | Alejandro García |
| 2025-09-17 | [bc4d3c6bc1](https://github.com/bisq-network/bisq/commit/bc4d3c6bc16f492afa6436a23ee34e9667e04805) | Merge | Merge PR #7497: checkout 5.0.0 | Alejandro García |
| 2025-09-17 | [21d624697c](https://github.com/bisq-network/bisq/commit/21d624697c21e44e22e8ad2bf484df254a6d9b33) | Merge | Merge PR #7500: setup java 5.0.0 | Alejandro García |
| 2025-09-17 | [5f5938be61](https://github.com/bisq-network/bisq/commit/5f5938be613e7ee8cd9be1820b09d052af69f2d4) | Merge | Merge PR #7504: stale 10.0.0 | Alejandro García |
| 2025-09-17 | [de24c4648c](https://github.com/bisq-network/bisq/commit/de24c4648ce1041756efd63e5a88b5d267c0d4ed) | Commit | api: Remove deprecated getmyoffer | Alva Swanson |
| 2025-09-29 | [2391fc53d5](https://github.com/bisq-network/bisq/commit/2391fc53d50a86c741fbaf2d18e9aecad22b29e6) | Commit | api: Refactor CoreOffersService findAvailableOffer | Alva Swanson |
| 2025-10-04 | [9ac8067430](https://github.com/bisq-network/bisq/commit/9ac80674301403cc61ec3d4cb264d0f9deeba433) | Commit | api: Move BTC positive balance filtering to API-level | Alva Swanson |
| 2025-10-06 | [704d22cde3](https://github.com/bisq-network/bisq/commit/704d22cde3c5c37475225f8965d6f7927e761baf) | Commit | Bump actions/stale from 10.0.0 to 10.1.0 | dependabot[bot] |
| 2025-10-09 | [9d89d01a4e](https://github.com/bisq-network/bisq/commit/9d89d01a4ecfb5fdbf00f4d222c383ba7f98935d) | Merge | Merge PR #7432: fix payment acc param | Alejandro García |
| 2025-10-09 | [72e178920a](https://github.com/bisq-network/bisq/commit/72e178920ac7e63413784674948b5bf969694697) | Merge | Merge PR #7433: improve cli takeoffer | Alejandro García |
| 2025-10-09 | [df098eeefd](https://github.com/bisq-network/bisq/commit/df098eeefd44013dcefc0bef091d5ab5ae67052a) | Merge | Merge PR #7481: update mediator addresses | Alejandro García |
| 2025-10-09 | [be58e349c5](https://github.com/bisq-network/bisq/commit/be58e349c5f1faf0987dc71a562af2fa93e3c4b7) | Merge | Merge PR #7483: cli get funding addresses enhancements | Alejandro García |
| 2025-10-09 | [3803c3c7e3](https://github.com/bisq-network/bisq/commit/3803c3c7e3d27fd8dd390615a199d205633a22db) | Merge | Merge PR #7484: btcNodes command line | Alejandro García |
| 2025-10-09 | [93469ebd5f](https://github.com/bisq-network/bisq/commit/93469ebd5fe21f5f1c3184efa8830f9a1d5ca7c4) | Merge | Merge PR #7506: api Remove deprecated getmyoffer | Alejandro García |
| 2025-10-09 | [05d07e53b1](https://github.com/bisq-network/bisq/commit/05d07e53b12b3efe99c72847e4a81ab07efb7d81) | Merge | Merge PR #7512: pr7433 review | Alejandro García |
| 2025-10-09 | [7a4e879234](https://github.com/bisq-network/bisq/commit/7a4e879234d653df2f44c7e422558b0d06bd3a19) | Merge | Merge PR #7515: pr7483 review | Alejandro García |
| 2025-10-09 | [10a0c41974](https://github.com/bisq-network/bisq/commit/10a0c419745b72d33b941fc805247fac8a513f11) | Merge | Merge PR #7516: stale 10.1.0 | Alejandro García |
| 2025-10-15 | [b9e82551de](https://github.com/bisq-network/bisq/commit/b9e82551de1f650b46a3144d4661afcc9bb2b27a) | Commit | Fix tests after mediator address update | HenrikJannsen |
| 2025-10-14 | [bf75f9d978](https://github.com/bisq-network/bisq/commit/bf75f9d978122a6679641f1a6a0a8faa0306c372) | Commit | Add missing guice config in ModuleForAppWithP2p | HenrikJannsen |
| 2025-10-15 | [d7ab7608c9](https://github.com/bisq-network/bisq/commit/d7ab7608c975cfa36302cd1a7b4ba4a91565f3de) | Merge | Merge PR #7519: fix broken test | HenrikJannsen |
| 2025-10-15 | [9f14c67bd8](https://github.com/bisq-network/bisq/commit/9f14c67bd8b79159b130bd88c0e2cbde32b698d8) | Merge | Merge PR #7518: fix missing guice config | HenrikJannsen |
| 2025-10-23 | [1c69a1796e](https://github.com/bisq-network/bisq/commit/1c69a1796ed47a5dc411d6e31116b3f316fb5da8) | Commit | update SWP node onion | suddenwhipvapor |
| 2025-11-03 | [e6740b89ca](https://github.com/bisq-network/bisq/commit/e6740b89cab88d8f82950995e9abbce43cdd39a4) | Commit | CapabilitiesTest: Allow use of deprecated Capabilities | Alva Swanson |
| 2025-11-03 | [6c961a4191](https://github.com/bisq-network/bisq/commit/6c961a41914e1192154ba6fd00de615f0d653421) | Commit | ConfigTests: Migrate deprecated string empty assertions | Alva Swanson |
| 2025-11-03 | [15302fe987](https://github.com/bisq-network/bisq/commit/15302fe987e9259cc0dabb925b56324238b69572) | Commit | Utilities: Migrate deprecated regex pattern replacement | Alva Swanson |
| 2025-11-03 | [9a12fd12f6](https://github.com/bisq-network/bisq/commit/9a12fd12f6898aa84fcc1d12cc6f067eff6b761a) | Commit | common: Suppress unchecked method invocation and conversion warnings | Alva Swanson |
| 2025-11-03 | [102a6cda64](https://github.com/bisq-network/bisq/commit/102a6cda64b0af936ac51bb9f7a0c675519e2159) | Commit | AbstractAsset: Migrate deprecated notNull method | Alva Swanson |
| 2025-11-03 | [6bcde91912](https://github.com/bisq-network/bisq/commit/6bcde9191261ebe7bf67d97f10777645f6e7551f) | Commit | BundleOfEnvelopes: Allow deprecated Capability BUNDLE_OF_ENVELOPES | Alva Swanson |
| 2025-11-03 | [c279185b85](https://github.com/bisq-network/bisq/commit/c279185b8595f68d751717ac5bc70ca11635db9b) | Commit | SocksSSLConnectionSocketFactory: Migrate deprecated HostnameVerifier | Alva Swanson |
| 2025-11-03 | [802ffbf537](https://github.com/bisq-network/bisq/commit/802ffbf537462e50a5be1f32a944c593d837a316) | Commit | p2p: Suppress unchecked warnings in TestState mocking | Alva Swanson |
| 2025-11-04 | [71c74a41dc](https://github.com/bisq-network/bisq/commit/71c74a41dcd982f2fbe7d2904e84fd126a76c510) | Commit | CorePersistenceProtoResolver: Suppress deprecation warning | Alva Swanson |
| 2025-11-04 | [de96821b74](https://github.com/bisq-network/bisq/commit/de96821b74b9e20d891f361cc16c7f49356213c8) | Commit | DaoMonitoring: Suppress deprecation warning | Alva Swanson |
| 2025-11-04 | [4ad23193a2](https://github.com/bisq-network/bisq/commit/4ad23193a266577b315d2fa53f20973290aba3b0) | Commit | TradeTaskRunner: Suppress unchecked warning | Alva Swanson |
| 2025-11-04 | [bd11954619](https://github.com/bisq-network/bisq/commit/bd11954619952cd9e312f6cd5ec0a313aabcd8d1) | Commit | OfferUtil: Suppress deprecation warning | Alva Swanson |
| 2025-11-04 | [3061331124](https://github.com/bisq-network/bisq/commit/306133112465e07a4e8f793ce377ccdbfbf66a08) | Commit | TradeStatistics2: Suppress deprecation warning | Alva Swanson |
| 2025-11-04 | [b2196df270](https://github.com/bisq-network/bisq/commit/b2196df2704a05f331bc51ef25fb08ded6b5bc51) | Commit | PaymentAccountUtil: Suppress deprecation warning | Alva Swanson |
| 2025-11-04 | [a418706b8a](https://github.com/bisq-network/bisq/commit/a418706b8ac265945c0816312f460c7cb2e7ec82) | Commit | PaymentAccountTypeAdapter: Suppress unchecked warning | Alva Swanson |
| 2025-11-04 | [f0dbd2b8c3](https://github.com/bisq-network/bisq/commit/f0dbd2b8c3b3ebed3aeba761d355f432bb1ff5e6) | Commit | XmrTxProofRequest: Suppress deprecation warning | Alva Swanson |
| 2025-11-04 | [5ba9bb8a53](https://github.com/bisq-network/bisq/commit/5ba9bb8a53e61a71ea1ae1fe18b2c071865a3227) | Commit | CoreNetworkCapabilities: Suppress deprecation warning | Alva Swanson |
| 2025-11-04 | [e30f7c8ab6](https://github.com/bisq-network/bisq/commit/e30f7c8ab64290d50496ee37ae01edb5a6e0f712) | Commit | CoreNetworkProtoResolver: Suppress deprecation warning | Alva Swanson |
| 2025-11-07 | [7cc74d4e48](https://github.com/bisq-network/bisq/commit/7cc74d4e48492f7a7383709f732053b735ab839d) | Commit | FormattingUtils: Migrate deprecated regex pattern replacement | Alva Swanson |
| 2025-11-07 | [23d55b790e](https://github.com/bisq-network/bisq/commit/23d55b790e93f7bbb09bcd3b431fcb85ec703a5f) | Commit | FormattingUtils: Migrate deprecated regex pattern replacement | Alva Swanson |
| 2025-11-07 | [6f7448d8b8](https://github.com/bisq-network/bisq/commit/6f7448d8b8d2397be423462fd80ba3730ba62973) | Commit | PaymentAccountFactory: Suppress deprecation warning | Alva Swanson |
| 2025-11-07 | [51d0062dc4](https://github.com/bisq-network/bisq/commit/51d0062dc425c18202b75017911c82b87f6560f7) | Commit | ArbitratorTest: Suppress RandomUtils deprecation warning | Alva Swanson |
| 2025-11-07 | [e41e030d83](https://github.com/bisq-network/bisq/commit/e41e030d83d46913586eaf5fe7805af3e7a9d809) | Commit | BlockchainExplorerSelectionTest: Suppress unchecked warning | Alva Swanson |
| 2025-11-07 | [84c00362b5](https://github.com/bisq-network/bisq/commit/84c00362b53805dc0fc6ae735008f3e69bec98d1) | Commit | core: Fix Mockito illegal reflective access bug | Alva Swanson |
| 2025-11-07 | [92fab1dfc9](https://github.com/bisq-network/bisq/commit/92fab1dfc95ad19b2082f4782124a73c1f71d0f4) | Commit | JFXRadioButtonSkinBisqStyle: Migrate deprecated snapSize method | Alva Swanson |
| 2025-11-07 | [bf82c7b750](https://github.com/bisq-network/bisq/commit/bf82c7b7507754d383f69cf0efed3866b8dba334) | Commit | Navigation: Suppress unchecked warning | Alva Swanson |
| 2025-11-07 | [805d13d788](https://github.com/bisq-network/bisq/commit/805d13d78852d0869e6c90a2a1d2b934ebe7c9fd) | Commit | ChaseQuickPayForm: Suppress deprecation warning | Alva Swanson |
| 2025-11-07 | [5ac366e364](https://github.com/bisq-network/bisq/commit/5ac366e36468f54ff0e0a2ccc8f9d8e136ab3e7b) | Commit | Overlay: Suppress unchecked warning | Alva Swanson |
| 2025-11-10 | [c19991440f](https://github.com/bisq-network/bisq/commit/c19991440f093c3a1b14e954cb19b126f1e5b015) | Commit | Add isUnlocked fields to BondedReputationDto | HenrikJannsen |
| 2025-11-11 | [a3ad49693b](https://github.com/bisq-network/bisq/commit/a3ad49693b4ce37983565b995feb2602a63a98b0) | Commit | Update refundagent2 matrix handle | suddenwhipvapor |
| 2025-11-12 | [50aa12e211](https://github.com/bisq-network/bisq/commit/50aa12e211377ef336e789aede19ec78ba67104d) | Merge | Merge PR #7535: fix build warnings 1 | Alejandro García |
| 2025-11-12 | [0f776eb319](https://github.com/bisq-network/bisq/commit/0f776eb31976460e168ad71059a5c06cce259398) | Merge | Merge PR #7536: fix build warnings 2 | Alejandro García |
| 2025-11-12 | [c546c4b57e](https://github.com/bisq-network/bisq/commit/c546c4b57eb2dfe254fea18537d090b6946fed11) | Merge | Merge PR #7537: FormattingUtils Migrate deprecated regex pattern replacement | Alejandro García |
| 2025-11-12 | [f198b39bce](https://github.com/bisq-network/bisq/commit/f198b39bcebea1b8486b281abe842e7ff86333aa) | Merge | Merge PR #7538: fix build warnings 3 | Alejandro García |
| 2025-11-25 | [3c61a408e5](https://github.com/bisq-network/bisq/commit/3c61a408e530d188bc46952de23714b8717657d8) | Merge | Merge PR #7527: update SWP btc node | HenrikJannsen |
| 2025-11-25 | [0875ff980a](https://github.com/bisq-network/bisq/commit/0875ff980ad87151ffeafe27c1860839683ac063) | Merge | Merge PR #7540: ra2 matrix handle | HenrikJannsen |
| 2025-11-25 | [f07807bc68](https://github.com/bisq-network/bisq/commit/f07807bc68421688d0f8d2e171b24359b0d74aa9) | Merge | Merge PR #7548: add support for handling unlocked bonds | HenrikJannsen |
| 2025-11-29 | [594d243c8d](https://github.com/bisq-network/bisq/commit/594d243c8d6c22aae00aa7c86b25f83b1fd8ee7e) | Commit | Add null check for lockupTxId and unlockTxId | HenrikJannsen |
| 2025-11-29 | [c82cdc9ec6](https://github.com/bisq-network/bisq/commit/c82cdc9ec6cc7c53add0ae913b4a0873a70e5344) | Merge | Merge PR #7550: Add null check for lockupTxId and unlockTxId | HenrikJannsen |
| 2025-11-29 | [8e4d18c703](https://github.com/bisq-network/bisq/commit/8e4d18c70388b27aba7ac5d572d8c34545c6d858) | Commit | Sync BondedReputationDto proto with Bisq 2 version | HenrikJannsen |
| 2025-11-29 | [f771e65b17](https://github.com/bisq-network/bisq/commit/f771e65b177a5a88d043df01905a7f30656477cd) | Merge | Merge PR #7551: Sync BondedReputationDto proto | HenrikJannsen |
| 2025-11-29 | [49e96cbb40](https://github.com/bisq-network/bisq/commit/49e96cbb40870c3e3f2096935e5210ce544ac4eb) | Commit | Remove null check for unLockupTxId | HenrikJannsen |
| 2025-11-29 | [7598e4fec8](https://github.com/bisq-network/bisq/commit/7598e4fec8bf30a66585eaa0233f53ae11d0638c) | Merge | Merge PR #7552: Remove null check for unLockupTxId | HenrikJannsen |
| 2025-12-01 | [6e61d67a6a](https://github.com/bisq-network/bisq/commit/6e61d67a6a106c67db0df67b09c6de9a0451bc8e) | Commit | Add logs if we enter a chain fork with the same chain length. | HenrikJannsen |
| 2025-11-12 | [e436ce1e48](https://github.com/bisq-network/bisq/commit/e436ce1e483306748792f47fb3420332517f0454) | Commit | AddDataMessageTest: Suppress RandomUtils deprecation warning | Alva Swanson |
| 2025-11-12 | [1fdd181f7f](https://github.com/bisq-network/bisq/commit/1fdd181f7ffe1813110d32f3ef32c23a909cc15c) | Commit | MapStoreServiceFake: Suppress unchecked warnings | Alva Swanson |
| 2025-11-12 | [69871592c2](https://github.com/bisq-network/bisq/commit/69871592c20b453241054a9af768413d375ce2a3) | Commit | AppendOnlyDataStoreServiceFake: Suppress unchecked warnings | Alva Swanson |
| 2025-11-12 | [5025d1e4aa](https://github.com/bisq-network/bisq/commit/5025d1e4aa75b08046b0188170466b9c167ee8ff) | Commit | PreferencesPayload: Reserve and remove deprecated buyerSecurityDepositAsLong | Alva Swanson |
| 2025-11-12 | [1f6b9910e2](https://github.com/bisq-network/bisq/commit/1f6b9910e246cefedcdb3ecc1bf24bc87b3d0f7f) | Commit | PaymentAccountPayload: Suppress deprecation warning for maxTradePeriod | Alva Swanson |
| 2025-11-12 | [090889f05c](https://github.com/bisq-network/bisq/commit/090889f05c2f0f281150a541445748240c499909) | Commit | CoreProtoResolver: Suppress deprecation warning | Alva Swanson |
| 2025-11-12 | [67854ae2e2](https://github.com/bisq-network/bisq/commit/67854ae2e21240ff0fd9d3721f831c935798dcb8) | Commit | OfferPayload: Suppress deprecation warning | Alva Swanson |
| 2025-11-12 | [380dbc30d9](https://github.com/bisq-network/bisq/commit/380dbc30d95deab9b9062b0587928d1dc1c218cf) | Commit | TradeStatisticsManager: Suppress deprecation warning | Alva Swanson |
| 2025-11-12 | [62c4cc796c](https://github.com/bisq-network/bisq/commit/62c4cc796c38f701633a321082d28637d12a0eea) | Commit | TradeStatisticsConverter: Suppress deprecation warning | Alva Swanson |
| 2025-11-12 | [10ae06c48a](https://github.com/bisq-network/bisq/commit/10ae06c48a16c0e970cf56655c045d3b5a437b7d) | Commit | AddDataMessageTest: Suppress RandomUtils deprecation warning | Alva Swanson |
| 2025-11-12 | [cbbf144354](https://github.com/bisq-network/bisq/commit/cbbf144354b67026d47e05f7985effbea3ece844) | Commit | MapStoreServiceFake: Suppress unchecked warnings | Alva Swanson |
| 2025-11-12 | [88de37441d](https://github.com/bisq-network/bisq/commit/88de37441dabadf6e9037d6afc2a5013bb46a38a) | Commit | AppendOnlyDataStoreServiceFake: Suppress unchecked warnings | Alva Swanson |
| 2025-11-12 | [254e3cffce](https://github.com/bisq-network/bisq/commit/254e3cffce5b6576eb3b6bc7661c7a8184720d35) | Commit | PreferencesPayload: Reserve and remove deprecated buyerSecurityDepositAsLong | Alva Swanson |
| 2025-11-12 | [7b30fd4e6a](https://github.com/bisq-network/bisq/commit/7b30fd4e6a2aee25040af0c4644a2f0477c391f1) | Commit | PaymentAccountPayload: Suppress deprecation warning for maxTradePeriod | Alva Swanson |
| 2025-11-12 | [af163f0689](https://github.com/bisq-network/bisq/commit/af163f068956f540ddfbe057e40bfbb9e38bafd9) | Commit | CoreProtoResolver: Suppress deprecation warning | Alva Swanson |
| 2025-11-12 | [465c474fe1](https://github.com/bisq-network/bisq/commit/465c474fe13518070dd54e8ebb183085a08ba30d) | Commit | OfferPayload: Suppress deprecation warning | Alva Swanson |
| 2025-11-12 | [6d429394b0](https://github.com/bisq-network/bisq/commit/6d429394b01f66310ebea7c2982059825b631d21) | Commit | TradeStatisticsManager: Suppress deprecation warning | Alva Swanson |
| 2025-11-12 | [2460a1b29b](https://github.com/bisq-network/bisq/commit/2460a1b29b6723c40a3b09ad43cb11a1eb897f3c) | Commit | TradeStatisticsConverter: Suppress deprecation warning | Alva Swanson |
| 2025-11-12 | [3b10568508](https://github.com/bisq-network/bisq/commit/3b10568508669f82c364a4c9adde6ea363d56228) | Commit | OpenOfferManager: Suppress deprecation warning | Alva Swanson |
| 2025-11-12 | [f602ff0f9a](https://github.com/bisq-network/bisq/commit/f602ff0f9acf204f5ff74f27df263128d8c954a3) | Commit | MenuItem: Suppress unchecked warning | Alva Swanson |
| 2025-11-12 | [3344975979](https://github.com/bisq-network/bisq/commit/334497597956af85d91c64de7b64f0f195eec4e9) | Commit | FxmlViewLoader: Suppress deprecation warning | Alva Swanson |
| 2025-11-12 | [7ad9eaeb57](https://github.com/bisq-network/bisq/commit/7ad9eaeb571e3036b6d8cbd377741e767b03c6a7) | Commit | FiatAccountsView: Suppress deprecation warning | Alva Swanson |
| 2025-11-12 | [9ff3fcd708](https://github.com/bisq-network/bisq/commit/9ff3fcd708b611eb607fddcd5cc82ff7197d8107) | Commit | CloneOfferDataModel: Suppress deprecation warning | Alva Swanson |
| 2025-11-12 | [6198b09c2f](https://github.com/bisq-network/bisq/commit/6198b09c2fa06f82390458a89b919473943aed16) | Commit | BuyerStep2View: Suppress deprecation warning | Alva Swanson |
| 2025-11-12 | [5617c1e14d](https://github.com/bisq-network/bisq/commit/5617c1e14d4f0a4978349813d04bb02388028d7d) | Commit | BsqWalletView: Suppress rawtypes and unchecked warning | Alva Swanson |
| 2025-12-01 | [2cd04f4afb](https://github.com/bisq-network/bisq/commit/2cd04f4afbcdf73785d4ac6b7b0536e028060a34) | Commit | DownloadTask: Migrate deprecated IOUtils.closeQuietly method | Alva Swanson |
| 2025-12-01 | [f2d480cd96](https://github.com/bisq-network/bisq/commit/f2d480cd96a9abecdb073aa7e909b7da7eca2ce1) | Commit | MovingAverageUtils: Suppress unchecked warning | Alva Swanson |
| 2025-12-01 | [48781ad9d6](https://github.com/bisq-network/bisq/commit/48781ad9d6766bda0670d5e07316fd2ce591ec19) | Commit | ComponentsDemo: Suppress unchecked warning | Alva Swanson |
| 2025-12-01 | [792f09b592](https://github.com/bisq-network/bisq/commit/792f09b5921bdc0bdc3e691c9eefc5c68f7f8d83) | Commit | TradeTest: Suppress deprecation warning | Alva Swanson |
| 2025-12-01 | [4c90099a3b](https://github.com/bisq-network/bisq/commit/4c90099a3b8dc28aab7793ff0fbd106cdd402a1a) | Commit | Update to Gradle 8.9 | Alva Swanson |
| 2025-12-01 | [b9e785cb08](https://github.com/bisq-network/bisq/commit/b9e785cb0896c3bef961e0a4158be638341d4395) | Commit | build-logic: Migrate to Gradle 8.0 | Alva Swanson |
| 2025-12-02 | [f6fe9a0c8b](https://github.com/bisq-network/bisq/commit/f6fe9a0c8b078bf4df2083aa81d16f0b654aed6b) | Commit | Log hash as hex and not byte array | HenrikJannsen |
| 2025-12-02 | [51d057d663](https://github.com/bisq-network/bisq/commit/51d057d6634293ebd96d026ae37303097ad1f609) | Commit | Load persisted dao data at resync | HenrikJannsen |
| 2025-12-02 | [3d1944a0d2](https://github.com/bisq-network/bisq/commit/3d1944a0d2b5a7f079895dec99a60d852d5dd439) | Commit | Add check if blocks are not empty Improve log | HenrikJannsen |
| 2025-12-02 | [a49488a3c8](https://github.com/bisq-network/bisq/commit/a49488a3c81f40ffec14eed034f864db99c4a272) | Merge | Merge PR #7556: fix bug with applying snapshot | HenrikJannsen |
| 2025-12-08 | [a78e50dc9c](https://github.com/bisq-network/bisq/commit/a78e50dc9c540a3254878c2035134ebf6e227b0a) | Commit | Bump actions/checkout from 5.0.0 to 6.0.1 | dependabot[bot] |
| 2025-12-08 | [28ef6c8f0b](https://github.com/bisq-network/bisq/commit/28ef6c8f0b3657638b479501164f2f973445315b) | Commit | Bump actions/setup-java from 5.0.0 to 5.1.0 | dependabot[bot] |
| 2025-12-08 | [8a6b82a7e1](https://github.com/bisq-network/bisq/commit/8a6b82a7e103bc8403e4e7322f16c3a8d97d46ab) | Commit | Bump actions/stale from 10.1.0 to 10.1.1 | dependabot[bot] |
| 2025-12-14 | [ea14fbee64](https://github.com/bisq-network/bisq/commit/ea14fbee64d824d826d8979727a72b2401df6879) | Commit | bitcoind: Target commit a72e56f | Alva Swanson |
| 2025-12-16 | [7eee3b6c3a](https://github.com/bisq-network/bisq/commit/7eee3b6c3a8a906fceea0aa5f6ebacf5e1e6af10) | Merge | Merge PR #7542: fix build warnings 5 | Alejandro García |
| 2025-12-16 | [a83dd93df2](https://github.com/bisq-network/bisq/commit/a83dd93df2f45b049d590daed92001d3340ecbd6) | Merge | Merge PR #7543: fix build warnings 6 | Alejandro García |
| 2025-12-16 | [873a715f42](https://github.com/bisq-network/bisq/commit/873a715f42b3f8afc9c3f6eb1f4e83c62c4cf088) | Merge | Merge PR #7553: fix build warnings 7 | Alejandro García |
| 2025-12-16 | [57408ef68e](https://github.com/bisq-network/bisq/commit/57408ef68e688c8b4c7e2635a8b69b578c01d76d) | Merge | Merge PR #7554: update gradle | Alejandro García |
| 2025-12-16 | [57067cb703](https://github.com/bisq-network/bisq/commit/57067cb7039a8b411833770696e93962901265a5) | Merge | Merge PR #7555: build logic Migrate to Gradle 8 0 | Alejandro García |
| 2025-12-16 | [5176e90d1f](https://github.com/bisq-network/bisq/commit/5176e90d1f3991f5cef05a1a0c4b50ca81bc5648) | Merge | Merge PR #7558: checkout 6.0.1 | Alejandro García |
| 2025-12-16 | [79238df648](https://github.com/bisq-network/bisq/commit/79238df6485f3bf9d8456ec0b147be2665791ade) | Merge | Merge PR #7559: setup java 5.1.0 | Alejandro García |
| 2025-12-16 | [f6d4838270](https://github.com/bisq-network/bisq/commit/f6d48382708dbdc018b80ad171a86c3f172e5bb4) | Merge | Merge PR #7560: stale 10.1.1 | Alejandro García |
| 2025-12-16 | [2016b8d3c0](https://github.com/bisq-network/bisq/commit/2016b8d3c02c9369d9efacb48b0c2455c219cbae) | Merge | Merge PR #7562: bitcoind target 79c05fe | Alejandro García |
| 2025-12-23 | [132e15d649](https://github.com/bisq-network/bisq/commit/132e15d6490f4772c5b121bf38748867128b6c14) | Commit | Use bitcoinj commit 7dc0851a807857ba19f76824e48d75ab0390eca7 with the backport of the chainwork bugfix. | HenrikJannsen |
| 2025-12-23 | [45df4ce695](https://github.com/bisq-network/bisq/commit/45df4ce6952fe65df424e9a16a3a03c42ee1b5da) | Commit | Set version 1.9.22 | HenrikJannsen |
| 2025-12-23 | [b3faaf7e43](https://github.com/bisq-network/bisq/commit/b3faaf7e43e866b7c2e30bcca089812c1eac342d) | Commit | Add Henrik Jannsen's pgp key (387C8307.asc) | HenrikJannsen |
| 2025-12-23 | [f2fe13d07d](https://github.com/bisq-network/bisq/commit/f2fe13d07def5cf7c57f15d0365c2052d3b9f88d) | Merge | Merge PR #7567: use backported bitcoinj fix | HenrikJannsen |
| 2026-01-20 | [d5df6a9359](https://github.com/bisq-network/bisq/commit/d5df6a9359137961cf45d923224b267d97449041) | Commit | Migrate to new bitcoind RPC implementation | Alva Swanson |
| 2026-01-20 | [2f11a93c6f](https://github.com/bisq-network/bisq/commit/2f11a93c6fafeffa300282d0cfa24bc4f6727739) | Commit | core: Remove unused jackson.databind dependency | Alva Swanson |
| 2026-01-26 | [a7167cc001](https://github.com/bisq-network/bisq/commit/a7167cc001a13f4276f437eb5aebdc3748a8f814) | Commit | Bump actions/checkout from 6.0.1 to 6.0.2 | dependabot[bot] |
| 2026-01-26 | [1aa07d48e6](https://github.com/bisq-network/bisq/commit/1aa07d48e6688099ff2a0ce10bff5f893d78913e) | Commit | Bump actions/setup-java from 5.1.0 to 5.2.0 | dependabot[bot] |
| 2026-01-27 | [db1d234994](https://github.com/bisq-network/bisq/commit/db1d234994b89bb1d1c7574191f4ea41e7688899) | Commit | UX: Show disk space warning only once | viresinnumer1s |
| 2026-02-02 | [b20d32d127](https://github.com/bisq-network/bisq/commit/b20d32d127afe323aee09ae880bbb036ef981a08) | Commit | Export accounts and signature key pair to json for import into Bisq 2 | HenrikJannsen |
| 2026-02-03 | [b18429e4ee](https://github.com/bisq-network/bisq/commit/b18429e4eee0619030016cf9125ff773c564aac4) | Commit | Hide the export button until MuSig is rolled out onBisq 2 | HenrikJannsen |
| 2026-02-03 | [59908094ae](https://github.com/bisq-network/bisq/commit/59908094ae1bc7ffc78e8a5e056931aa6fff8ccd) | Merge | Merge PR #7583: add export accounts for bisq2 feature | HenrikJannsen |
| 2026-02-04 | [488fd0a406](https://github.com/bisq-network/bisq/commit/488fd0a406b60b6fcdd495e1f74bf132dca8caa9) | Merge | Merge PR #7580: UXDontNagUserAboutSpace | HenrikJannsen |
| 2026-02-04 | [4618b167a2](https://github.com/bisq-network/bisq/commit/4618b167a240e32581a687a69c2e1432c16d647a) | Commit | Add AccountTimestampGrpcService | HenrikJannsen |
| 2026-02-08 | [8f1640d592](https://github.com/bisq-network/bisq/commit/8f1640d592bb80fb46209b8ce922365e7c815b96) | Commit | Include salt in exported json | HenrikJannsen |
| 2026-02-08 | [6466052462](https://github.com/bisq-network/bisq/commit/6466052462a8cf0e9f360b35bb13aa32b8c25246) | Commit | Filter out BsqSwapAccounts from fiat accounts | HenrikJannsen |
| 2026-02-09 | [3d4cbe8a83](https://github.com/bisq-network/bisq/commit/3d4cbe8a8347c13abab1c4f081b68a3fb8ca0683) | Merge | Merge PR #7584: add AccountTimestampGrpcService | HenrikJannsen |
| 2026-02-11 | [406c5a6f5b](https://github.com/bisq-network/bisq/commit/406c5a6f5ba0b7bcb6181571810b565a31126254) | Commit | Remove HRK from various currency lists. | HenrikJannsen |
| 2026-02-14 | [f882fc3556](https://github.com/bisq-network/bisq/commit/f882fc35560829aa2019f2e785e9a8d5799f9a23) | Merge | Merge PR #7585: Remove HRK (Croatian Kuna) as not in use anymore | HenrikJannsen |
| 2026-02-14 | [ef3d6d96c2](https://github.com/bisq-network/bisq/commit/ef3d6d96c2d895571a74d63bf67d66748c7cff44) | Commit | Fix Swish account creation NPE by initializing validator superclass | HenrikJannsen |
| 2026-02-14 | [c077d06e41](https://github.com/bisq-network/bisq/commit/c077d06e4118797372cf65a9e04b96d5741c58d5) | Merge | Merge PR #7586: Fix Swish validator | HenrikJannsen |
| 2026-02-16 | [4859cd0f4d](https://github.com/bisq-network/bisq/commit/4859cd0f4da87668bb13d227ed80c93734334190) | Commit | Fix BSQ wallet CSV tx type mapping for swaps and issuance | Christoph Atteneder |
| 2026-02-16 | [d1b8d8a801](https://github.com/bisq-network/bisq/commit/d1b8d8a801cf72fa0ba92c2b7db72cc551e91536) | Commit | refactor(desktop): DRY BSQ CSV export type mapping | Christoph Atteneder |
| 2026-02-16 | [1fb8db63db](https://github.com/bisq-network/bisq/commit/1fb8db63db98c3781d568fb37e78305466c740ad) | Commit | refactor(desktop): avoid redundant tx type lookups in CSV export mapping | Christoph Atteneder |
| 2026-02-17 | [d681fa45c6](https://github.com/bisq-network/bisq/commit/d681fa45c630e62f0700dc15dd68aef452631dc3) | Commit | Deprecate VERSE for new payment account creation | HenrikJannsen |
| 2026-02-17 | [aba66c39d6](https://github.com/bisq-network/bisq/commit/aba66c39d69110834f2c7f9083bf337930f15c61) | Merge | Merge PR #7589: bsq csv swap type | HenrikJannsen |
| 2026-02-17 | [80f5ed863c](https://github.com/bisq-network/bisq/commit/80f5ed863cbc9ed88131556ac7905817c764450c) | Merge | Merge PR #7590: remove verse from create payment account list | HenrikJannsen |
| 2026-02-23 | [09c3a5672d](https://github.com/bisq-network/bisq/commit/09c3a5672df4417b029d87dbd2ba1de0bb095b8e) | Commit | Bump actions/stale from 10.1.1 to 10.2.0 | dependabot[bot] |
| 2026-02-27 | [c819f4df75](https://github.com/bisq-network/bisq/commit/c819f4df75f7fe2cbcfab4afc01f54eed282054e) | Commit | Delay shutdown when payout transaction broadcasts are pending | Alva Swanson |
| 2026-02-27 | [888872e7c9](https://github.com/bisq-network/bisq/commit/888872e7c92b72e92afad59a4a633db25ee7629b) | Commit | Add second bitcoind node for re-org testing | Alva Swanson |
| 2026-02-27 | [efbdd52d4e](https://github.com/bisq-network/bisq/commit/efbdd52d4e007fc82665fee6c51abd2b8c54963f) | Commit | Add gradle:gradle:8.9 to dependency verification | Alva Swanson |
| 2026-03-01 | [65e46e7f6b](https://github.com/bisq-network/bisq/commit/65e46e7f6bfffbcc03d0142c8c0850f2412335e4) | Commit | Update README.md - Add downloads count badge | tat twam asi |
| 2026-03-05 | [192e43414a](https://github.com/bisq-network/bisq/commit/192e43414a2bef234e1aeda8e2db1cb25266dfba) | Commit | update luis onion, clean old entries | suddenwhipvapor |
| 2026-03-07 | [81d375ea64](https://github.com/bisq-network/bisq/commit/81d375ea643f611a1c8690bd3c58cf57046a1c78) | Merge | Merge PR #7564: add bitcoind v29.2 support | Alejandro García |
| 2026-03-07 | [d1eff5dbbe](https://github.com/bisq-network/bisq/commit/d1eff5dbbe9bcc725b318cb205c10c3c70dede40) | Merge | Merge PR #7578: checkout 6.0.2 | Alejandro García |
| 2026-03-07 | [605b91b4dd](https://github.com/bisq-network/bisq/commit/605b91b4dd4b31bfaac281e1b30815c077d299bc) | Merge | Merge PR #7579: setup java 5.2.0 | Alejandro García |
| 2026-03-07 | [776aa20470](https://github.com/bisq-network/bisq/commit/776aa204704b0e0866c2418fb0590c67dfd335f0) | Merge | Merge PR #7592: stale 10.2.0 | Alejandro García |
| 2026-03-07 | [d0f7dda2f3](https://github.com/bisq-network/bisq/commit/d0f7dda2f32e50634e7e4871fcc0106771227fee) | Merge | Merge PR #7593: delay shutdown confirm payment received | Alejandro García |
| 2026-03-07 | [241ae91020](https://github.com/bisq-network/bisq/commit/241ae91020ff0a21b3aa1c005709367a535aa386) | Merge | Merge PR #7595: add second bitcoind node reorg testing | Alejandro García |
| 2026-03-07 | [8168b75903](https://github.com/bisq-network/bisq/commit/8168b7590325ff1f7c60af836bea4d7f4e5e56bd) | Merge | Merge PR #7596: fix gradle src dependency verification fail bug | Alejandro García |
| 2026-03-07 | [9a76563ca9](https://github.com/bisq-network/bisq/commit/9a76563ca98cc6feb809e7fcd684954ed7d1393c) | Merge | Merge PR #7599: patch 1 | Alejandro García |
| 2026-03-09 | [1a41e8461e](https://github.com/bisq-network/bisq/commit/1a41e8461e3d42d935739a7128c775fa81f7429e) | Commit | Fix application mainClass definitions | Alva Swanson |
| 2026-03-09 | [d284ca328f](https://github.com/bisq-network/bisq/commit/d284ca328fa5bd586576341d346fa0c1aff47add) | Commit | Remove broken app start scripts | Alva Swanson |
| 2026-03-09 | [49dc3322f0](https://github.com/bisq-network/bisq/commit/49dc3322f039c3d9ee40ef98b6160f97b477198b) | Commit | AppStartPlugin: Migrate jvmArgs to new Gradle version | Alva Swanson |
| 2026-03-09 | [9031784903](https://github.com/bisq-network/bisq/commit/90317849035aa55d037b00b3c1d2b7d4fabe2c39) | Commit | coverage-reporter: Pull and checkout submodules | Alva Swanson |
| 2026-03-12 | [a853068658](https://github.com/bisq-network/bisq/commit/a853068658df4336c9634ac91363b6ad5253ba15) | Commit | Update refundagent2 matrix handle | suddenwhipvapor |
| 2026-04-10 | [9df2d98d39](https://github.com/bisq-network/bisq/commit/9df2d98d399fc323f9e320e171ef2dc463df001e) | Commit | Update data stores for v1.9.23 | Alejandro García |
| 2026-04-10 | [43ca201b4d](https://github.com/bisq-network/bisq/commit/43ca201b4dc89b7e551a2330b152fae4b3b86ee1) | Merge | Merge PR #7602: luis new onion | Alejandro García |
| 2026-04-10 | [58328a318d](https://github.com/bisq-network/bisq/commit/58328a318d74580da28d5265b5be793446ec68fd) | Merge | Merge PR #7603: fix code coverage | Alejandro García |
| 2026-04-10 | [eab40d8232](https://github.com/bisq-network/bisq/commit/eab40d823238ea11c8f4946d957f5ecfdf2e8b23) | Merge | Merge PR #7604: new RA2 matrix handle | Alejandro García |
| 2026-04-13 | [403c749679](https://github.com/bisq-network/bisq/commit/403c7496796cd906acd78498bb4a2b3a97d72c72) | Commit | Create wrapper scripts to call custom app start Gradle task | Alva Swanson |
| 2026-03-09 | [4051c44e05](https://github.com/bisq-network/bisq/commit/4051c44e0502ae6dcb494e122bd7ad088678f1ac) | Commit | Move seednode JVM arguments to global configuration | Alva Swanson |
| 2026-04-13 | [ba29397860](https://github.com/bisq-network/bisq/commit/ba29397860dceefeb06ce597c3e1f7454ac2d0a4) | Merge | Merge PR #7597: fix bisq launch scripts | Alejandro García |
| 2026-04-13 | [18dee23785](https://github.com/bisq-network/bisq/commit/18dee2378543f5d4e4e4f40eebb464917abeba81) | Merge | Merge PR #7598: Move seednode JVM arguments to global configuration | Alejandro García |
| 2026-04-13 | [32c825a393](https://github.com/bisq-network/bisq/commit/32c825a393de64a1f34ecffd5bfcdb69398bfa12) | Merge | Merge PR #7613: update data stores for v1.9.23 | Alejandro García |
| 2026-05-02 | [797836cc9d](https://github.com/bisq-network/bisq/commit/797836cc9d3ef0891a1bd349e18127fe252eb860) | Commit | Validate trade peer deposit inputs during maker/taker deposit setup | HenrikJannsen |
| 2026-05-02 | [3ce703d741](https://github.com/bisq-network/bisq/commit/3ce703d74117db0667c90153d517ffac487e0f10) | Commit | Add instruction how to update submodule if not cloned newly | HenrikJannsen |
| 2026-05-02 | [382e438a87](https://github.com/bisq-network/bisq/commit/382e438a8763fa7b934d99971c7d2052e84014f1) | Commit | Check that changeOutputAddress is not null if changeOutputValue is > 0. | HenrikJannsen |
| 2026-05-02 | [95fff711ef](https://github.com/bisq-network/bisq/commit/95fff711efdc16fece6c4b62f9afa82f9c3283d1) | Commit | Refactor: rename and extract variable | HenrikJannsen |
| 2026-05-02 | [ad0568db35](https://github.com/bisq-network/bisq/commit/ad0568db35ba8d2e076f0660376f9e28220a1ee4) | Commit | Refactor: rename tradeAmount to requestTradeAmount and rearrange | HenrikJannsen |
| 2026-05-02 | [0c08727dd6](https://github.com/bisq-network/bisq/commit/0c08727dd65bf408aa1a00e73b891d67206bf2bb) | Commit | Call TradePeerTxInputValidator.validateContribution at MakerProcessesInputsForDepositTxRequest | HenrikJannsen |
| 2026-05-02 | [acc8c84237](https://github.com/bisq-network/bisq/commit/acc8c84237cdc6968667de29558146a85e59a59e) | Commit | Refactor: rename and extract variable and rearrange | HenrikJannsen |
| 2026-05-02 | [89171806e8](https://github.com/bisq-network/bisq/commit/89171806e8d3ae7a74c34fe6e40948245e1bf761) | Merge | Merge PR #7620: patch to fix exploit | HenrikJannsen |
| 2026-05-02 | [933e448dc1](https://github.com/bisq-network/bisq/commit/933e448dc1cf20418c190bbf098a8a72c20ef2ad) | Merge | Merge PR #7621: add instructions for submodule update | HenrikJannsen |
| 2026-05-02 | [cee07b868e](https://github.com/bisq-network/bisq/commit/cee07b868e7171b7e402afa4cd081880ae458d79) | Commit | Call TradePeerTxInputValidator.validateContribution at TakerProcessesInputsForDepositTxResponse | HenrikJannsen |
| 2026-05-02 | [87b5afc143](https://github.com/bisq-network/bisq/commit/87b5afc1437c2811d751938719d303ae33d4cd42) | Commit | Make getValidatedInputValue private as not used from outside anymore. | HenrikJannsen |
| 2026-05-02 | [34c61748fb](https://github.com/bisq-network/bisq/commit/34c61748fbe2ffb38caa0ec31d9d4fab8448f19a) | Commit | Add WalletUtils helpers for P2W checks and connected outpoints | HenrikJannsen |
| 2026-05-02 | [b99cc3bbab](https://github.com/bisq-network/bisq/commit/b99cc3bbab0ea2d9d6076fd5bda6da9c2bf6bd9b) | Commit | Add check that inputs are P2WH | HenrikJannsen |
| 2026-05-02 | [1411a30461](https://github.com/bisq-network/bisq/commit/1411a304616426e090c070424ad1294662d113f4) | Commit | Verify trade peer's multisig pubkey is compressed | Steven Barclay |
| 2026-05-02 | [474f113031](https://github.com/bisq-network/bisq/commit/474f113031a72745406b476bb08165574b2c65d0) | Commit | Return only taker deposit inputs; the taker fee transaction already provides the exact deposit value | HenrikJannsen |
| 2026-05-02 | [4bfc5b0136](https://github.com/bisq-network/bisq/commit/4bfc5b0136ebe1e1834a55e2e4c4019315684908) | Commit | Remove changeOutputValue and changeOutputAddress from InputsForDepositTxRequest. | HenrikJannsen |
| 2026-05-02 | [9fbc00b3be](https://github.com/bisq-network/bisq/commit/9fbc00b3beebb93725a4d45f8c6f22779ca896cc) | Commit | Remove takers change from sellerAsMakerCreatesDepositTx and buyerAsMakerCreatesAndSignsDepositTx wallet methods. | HenrikJannsen |
| 2026-05-02 | [4ae757b4d6](https://github.com/bisq-network/bisq/commit/4ae757b4d68cdb5f49338a83c46e1434d4305646) | Commit | Add missing P2WSH witness validation for DPT | Steven Barclay |
| 2026-05-02 | [55608f3b21](https://github.com/bisq-network/bisq/commit/55608f3b21a930bc3d0a20c1f9a85d7151b9f83d) | Commit | Remove change value from TradePeerTxInputValidator.validateContribution | HenrikJannsen |
| 2026-05-02 | [6b03efd963](https://github.com/bisq-network/bisq/commit/6b03efd963c3a2ea6bd7b3e2168dcea3bd47fe90) | Commit | Refactor: Rename variables and method | HenrikJannsen |
| 2026-05-03 | [00309986c3](https://github.com/bisq-network/bisq/commit/00309986c376d91d7e071b944d23c3fd888fdc5d) | Commit | Set version 1.9.24 | HenrikJannsen |
| 2026-05-03 | [c24cc91a60](https://github.com/bisq-network/bisq/commit/c24cc91a60daab8ec3323cd2a01c43816efe4d00) | Merge | Merge PR #7629: set version 1.9.24 | HenrikJannsen |
| 2026-05-03 | [815b34e363](https://github.com/bisq-network/bisq/commit/815b34e3633debba4b3fc934848920890927ab0e) | Commit | Add validation methods to Validator Add integrity validation to Trade messages Add tests | HenrikJannsen |
| 2026-05-03 | [baaa2d74ac](https://github.com/bisq-network/bisq/commit/baaa2d74ac556286286bcf3b1be06685fbc5f932) | Commit | Remove nullable annotation for accountAgeWitnessSignatureOfPreparedDepositTx in InputsForDepositTxResponse. | HenrikJannsen |
| 2026-05-03 | [a2dbcb9b7c](https://github.com/bisq-network/bisq/commit/a2dbcb9b7cadd7fb7123aa0d52fedc9cc19ab98b) | Commit | Allow 0 for burningManSelectionHeight in InputsForDepositTxRequest | HenrikJannsen |
| 2026-05-04 | [e741eb0462](https://github.com/bisq-network/bisq/commit/e741eb046209abd77bd07df87b0f57cc135c48d5) | Commit | Validate multisig keys by forcing decompression without error | Steven Barclay |
| 2026-05-03 | [c9a82dcd82](https://github.com/bisq-network/bisq/commit/c9a82dcd8245070dc8fc502775d1708aa5436935) | Commit | Add try catch to WalletUtils.isP2WH Small improvements and cleanups | HenrikJannsen |
| 2026-05-04 | [b4e239357d](https://github.com/bisq-network/bisq/commit/b4e239357d7395ccc90de121fc849cf964a77317) | Commit | Reduce  max price deviation to 25%. | HenrikJannsen |
| 2026-05-04 | [3ac2786b12](https://github.com/bisq-network/bisq/commit/3ac2786b127fc0707932c136f2f3acf14f217bb4) | Commit | Enforce price to be inside the max price bounds of 25%. | HenrikJannsen |
| 2026-05-04 | [76b74287be](https://github.com/bisq-network/bisq/commit/76b74287be2cdea0249d4878333f94ae07d3907d) | Merge | Merge PR #7624: use validateContribution also for makers inputs | HenrikJannsen |
| 2026-05-04 | [3b5ae98849](https://github.com/bisq-network/bisq/commit/3b5ae98849d93ec1570066da8c289ec627a4cb9b) | Merge | Merge PR #7625: add check that inputs are P2WH | HenrikJannsen |
| 2026-05-04 | [912819996a](https://github.com/bisq-network/bisq/commit/912819996a256af033115850ead0da9b3788b787) | Merge | Merge PR #7626: remove changeOutputValue and address | HenrikJannsen |
| 2026-05-04 | [04c28c3ee7](https://github.com/bisq-network/bisq/commit/04c28c3ee7e52a7d61bfdceae866ff6a028d4298) | Merge | Merge PR #7631: add validation for trade message integrity | HenrikJannsen |
| 2026-05-04 | [aa15be9e21](https://github.com/bisq-network/bisq/commit/aa15be9e2124b057f15cafd378987fe78c76f56f) | Merge | Merge hotfix_1.9.24 into prevent-invalid-dpt | HenrikJannsen |
| 2026-05-04 | [844aec628f](https://github.com/bisq-network/bisq/commit/844aec628f1c80d6f53fc9dbf1098395ecb04b33) | Merge | Merge PR #7627: prevent invalid dpt | HenrikJannsen |
| 2026-05-04 | [5d8e3877fa](https://github.com/bisq-network/bisq/commit/5d8e3877fac0505e1aef245efc3fa1575bf2eece) | Commit | Refactor: Rename SendersNodeAddressMessage to SendersNodeAddressAwareEnvelope and rename related methods and variables | HenrikJannsen |
| 2026-05-04 | [4877e4b94e](https://github.com/bisq-network/bisq/commit/4877e4b94e8a94d38c8cd3078b37f167cf250785) | Merge | Merge PR #7635: reduce max price deviation | HenrikJannsen |
| 2026-05-04 | [fd4b152d5e](https://github.com/bisq-network/bisq/commit/fd4b152d5ea5e17f2cf820ad452cc7e7474b335d) | Merge | Merge PR #7636: enforce max price deviation in offers and trade | HenrikJannsen |
| 2026-05-04 | [07b1f3cf2f](https://github.com/bisq-network/bisq/commit/07b1f3cf2f573d93bacc602ce9e6b35fbf1a51d1) | Commit | Limit the max trade amount to 0.125 BTC | HenrikJannsen |
| 2026-05-05 | [0ac4977670](https://github.com/bisq-network/bisq/commit/0ac497767013a1d6403d5ffcdf9548cb1512815c) | Merge | Merge PR #7634: limit trade amount | HenrikJannsen |
| 2026-05-05 | [1c1154c266](https://github.com/bisq-network/bisq/commit/1c1154c2661cfbece89e610bd31c779754e22a53) | Commit | Add getTradeAmountAsCoin convenience method | HenrikJannsen |
| 2026-05-05 | [51df450585](https://github.com/bisq-network/bisq/commit/51df45058566ade60478580c7c9f18e98dab1d0c) | Commit | Improve error message | HenrikJannsen |
| 2026-05-05 | [277eda4f5c](https://github.com/bisq-network/bisq/commit/277eda4f5ca045f3e076fb3de6a3dafdad5b6480) | Commit | Replace broad deposit-input request validation with local checks for peer and offer ownership | HenrikJannsen |
| 2026-05-05 | [0c966feab5](https://github.com/bisq-network/bisq/commit/0c966feab5040e3c6a71e7a274f3c5f3890e9895) | Commit | Add check for checkTradeId(processModel.getOfferId(), message); to DisputeProtocol base methods when a new message arrives. | HenrikJannsen |
| 2026-05-05 | [9c5d847dd5](https://github.com/bisq-network/bisq/commit/9c5d847dd53e5d8facd2cf151710521447c5f9d5) | Commit | Add TradeValidation class | HenrikJannsen |
| 2026-05-05 | [70e07eab47](https://github.com/bisq-network/bisq/commit/70e07eab47ca60b166974b28e8fdaed8a87bb07b) | Commit | Add checkMultiSigPubKey method to TradeValidation and use that | HenrikJannsen |
| 2026-05-05 | [e25d3180eb](https://github.com/bisq-network/bisq/commit/e25d3180eb2b68ade0309a84dd93419f0f2b4a8b) | Commit | Add test for TradeValidation | HenrikJannsen |
| 2026-05-05 | [37b4e79496](https://github.com/bisq-network/bisq/commit/37b4e79496e41d853006dc84ce257580446895ba) | Commit | Move checks for rawTransactionInputs to TradeValidation | HenrikJannsen |
| 2026-05-04 | [22bbdcfdf2](https://github.com/bisq-network/bisq/commit/22bbdcfdf22f582e0b0180535dee6e7b002ab76e) | Commit | Validate maker change before taker signs deposit tx | KimStrand |
| 2026-05-05 | [cb49f649b4](https://github.com/bisq-network/bisq/commit/cb49f649b4d5f5611f216f905f835fbab2c59e3c) | Commit | Add checkBitcoinAddress and use it when we get an address string from a trade message. | HenrikJannsen |
| 2026-05-05 | [17771eb9e2](https://github.com/bisq-network/bisq/commit/17771eb9e274c6aaff029a111db9db3d15f9b9e2) | Merge | Merge PR #7638: validate maker deposit change | HenrikJannsen |
| 2026-05-05 | [089412533c](https://github.com/bisq-network/bisq/commit/089412533c5c7e9b98e39a991b9af21937cf7030) | Commit | Add checkPeersBurningManSelectionHeight | HenrikJannsen |
| 2026-05-05 | [9313c208ab](https://github.com/bisq-network/bisq/commit/9313c208ab1fc189c2d68f98618c6befac0bb96b) | Commit | Add checkSerializedTransaction, toTransaction and checkTransactionId | HenrikJannsen |
| 2026-05-05 | [acc25f21e5](https://github.com/bisq-network/bisq/commit/acc25f21e5331d6e5c9876b13087ee59fa66c841) | Commit | Add checkSignature method to TradeValidation | HenrikJannsen |
| 2026-05-05 | [4337ff9cc9](https://github.com/bisq-network/bisq/commit/4337ff9cc909503f59ce5dd41393f451490479ac) | Commit | Remove unused trade validation parameter | HenrikJannsen |
| 2026-05-04 | [7c5bd33355](https://github.com/bisq-network/bisq/commit/7c5bd333555642698bf9e9f97e51b0ecdd33dd54) | Commit | Verify encrypted message sender addresses at network layer | HenrikJannsen |
| 2026-05-05 | [4253a51154](https://github.com/bisq-network/bisq/commit/4253a511548fbd96b9b9ac264060e34153f30db1) | Commit | Add the check also for AckMessage | HenrikJannsen |
| 2026-05-06 | [dae40bf766](https://github.com/bisq-network/bisq/commit/dae40bf766ccca5ee662c9af902b6c3c080bf6c2) | Commit | Make small validation and logging improvements | HenrikJannsen |
| 2026-05-05 | [c0b1437010](https://github.com/bisq-network/bisq/commit/c0b143701028c362c8d2664084d40175d279f101) | Commit | Add getCheckedMediatorPubKeyRing and checkPeersDate | HenrikJannsen |
| 2026-05-05 | [9bb7a4f292](https://github.com/bisq-network/bisq/commit/9bb7a4f29228dc86787a02194a76816556792416) | Commit | Add checkTakersTradePrice | HenrikJannsen |
| 2026-05-05 | [a81dfb3175](https://github.com/bisq-network/bisq/commit/a81dfb3175b72e3644611fd9045099d5c2b55ddc) | Commit | Add checkLockTime, checkTxFee, checkTakerFee and checkBase64Signature methods | HenrikJannsen |
| 2026-05-05 | [787ff74f37](https://github.com/bisq-network/bisq/commit/787ff74f3713d69bcf66b689eaf50e4639f9f183) | Commit | Add separators and rearrange methods | HenrikJannsen |
| 2026-05-05 | [255e2f1af5](https://github.com/bisq-network/bisq/commit/255e2f1af5444bf3c7f0572e9eb1401f0ad28c90) | Commit | Add null checks to PubKeyRing | HenrikJannsen |
| 2026-05-05 | [d75bb2a61c](https://github.com/bisq-network/bisq/commit/d75bb2a61c25a066d6fba8a7e8353002a2376890) | Commit | Add checkInputsForDepositTxRequest to validate full message at handleTakeOfferRequest | HenrikJannsen |
| 2026-05-05 | [4bfb11947d](https://github.com/bisq-network/bisq/commit/4bfb11947d09bdd69cd819535f11add3270c3895) | Commit | Add focused test coverage for the associated validation change | HenrikJannsen |
| 2026-05-05 | [988ed66248](https://github.com/bisq-network/bisq/commit/988ed66248ae4304a97ef5a21afe08b339cf6b64) | Commit | Refactor: Move checkTradeId and isTradeIdValid to TradeValidation | HenrikJannsen |
| 2026-05-05 | [df4e09fbc3](https://github.com/bisq-network/bisq/commit/df4e09fbc3fcc79b43d1e7485f3cc4a95d93fe74) | Commit | Refactor: Move TradeValidation to trade package | HenrikJannsen |
| 2026-05-05 | [e4e0ca1c73](https://github.com/bisq-network/bisq/commit/e4e0ca1c7320ad047a568657b8f61cc3ed72a40c) | Commit | Return tradeId at checkTradeId. Add null checks | HenrikJannsen |
| 2026-05-05 | [d3613993c4](https://github.com/bisq-network/bisq/commit/d3613993c455407cbc38626b934c67e0bc7c5f3f) | Commit | Add focused test coverage for the associated validation change | HenrikJannsen |
| 2026-05-06 | [c09ad136c0](https://github.com/bisq-network/bisq/commit/c09ad136c07adc35803199f707175af7e67c3bff) | Commit | Add try/catch in TradeManager Add null checks | HenrikJannsen |
| 2026-05-06 | [8b1975e761](https://github.com/bisq-network/bisq/commit/8b1975e761f607f8cab212ff73e051ccfc4fdf34) | Merge | Merge PR #7641: add TradeValidation class | HenrikJannsen |
| 2026-05-06 | [36803447bc](https://github.com/bisq-network/bisq/commit/36803447bca8f3775ca2d63b3d88235e9c2a18ff) | Commit | Add checkNonBlankString method | HenrikJannsen |
| 2026-05-06 | [3ed3676623](https://github.com/bisq-network/bisq/commit/3ed367662396cc824594e702a881e08e162812a9) | Commit | Improve checkBase64Signature method | HenrikJannsen |
| 2026-05-06 | [fa6f825ea9](https://github.com/bisq-network/bisq/commit/fa6f825ea9dba62ef5d7b3e269361d2781f094fe) | Merge | Merge PR #7644: add more validation to TradeValidation | HenrikJannsen |
| 2026-05-06 | [0b938fedfd](https://github.com/bisq-network/bisq/commit/0b938fedfd1c1b78ba7c13f50c95d60920d7cae8) | Merge | Merge PR #7632: verify sender address at network layer | HenrikJannsen |
| 2026-05-04 | [4c389c9105](https://github.com/bisq-network/bisq/commit/4c389c910527932363fd0a7d7a4d47b8d349ee8d) | Commit | Verify encrypted message sender addresses at network layer | HenrikJannsen |
| 2026-05-04 | [15dd6bb606](https://github.com/bisq-network/bisq/commit/15dd6bb606c16fd56ea82bc25da45e3e694c82cd) | Commit | Verify sender address for AckMessage. | HenrikJannsen |
| 2026-05-05 | [b82866713c](https://github.com/bisq-network/bisq/commit/b82866713c8c1a93b3ee5db38145ebdf30f150ac) | Commit | Verify that payload sender signature keys match the envelope signing key | HenrikJannsen |
| 2026-05-06 | [f878af383a](https://github.com/bisq-network/bisq/commit/f878af383a171ec699f6b4739592a211dbf338dd) | Commit | Remove obsolete nullable annotation | HenrikJannsen |
| 2026-05-06 | [f3b42f5b96](https://github.com/bisq-network/bisq/commit/f3b42f5b96c067a7f9b10775685eee911d5e405c) | Commit | Call ignoredMailboxService.ignore also on non crypto exceptions | HenrikJannsen |
| 2026-05-06 | [82b3b17fd2](https://github.com/bisq-network/bisq/commit/82b3b17fd2b3145f97ce8642cea3bb3a66724956) | Commit | Refactoring: Rename SendersSignaturePubKeyAwarePayload to SendersSignaturePubKeyProvidingPayload | HenrikJannsen |
| 2026-05-06 | [0ba10bd84d](https://github.com/bisq-network/bisq/commit/0ba10bd84d2c0d4588b780f8b1a7cc8ec49025e4) | Commit | Refactoring: Rename SendersNodeAddressAwarePayload to SendersNodeAddressProvidingPayload | HenrikJannsen |
| 2026-05-06 | [1a28645f34](https://github.com/bisq-network/bisq/commit/1a28645f343a91c1ddbddc6dda23dc7bbbe38cfc) | Commit | Refactoring: Rename remaining variables, comments, methods and tests | HenrikJannsen |
| 2026-05-06 | [5965eeca98](https://github.com/bisq-network/bisq/commit/5965eeca9859eb89305196b954597cb5ce0265a2) | Commit | Improve network validation logging | HenrikJannsen |
| 2026-05-06 | [d8631cc288](https://github.com/bisq-network/bisq/commit/d8631cc288251d910c79876a8380bf4e2b976067) | Commit | Remove redundant type checks and harden test coverage | HenrikJannsen |
| 2026-05-06 | [5c60120798](https://github.com/bisq-network/bisq/commit/5c601207982f6273794f755f9c37a695db13279a) | Merge | Merge PR #7642: add network level verification of sender pub key in payloads | HenrikJannsen |
| 2026-05-05 | [7afd3962ff](https://github.com/bisq-network/bisq/commit/7afd3962ffefe894fc6fd6c75c80e4c18d96ff7e) | Commit | Remove takerPaymentAccountPayload and require taker payment hash/method values | HenrikJannsen |
| 2026-05-06 | [1320c8e285](https://github.com/bisq-network/bisq/commit/1320c8e28545f5665862c9553a3b10ee637d8395) | Commit | Validate offer availability response peer | KimStrand |
| 2026-05-06 | [44d539854d](https://github.com/bisq-network/bisq/commit/44d539854d8cb8e73ab9f9fbd68ed83368bc67fe) | Merge | Merge PR #7650: validate offer availability peer | HenrikJannsen |
| 2026-05-06 | [789c5660c1](https://github.com/bisq-network/bisq/commit/789c5660c1820eb35d878ca02ded193c02da6321) | Merge | Merge PR #7649: cleanup fields from 1.7.0 changes | HenrikJannsen |
| 2026-05-06 | [1585a7180b](https://github.com/bisq-network/bisq/commit/1585a7180b8f74bf7264d9392d697fb2abef3a9a) | Commit | Remove optionality handling in InputsForDepositTxRequest | HenrikJannsen |
| 2026-05-06 | [4d9d60d151](https://github.com/bisq-network/bisq/commit/4d9d60d151cd74897e18f15f64c32dc058260277) | Commit | Add focused tests for trade peer tx input validation | KimStrand |
| 2026-05-06 | [750faa4f49](https://github.com/bisq-network/bisq/commit/750faa4f49a18a4a0ef8599d3c1d14b36fa1c35a) | Commit | Remove takerPaymentAccountPayload from deposit response and require taker payment hash/method values | HenrikJannsen |
| 2026-05-06 | [5c8c823e12](https://github.com/bisq-network/bisq/commit/5c8c823e1287882fc809374dba7acf97daa49497) | Merge | Merge PR #7652: remove unused fields | HenrikJannsen |
| 2026-05-06 | [2f32810593](https://github.com/bisq-network/bisq/commit/2f32810593bfe535c5ddb0f922b606767be6f8b6) | Commit | Improve checks for fees and add tolerance | HenrikJannsen |
| 2026-05-06 | [5acc24fd6a](https://github.com/bisq-network/bisq/commit/5acc24fd6a8d62f265ccb28acd4c1b16a71372a3) | Commit | Improve checkLockTime | HenrikJannsen |
| 2026-05-06 | [0b9bbfcd7f](https://github.com/bisq-network/bisq/commit/0b9bbfcd7fc758ce4bec3ccdcb7ebd20b7a60c37) | Commit | Improve fee validation checks and tests | HenrikJannsen |
| 2026-05-06 | [b5e1dfd379](https://github.com/bisq-network/bisq/commit/b5e1dfd379f24f29df6f5b41b7515a24041172a1) | Merge | Merge PR #7645: improve fee validation | HenrikJannsen |
| 2026-05-06 | [55bf749681](https://github.com/bisq-network/bisq/commit/55bf749681b768f39291b3a2bbfb2652719565ac) | Commit | Move trade validation helpers into validation package | KimStrand |
| 2026-05-06 | [b46c42889d](https://github.com/bisq-network/bisq/commit/b46c42889ddfeaaf061374ea34b4a5c7d062c459) | Commit | Resolve merge conflict fallout in validation code | HenrikJannsen |
| 2026-05-06 | [40912869c3](https://github.com/bisq-network/bisq/commit/40912869c3773383b1d7593089150ebb6cbfef6b) | Merge | Merge PR #7654: create trade validation package rebased | HenrikJannsen |
| 2026-05-06 | [ccae388ca2](https://github.com/bisq-network/bisq/commit/ccae388ca2c39524433ed371d44e5e8577b938ec) | Commit | Add multisig pubkey curve validation tests | KimStrand |
| 2026-05-06 | [2761b14bd3](https://github.com/bisq-network/bisq/commit/2761b14bd34ef4dbde67c07ba30dc2b177999845) | Merge | Merge PR #7646: add multisig pubkey curve tests | HenrikJannsen |
| 2026-05-06 | [1a60e9c535](https://github.com/bisq-network/bisq/commit/1a60e9c535cc071b94d8ef8cd466d44bdf3e82d2) | Merge | Merge PR #7640: add trade peer tx input validator tests | HenrikJannsen |
| 2026-05-06 | [0b4fdb2742](https://github.com/bisq-network/bisq/commit/0b4fdb27428b49f71bac7c35a8ff0a839f5a7ff1) | Commit | Add checkTransactionIsUnsigned and checkDerEncodedEcdsaSignature Rename toTransaction to toVerifiedTransaction | HenrikJannsen |
| 2026-05-06 | [3f12f9d56b](https://github.com/bisq-network/bisq/commit/3f12f9d56b93c44fd7448772e19c58fbb10a2379) | Commit | Remove unused param | HenrikJannsen |
| 2026-05-06 | [eac8420233](https://github.com/bisq-network/bisq/commit/eac84202330373bc06b2abeed4bf2a1793096b07) | Commit | Add checks to BuyerAsTakerSendsDepositTxMessage | HenrikJannsen |
| 2026-05-06 | [a5119f5adb](https://github.com/bisq-network/bisq/commit/a5119f5adb1ac5f7548b820efe0b55d705355d63) | Commit | Add checks to SellerAsMakerProcessDepositTxMessage | HenrikJannsen |
| 2026-05-06 | [811e27a1be](https://github.com/bisq-network/bisq/commit/811e27a1be0d10061c02417dc1e67cf1b59a2d1e) | Commit | Add checks to SellerProcessDelayedPayoutTxSignatureResponse | HenrikJannsen |
| 2026-05-06 | [16a19004da](https://github.com/bisq-network/bisq/commit/16a19004daf467266be43f0492c8b333c9d38f5a) | Commit | Add checks to BuyerProcessDelayedPayoutTxSignatureRequest | HenrikJannsen |
| 2026-05-06 | [933bf8b899](https://github.com/bisq-network/bisq/commit/933bf8b8992b59ae48be4b66bf21416944aaf0ea) | Commit | Clean up checkNotNull | HenrikJannsen |
| 2026-05-06 | [fa5db811b0](https://github.com/bisq-network/bisq/commit/fa5db811b07690dd2a303ead043db7b11c10b686) | Commit | Extract variables in trade validation task code | HenrikJannsen |
| 2026-05-06 | [560f2483eb](https://github.com/bisq-network/bisq/commit/560f2483ebf822ee3900926f1dc6c453993ad792) | Commit | Rearrange and clean up trade validation logic | HenrikJannsen |
| 2026-05-06 | [a1bb12a319](https://github.com/bisq-network/bisq/commit/a1bb12a319fd2138d4bffe6caacccb76fe01068c) | Commit | Add checks for transactions | HenrikJannsen |
| 2026-05-06 | [5e2c1183b9](https://github.com/bisq-network/bisq/commit/5e2c1183b9833bd53e400dedbb0bf2d7b6b512f3) | Commit | Rename variables | HenrikJannsen |
| 2026-05-07 | [db515285f8](https://github.com/bisq-network/bisq/commit/db515285f8063e9058f67ae9f56a9fd84a5353b2) | Commit | Remove roundtrip encode/decode at checkBase64Signature and fix wrong method name | HenrikJannsen |
| 2026-05-07 | [f6e06ec441](https://github.com/bisq-network/bisq/commit/f6e06ec4410fbe655a7369fa0f6e7c2063327255) | Commit | Apply validation to ProcessMediatedPayoutTxPublishedMessage | HenrikJannsen |
| 2026-05-07 | [5d92623db6](https://github.com/bisq-network/bisq/commit/5d92623db6f7746c3cf1a81651a35e05ba4826b4) | Merge | Merge PR #7655: add more trade task checks | HenrikJannsen |
| 2026-05-07 | [334ea70604](https://github.com/bisq-network/bisq/commit/334ea70604502205ed1bf3c1c1671c4e9cc716d4) | Merge | Merge PR #7657: add validation to BuyerProcessDepositTxAndDelayedPayoutTxMessage | HenrikJannsen |
| 2026-05-07 | [d8aef211f0](https://github.com/bisq-network/bisq/commit/d8aef211f0e3204c24c9cb4e4a951fb963243b9f) | Commit | Use toVerifiedTransaction instead of checkSerializedTransaction and btcWalletService.getTxFromSerializedTx | HenrikJannsen |
| 2026-05-07 | [58114fce0e](https://github.com/bisq-network/bisq/commit/58114fce0e663389886a2aaef505ff8db03c14f1) | Commit | Add checks to BuyerProcessPayoutTxPublishedMessage | HenrikJannsen |
| 2026-05-07 | [b92f4aa87f](https://github.com/bisq-network/bisq/commit/b92f4aa87f8c02abad7de6bb328a5b434ab84453) | Merge | Merge PR #7662: add validation to ProcessMediatedPayoutTxPublishedMessage | HenrikJannsen |
| 2026-05-07 | [dac992de0d](https://github.com/bisq-network/bisq/commit/dac992de0d9f7c16ccce23d727b0aa681e2ed849) | Merge | Merge PR #7663: add validation to BuyerProcessPayoutTxPublishedMessage | HenrikJannsen |
| 2026-05-07 | [b8371521c3](https://github.com/bisq-network/bisq/commit/b8371521c34838d25f7670fe33cee89290cfa70f) | Commit | Clean up checkNotNull Extract variables | HenrikJannsen |
| 2026-05-07 | [06766ae12a](https://github.com/bisq-network/bisq/commit/06766ae12ab67a04b85974eca94094eb80af4b80) | Commit | Extract variables, rename | HenrikJannsen |
| 2026-05-07 | [30776aea30](https://github.com/bisq-network/bisq/commit/30776aea3011a0b9ba1865ded732c98f729e7dbc) | Commit | Use PaymentAccountPayload contract hashes directly instead of storing duplicate process-model hashes | HenrikJannsen |
| 2026-05-07 | [56988b0d17](https://github.com/bisq-network/bisq/commit/56988b0d17ffe80458a4b24e4be64e3787c4490d) | Commit | Use PaymentAccountPayload contract hashes directly instead of storing duplicate process-model hashes | HenrikJannsen |
| 2026-05-07 | [fa97380986](https://github.com/bisq-network/bisq/commit/fa973809862c1d2cb69f382be2fc93e1cc322edd) | Commit | Add checkByteArrayWithExpected | HenrikJannsen |
| 2026-05-07 | [1538ae3957](https://github.com/bisq-network/bisq/commit/1538ae3957393a909803f885c2c2c347f1b2cca4) | Commit | Use checkByteArrayWithExpected and make more clear what and why we compare. | HenrikJannsen |
| 2026-05-07 | [7009dcdb25](https://github.com/bisq-network/bisq/commit/7009dcdb250f353c114b8015db1db42258555e3a) | Commit | Fix wrong validation variable name | HenrikJannsen |
| 2026-05-07 | [9185167b0e](https://github.com/bisq-network/bisq/commit/9185167b0e2d33d764645a56a2cd0e7c9b846a89) | Commit | Remove unused RefreshTradeStateRequest | HenrikJannsen |
| 2026-05-07 | [5834189f80](https://github.com/bisq-network/bisq/commit/5834189f8030722161b6d28af32446d7659774f7) | Commit | Remove unused TraderSignedWitnessMessage | HenrikJannsen |
| 2026-05-07 | [1fac1429c1](https://github.com/bisq-network/bisq/commit/1fac1429c16aafe3f7ae84d1f8ebcd9bdc6ee380) | Merge | Merge PR #7665: remove unused RefreshTradeStateRequest | HenrikJannsen |
| 2026-05-07 | [2666a340b5](https://github.com/bisq-network/bisq/commit/2666a340b555d4bce5aa2a5adc1b84c88e41b786) | Merge | Merge PR #7666: remove unused TraderSignedWitnessMessage | HenrikJannsen |
| 2026-05-07 | [174cafb2c8](https://github.com/bisq-network/bisq/commit/174cafb2c86a359078afcce3333493958735fab9) | Commit | Dont log full hex of byte array but truncate to 8 chars | HenrikJannsen |
| 2026-05-07 | [933b98bd4e](https://github.com/bisq-network/bisq/commit/933b98bd4e0226007e982c6538617350df083eb2) | Commit | Add focused test coverage for the associated validation change | HenrikJannsen |
| 2026-05-07 | [7d80485684](https://github.com/bisq-network/bisq/commit/7d804856842a97b81d68c78158bfd38ded7684dd) | Commit | Remove obsolete Burning Man activation flags that are now always active | HenrikJannsen |
| 2026-05-07 | [4cd42e7a69](https://github.com/bisq-network/bisq/commit/4cd42e7a694f5f4ab0b0a51c68cc8811a05dfd98) | Commit | Extract variables, rename, rearrange | HenrikJannsen |
| 2026-05-07 | [9d7ccfd92d](https://github.com/bisq-network/bisq/commit/9d7ccfd92d82f8260540b24e2778f9d708b2852d) | Commit | Remove getReceivers methods which had 2 boolean parameters which are now always true | HenrikJannsen |
| 2026-05-07 | [e6cb85e0bf](https://github.com/bisq-network/bisq/commit/e6cb85e0bf362c5e8b8ffccda91c197afec7c0e4) | Commit | Remove getReceiverAddress method with boolean parameter as always true | HenrikJannsen |
| 2026-05-07 | [140ca3fa5e](https://github.com/bisq-network/bisq/commit/140ca3fa5ef758a00a1acb0148fd571df0230eb0) | Commit | Remove getActiveBurningManCandidates method with boolean parameter as always false | HenrikJannsen |
| 2026-05-07 | [985c014daf](https://github.com/bisq-network/bisq/commit/985c014daf8189b377a4cea376824fee42277f89) | Commit | Remove getBurningManCandidatesByName method with boolean parameter as always false | HenrikJannsen |
| 2026-05-07 | [6513c43dd3](https://github.com/bisq-network/bisq/commit/6513c43dd31dcd0004deda266a634793e9579180) | Commit | Remove boolean parameter from imposeCaps method as it is always false | HenrikJannsen |
| 2026-05-07 | [d93b68b8fc](https://github.com/bisq-network/bisq/commit/d93b68b8fc1c7061936571ae4f89300f207bcb95) | Commit | Remove mostRecentAddress as not accessed anymore only written but no read | HenrikJannsen |
| 2026-05-07 | [41eef3866c](https://github.com/bisq-network/bisq/commit/41eef3866cfc3b08d2f8540bef67a868b28627cd) | Commit | Adjust tests | HenrikJannsen |
| 2026-05-07 | [58ea38a72f](https://github.com/bisq-network/bisq/commit/58ea38a72f9ec83a65ea9b074765b7f109cef21d) | Commit | Add checkRawTransactionInputsAreNotMalleable | HenrikJannsen |
| 2026-05-07 | [2186727cf5](https://github.com/bisq-network/bisq/commit/2186727cf5f692e119de0849bdb318e47d0f9515) | Commit | Use checkRawTransactionInputsAreNotMalleable to check ours and peers inputs. | HenrikJannsen |
| 2026-05-07 | [945d8c4ed2](https://github.com/bisq-network/bisq/commit/945d8c4ed2f03e25b7d17d60d6171667f44b7b56) | Commit | Refactor: Rename method and variables | HenrikJannsen |
| 2026-05-07 | [609d43a9b4](https://github.com/bisq-network/bisq/commit/609d43a9b4999a4891d46b63c37aab99ec14a37b) | Commit | check burningManSelectionHeight rename variable | HenrikJannsen |
| 2026-05-07 | [08b24341a6](https://github.com/bisq-network/bisq/commit/08b24341a679ca62971bc5f54ddd8b972f531234) | Commit | Add checkDelayedPayoutTxInputAmount method | HenrikJannsen |
| 2026-05-07 | [ae91131ce6](https://github.com/bisq-network/bisq/commit/ae91131ce64fad8b509418e1b4dd3a16a18dd317) | Commit | Document removed TraderSignedWitnessMessage proto field | KimStrand |
| 2026-05-07 | [c463ca1523](https://github.com/bisq-network/bisq/commit/c463ca15236871f5ab179b510e6827034cda3374) | Commit | Add PayoutTxValidation | HenrikJannsen |
| 2026-05-07 | [fe72d656dc](https://github.com/bisq-network/bisq/commit/fe72d656dc3be092776c0fbd1a8d3506ba98072f) | Merge | Merge PR #7669: add various validations | HenrikJannsen |
| 2026-05-07 | [51a1b83c54](https://github.com/bisq-network/bisq/commit/51a1b83c54c9756b6dfd228f6f22c94a1cb8d7be) | Merge | Merge PR #7673: add PayoutTxValidation | HenrikJannsen |
| 2026-05-07 | [f0b60f1f55](https://github.com/bisq-network/bisq/commit/f0b60f1f55ae665269d8a149936cd66845a312d3) | Commit | Add focused test coverage for the associated validation change | HenrikJannsen |
| 2026-05-07 | [15f1b1155a](https://github.com/bisq-network/bisq/commit/15f1b1155a824d222608d81e720edebb3d0b486c) | Commit | Bound taker-supplied trade tx fee | wodoro |
| 2026-05-07 | [3452e30b8c](https://github.com/bisq-network/bisq/commit/3452e30b8c8fe1121ca2d390c4be6c781b95e312) | Merge | Merge PR #7674: add tests | HenrikJannsen |
| 2026-05-07 | [1a848a49da](https://github.com/bisq-network/bisq/commit/1a848a49da421da18412d46f9201db92082a42ac) | Commit | Remove getReceiverAddress method | HenrikJannsen |
| 2026-05-07 | [bb289d64cc](https://github.com/bisq-network/bisq/commit/bb289d64cc1db93fbfe754ec109e82d8767568fe) | Merge | Merge PR #7667: remove flags for support of old BM versions | HenrikJannsen |
| 2026-05-07 | [c44d3bdabe](https://github.com/bisq-network/bisq/commit/c44d3bdabe303d447b80ce00333e5970baa09ba3) | Merge | Merge PR #7658: 01 tx fee bounds | HenrikJannsen |
| 2026-05-07 | [8f6b7fbb32](https://github.com/bisq-network/bisq/commit/8f6b7fbb32d873b1a66b603dcdc76310dc8981ee) | Commit | Add MediatedPayoutTxValidation and tests | HenrikJannsen |
| 2026-05-07 | [c9c88d2157](https://github.com/bisq-network/bisq/commit/c9c88d21572ebe214c7153627a62336a0494cc24) | Merge | Merge PR #7671: Add MediatedPayoutTxValidation | HenrikJannsen |
| 2026-05-07 | [a6cd5d0151](https://github.com/bisq-network/bisq/commit/a6cd5d0151e20cfdc68b489a54ad603d5b371d5f) | Commit | Add DepositTxValidation | HenrikJannsen |
| 2026-05-07 | [976c7bdfba](https://github.com/bisq-network/bisq/commit/976c7bdfba4be50b76add4a7f4bfedb72504ecc8) | Merge | Merge PR #7672: document removed trader signed witness proto | HenrikJannsen |
| 2026-05-07 | [87bed257d5](https://github.com/bisq-network/bisq/commit/87bed257d5744977bc9ba78b9aea8a47132e33ae) | Merge | Merge PR #7675: Add DepositTxValidation | HenrikJannsen |
| 2026-05-07 | [f1dd86b82f](https://github.com/bisq-network/bisq/commit/f1dd86b82fd5ec03e98a15c2bb636125fe465495) | Commit | Reject non-P2WPKH maker inputs and non-default sequence/lockTime | wodoro |
| 2026-05-07 | [752c85e1ef](https://github.com/bisq-network/bisq/commit/752c85e1efeeb478d4ff218406a09e0ba4b3e4d9) | Merge | Merge PR #7676: strict maker inputs | HenrikJannsen |
| 2026-05-07 | [dbb568fa1e](https://github.com/bisq-network/bisq/commit/dbb568fa1eea43a957e9661ae8771640b48d5f37) | Commit | Re-verify deposit-tx multisig output in payout-signing tasks | wodoro |
| 2026-05-07 | [5a2b1ceae0](https://github.com/bisq-network/bisq/commit/5a2b1ceae0dba37fe25246a2d260afc25da74ff5) | Commit | Remove dead legacy P2SH branches in payout signing | wodoro |
| 2026-05-07 | [cc98d9135a](https://github.com/bisq-network/bisq/commit/cc98d9135ad2f12d8cc81a23dd31092e7ff7e49f) | Merge | Merge PR #7659: 04 payout multisig recheck | HenrikJannsen |
| 2026-05-07 | [86c03221bf](https://github.com/bisq-network/bisq/commit/86c03221bf01f2893430470f65c99120e5dbceba) | Commit | Refactor: Move checkTradeAmount and checkTakersTradePrice to DepositTxValidation | HenrikJannsen |
| 2026-05-07 | [b27a1df7e1](https://github.com/bisq-network/bisq/commit/b27a1df7e1dc527b5c32e81efd95215ba46339d8) | Commit | Refactor: Move checkBurningManSelectionHeight to DelayedPayoutTxValidation | HenrikJannsen |
| 2026-05-07 | [5c1bd473f6](https://github.com/bisq-network/bisq/commit/5c1bd473f66b44e62a4a62cd670a4cc75799a6de) | Commit | Refactor: Move checkMultiSigPubKey to DepositTxValidation | HenrikJannsen |
| 2026-05-07 | [c6a7e7724a](https://github.com/bisq-network/bisq/commit/c6a7e7724ae00e0ffb30a2e059ebd64b580ae8b2) | Commit | Refactor: Move generic methods to TradeValidationUtils | HenrikJannsen |
| 2026-05-07 | [8cf15b30ac](https://github.com/bisq-network/bisq/commit/8cf15b30ac6bbe5299e2a01a703eec385ad9324d) | Commit | Refactor: Move shared test methods and fields to TradeValidationTestUtils | HenrikJannsen |
| 2026-05-07 | [3ebd8b0552](https://github.com/bisq-network/bisq/commit/3ebd8b05524a81badc32d12424a185d7b3407091) | Commit | Refactor: Move checkInputsForDepositTxRequest to DepositTxValidation | HenrikJannsen |
| 2026-05-07 | [de159fb45e](https://github.com/bisq-network/bisq/commit/de159fb45ec6d61ccd50f83039b255cdb4cdea23) | Commit | Refactor: Move taker fee and maker fee methods to TradeFeeValidation | HenrikJannsen |
| 2026-05-07 | [9acada7fd3](https://github.com/bisq-network/bisq/commit/9acada7fd377c4de83e5a7ff5fb4a94beae09dc8) | Commit | Refactor: Move miner fee methods to MinerFeeValidation | HenrikJannsen |
| 2026-05-07 | [5f3813dbd4](https://github.com/bisq-network/bisq/commit/5f3813dbd4404c7ab8e12d1c7bb76093e876afd7) | Commit | Refactor: Move checkDelayedPayoutTxInputAmount to DelayedPayoutTxValidation | HenrikJannsen |
| 2026-05-07 | [96820f9ff5](https://github.com/bisq-network/bisq/commit/96820f9ff565b397f9b6c87767d753ad059ed347) | Commit | Refactor: Move lock time methods to DelayedPayoutTxValidation | HenrikJannsen |
| 2026-05-07 | [2800cf5cb7](https://github.com/bisq-network/bisq/commit/2800cf5cb748b6d0b1d6124778f9d3d0807f08a0) | Commit | Refactor: Move deposit tx related methods to DepositTxValidation | HenrikJannsen |
| 2026-05-07 | [e60736b4c3](https://github.com/bisq-network/bisq/commit/e60736b4c3466013fcd3502da5a75f691d239b8c) | Commit | Refactor: Move transaction related methods to TransactionValidation | HenrikJannsen |
| 2026-05-07 | [b01f166e16](https://github.com/bisq-network/bisq/commit/b01f166e164868db81b87ec68dfeb74f5b5ac9e7) | Commit | Refactor: Rename methods and variables to make more clear its DSA signature not Bitcoin EC signature | HenrikJannsen |
| 2026-05-07 | [aff3335733](https://github.com/bisq-network/bisq/commit/aff3335733fe434a3d1f1937917cdb13458af989) | Commit | Refactor: Move getCheckedMediatorPubKeyRing to DepositTxValidation | HenrikJannsen |
| 2026-05-07 | [ea0241b9f9](https://github.com/bisq-network/bisq/commit/ea0241b9f91aa9f9a653a00eacfe7ba739778f27) | Commit | Refactor: Move static fields to relevant classes | HenrikJannsen |
| 2026-05-07 | [f39ff93ca6](https://github.com/bisq-network/bisq/commit/f39ff93ca6e0638d8a7327caa80ee2915e20fd2d) | Commit | Remove TradePeerTxInputValidator and TradePeerTxInputValidatorTest and move methods to DepositTxValidation | HenrikJannsen |
| 2026-05-07 | [52461daaaa](https://github.com/bisq-network/bisq/commit/52461daaaa682021424f15a7c767bbfbad3654f7) | Commit | Refactor: Move checkByteArrayWithExpected to TradeValidation | HenrikJannsen |
| 2026-05-07 | [8a50a67dd0](https://github.com/bisq-network/bisq/commit/8a50a67dd049493eff0aafc34fbb9dad3971bb3b) | Commit | Refactor: Rename method | HenrikJannsen |
| 2026-05-07 | [50278ef0aa](https://github.com/bisq-network/bisq/commit/50278ef0aa22ebaf1ae399cfd0f5781d00f79007) | Commit | Refactor: Move amount and price related methods to new validation classes | HenrikJannsen |
| 2026-05-07 | [00a69b8b59](https://github.com/bisq-network/bisq/commit/00a69b8b59a1ade2b8eb69389d7b50511c5845f4) | Commit | Refactor: Move checkMultiSigPubKey to TransactionValidation | HenrikJannsen |
| 2026-05-07 | [824c57c05d](https://github.com/bisq-network/bisq/commit/824c57c05d8d4cf39ef40afe11e3bf91aa58a52e) | Commit | Refactor: Move getCheckedMediatorPubKeyRing to TradeValidation | HenrikJannsen |
| 2026-05-07 | [dce04c368f](https://github.com/bisq-network/bisq/commit/dce04c368f4a52107319c2cd74ec6507fe2c489e) | Commit | Refactor: Move checkInputsForDepositTxRequest to InputsForDepositTxRequestValidation | HenrikJannsen |
| 2026-05-07 | [1cae1ed962](https://github.com/bisq-network/bisq/commit/1cae1ed962f1e257b44f7d1645a5f3b41c670886) | Commit | Refactor: Move DSA signature related methods to DsaSignatureValidation | HenrikJannsen |
| 2026-05-07 | [469405957d](https://github.com/bisq-network/bisq/commit/469405957d246e8aa4ed75e96b9501ff1672884f) | Commit | Refactor: Move checkValueInTolerance to TradeValidation. Rename class | HenrikJannsen |
| 2026-05-07 | [9491c74ba3](https://github.com/bisq-network/bisq/commit/9491c74ba329dcbfa2cd2bbc6022e682357f18fc) | Commit | Refactor: Add comment separators and rearrange methods | HenrikJannsen |
| 2026-05-07 | [6932327d68](https://github.com/bisq-network/bisq/commit/6932327d6830454f3c5e038d485c15dbd2f84bff) | Commit | Make validation classes final with private constructor | HenrikJannsen |
| 2026-05-07 | [7413d24d4c](https://github.com/bisq-network/bisq/commit/7413d24d4cbafc0e3a9dcb00ef38b5acce3310b1) | Commit | Improve validation test coverage | HenrikJannsen |
| 2026-05-07 | [e2ff94768b](https://github.com/bisq-network/bisq/commit/e2ff94768b86cbf10cdd335cb3c60d4d88522323) | Commit | Extract duplicated code from  PayoutTxValidation and MediatedPayoutTxValidation to PayoutTxValidationUtils | HenrikJannsen |
| 2026-05-07 | [d7e79cad4f](https://github.com/bisq-network/bisq/commit/d7e79cad4f5d7c5ede9af9d624dc319cdbf111d5) | Commit | Clean up BuyerSignPayoutTx and resolve merge issues | HenrikJannsen |
| 2026-05-07 | [d0a0080cc2](https://github.com/bisq-network/bisq/commit/d0a0080cc267e22dd4d446fbc35e81e8deff505f) | Commit | Refactor: Change signature of validateDelayedPayoutTx | HenrikJannsen |
| 2026-05-07 | [d276cf4174](https://github.com/bisq-network/bisq/commit/d276cf4174e4765b7ca9587607045787be563009) | Commit | Refactor: Change signature of validateDelayedPayoutTx | HenrikJannsen |
| 2026-05-07 | [24e51fda7b](https://github.com/bisq-network/bisq/commit/24e51fda7b616435fe7bab6050522b931b947530) | Commit | Refactor: Rename method | HenrikJannsen |
| 2026-05-07 | [b52ac587ba](https://github.com/bisq-network/bisq/commit/b52ac587ba68da0bdca91ab73ee4a7cbf4e21ad5) | Commit | Refactor: Change signature of validateDelayedPayoutTxInput | HenrikJannsen |
| 2026-05-07 | [237ee8fbc8](https://github.com/bisq-network/bisq/commit/237ee8fbc8c0d66bb40d73db421fec3362999177) | Commit | Move delayed payout transaction validation from TradeDataValidation into DelayedPayoutTxValidation | HenrikJannsen |
| 2026-05-07 | [223b809a10](https://github.com/bisq-network/bisq/commit/223b809a106eb7677e2936622b8364d1725f2009) | Commit | Refactor: Move validateDepositInputs from TradeDataValidation to DepositTxValidation | HenrikJannsen |
| 2026-05-07 | [c1efab06bd](https://github.com/bisq-network/bisq/commit/c1efab06bd125c8e12283fe8bfe91a19ffff467e) | Commit | Move canonical deposit transaction checks into DepositTxValidation | HenrikJannsen |
| 2026-05-07 | [32286ae9bd](https://github.com/bisq-network/bisq/commit/32286ae9bd444b639f55d5a017c991d1c8290c05) | Commit | Refactor: Rearrange methods, make access package private | HenrikJannsen |
| 2026-05-07 | [ea5ab82c3c](https://github.com/bisq-network/bisq/commit/ea5ab82c3cd17e346ab38db0df144c51e2447c08) | Commit | Apply pattern used in all validation classes to the newly merged methods | HenrikJannsen |
| 2026-05-07 | [51084c996b](https://github.com/bisq-network/bisq/commit/51084c996bf49838e2fe951b665b69ac030aab42) | Commit | Avoid code duplication Add more validations Remove unused classes Cleanups | HenrikJannsen |
| 2026-05-07 | [bdff8de3a8](https://github.com/bisq-network/bisq/commit/bdff8de3a8b3ac7ac43d4c4ac553c43319e846bd) | Commit | Add extra checks and tests, cleanups | HenrikJannsen |
| 2026-05-07 | [3b1bdf8d85](https://github.com/bisq-network/bisq/commit/3b1bdf8d854bb004fe28679b33612aebab4164ec) | Merge | Merge PR #7678: reorganize validation classes | HenrikJannsen |
| 2026-05-08 | [5ab8145155](https://github.com/bisq-network/bisq/commit/5ab8145155d4ea853b83b6341fe12f51b01e4b57) | Merge | Merge PR #7677: dead p2sh payout branches | HenrikJannsen |
| 2026-05-08 | [acc0a7a2ff](https://github.com/bisq-network/bisq/commit/acc0a7a2ff4ade2071fffd4c3f9526d384f4229b) | Merge | Merge hotfix_1.9.24 into merged-hotfix-branch | HenrikJannsen |
| 2026-05-08 | [92c8d7aca9](https://github.com/bisq-network/bisq/commit/92c8d7aca91281beb108b42a090e6fd589b89b6c) | Commit | Resolve merge conflict fallout in validation code | HenrikJannsen |
| 2026-05-08 | [f6a8e3f720](https://github.com/bisq-network/bisq/commit/f6a8e3f7207b43963a38a2aea3a049a4b960fe28) | Commit | Use Address for comparison instead of string | HenrikJannsen |
| 2026-05-08 | [c38024c283](https://github.com/bisq-network/bisq/commit/c38024c2835afb9c9fd70678b3b728347c828995) | Commit | Use the original code to average the vsizeInVbytes with 233 in getTxFeeByVsize | HenrikJannsen |
| 2026-05-08 | [d2ff5632e0](https://github.com/bisq-network/bisq/commit/d2ff5632e01918eaf6d3e63f5b9fa04e350fd681) | Merge | Merge PR #7688: fix address comparison | HenrikJannsen |
| 2026-05-08 | [b5054e6cf1](https://github.com/bisq-network/bisq/commit/b5054e6cf1d6871a24b8e49957de85621e25c884) | Commit | Add null check to revert to original behavior | HenrikJannsen |
| 2026-05-08 | [7bcab3ec93](https://github.com/bisq-network/bisq/commit/7bcab3ec933480bb673cbacd5d2d6031bbdf087e) | Merge | Merge PR #7687: fix fee calculation regression | HenrikJannsen |
| 2026-05-08 | [aa380a8619](https://github.com/bisq-network/bisq/commit/aa380a86196d2873ed64f3b5112129d9a7c769b6) | Commit | Fix not updating index in loop | HenrikJannsen |
| 2026-05-08 | [843d55d75a](https://github.com/bisq-network/bisq/commit/843d55d75a2bc7a8b2d2ea2c5e3dd73df4630fb3) | Commit | Require non-zero burningManSelectionHeight for active trades | HenrikJannsen |
| 2026-05-08 | [42eb9f7135](https://github.com/bisq-network/bisq/commit/42eb9f713594b2e7a25a5915cdbae8ee36044c38) | Merge | Merge PR #7690: Fix not updating index in loop | HenrikJannsen |
| 2026-05-08 | [e09ee208fd](https://github.com/bisq-network/bisq/commit/e09ee208fd10af20c7373143ab4884bc4800ede5) | Merge | Merge PR #7691: harden burningManSelectionHeight check | HenrikJannsen |
| 2026-05-08 | [848b9a148c](https://github.com/bisq-network/bisq/commit/848b9a148c758b14074f549d63b30666e23af4e5) | Merge | Merge PR #7689: Do not trigger warning if delayed payout tx is null | HenrikJannsen |
| 2026-05-08 | [cea944a03d](https://github.com/bisq-network/bisq/commit/cea944a03d0704dcf618c92ff4e8becd21808416) | Merge | Merge hotfix_1.9.24 into merged-hotfix-branch | HenrikJannsen |
| 2026-05-08 | [6c26077e51](https://github.com/bisq-network/bisq/commit/6c26077e518acb9d788efaac2ae9008f42940e8a) | Commit | Use checkTransactionIsUnsigned at TakerProcessesInputsForDepositTxResponse Add checks Cleanups | HenrikJannsen |
| 2026-05-08 | [dc175381c8](https://github.com/bisq-network/bisq/commit/dc175381c8d07af1f7a7f74469db31895fdc114d) | Merge | Merge PR #7692: harden burningManSelectionHeight check | HenrikJannsen |
| 2026-05-08 | [d4e060f118](https://github.com/bisq-network/bisq/commit/d4e060f118d9af5341366f65b3ee68c9aa705d57) | Merge | Merge hotfix_1.9.24 into merged-hotfix-branch | HenrikJannsen |
| 2026-05-08 | [46df346414](https://github.com/bisq-network/bisq/commit/46df346414fb23cab80f98cad10cc06caa2c256f) | Merge | Merge PR #7680: merged hotfix branch | HenrikJannsen |
| 2026-05-08 | [3e29baf3fd](https://github.com/bisq-network/bisq/commit/3e29baf3fd9cd94cbb0b1cd20d363aad8d60515a) | Commit | Fix XMR auto-confirm quorum to exclude filter-banned services | wodoro |
| 2026-05-08 | [5cba1c2c87](https://github.com/bisq-network/bisq/commit/5cba1c2c87bf277242c91545b525ac8d22b78875) | Commit | Add cold storage reminder popup for high wallet balances | wodoro |
| 2026-05-08 | [b26d68878f](https://github.com/bisq-network/bisq/commit/b26d68878fead0564c33fc93fc0c62d2a2d81225) | Merge | Merge PR #7693: xmr fix | HenrikJannsen |
| 2026-05-08 | [ba6a0d64fe](https://github.com/bisq-network/bisq/commit/ba6a0d64fe58fda1021e5e7bfbf6e344b6da5b48) | Commit | Trade message integrity hardening | HenrikJannsen |
| 2026-05-08 | [eb599f732b](https://github.com/bisq-network/bisq/commit/eb599f732b39771feb1714bd3e39847f21587906) | Merge | Merge PR #7694: Trade message integrity hardening | HenrikJannsen |
| 2026-05-08 | [94e3f1a13c](https://github.com/bisq-network/bisq/commit/94e3f1a13c5748e97a063f2e58a0f8cc7c17249e) | Merge | Merge PR #7695: wallet cold storage warning | HenrikJannsen |
| 2026-05-08 | [e57f2a6d77](https://github.com/bisq-network/bisq/commit/e57f2a6d778f826e101757e3e7eda0099fe22a1e) | Commit | Implements the validation/commit cleanup for Bisq v1 trade-message processing. | HenrikJannsen |
| 2026-05-08 | [bc1f8139df](https://github.com/bisq-network/bisq/commit/bc1f8139dfd0cfb9f9118a9ee9bab279661a1cfc) | Commit | Added shared payout signature validation in PayoutTxValidation. | HenrikJannsen |
| 2026-05-08 | [6448d937dc](https://github.com/bisq-network/bisq/commit/6448d937dcd0b2851aec4dc74a67e136d4f7f38d) | Merge | Merge PR #7699: Ensure validation commit pattern is applied in trade message processing | HenrikJannsen |
| 2026-05-08 | [e557bae0d9](https://github.com/bisq-network/bisq/commit/e557bae0d98b79a40f941f5445c3218926f1d822) | Merge | Merge PR #7701: further protocol hardening | HenrikJannsen |
| 2026-05-08 | [578a98daa3](https://github.com/bisq-network/bisq/commit/578a98daa355304cfbe80707947027d7d234b5f2) | Commit | Add witness malleability validation to trade protocol | HenrikJannsen |
| 2026-05-08 | [fc2f7d2618](https://github.com/bisq-network/bisq/commit/fc2f7d2618e89c4471bcd99f012b0208ff1760e7) | Commit | Use Coin safe arithmetic methods instead of raw long arithmetic | HenrikJannsen |
| 2026-05-08 | [47b2cea32c](https://github.com/bisq-network/bisq/commit/47b2cea32c95dbc7474115b53593f6e1e5685995) | Merge | Merge PR #7703: Add witness malleability validation | HenrikJannsen |
| 2026-05-08 | [da266b6c38](https://github.com/bisq-network/bisq/commit/da266b6c38a30a8e2686c1956ebf0ff309255fc3) | Commit | Clean up validation and Burning Man address list code | HenrikJannsen |
| 2026-05-08 | [fe156db381](https://github.com/bisq-network/bisq/commit/fe156db3810578cb5947b47a481d4050ce5da3f9) | Commit | Add more tests | HenrikJannsen |
| 2026-05-08 | [b4e7b0610d](https://github.com/bisq-network/bisq/commit/b4e7b0610d8bc04666ee24d33aebe67c2d2a722e) | Merge | Merge PR #7704: Use safe arithmetic methods of Coin | HenrikJannsen |
| 2026-05-08 | [3a2e55d5b2](https://github.com/bisq-network/bisq/commit/3a2e55d5b2e798e26e79381fcdd05f05f70f585d) | Commit | Add DelayedPayoutTxSignatureValidation | HenrikJannsen |
| 2026-05-08 | [12a84fc212](https://github.com/bisq-network/bisq/commit/12a84fc212122cb2d9b63cadce09520c603cde3e) | Commit | Cleanups Dont use toFriendlyString in bsq context | HenrikJannsen |
| 2026-05-08 | [b68986562a](https://github.com/bisq-network/bisq/commit/b68986562a3761113a12da0240c434afef3e7691) | Merge | Merge PR #7706: add DelayedPayoutTxSignatureValidation | HenrikJannsen |
| 2026-05-09 | [7bd9ac0c81](https://github.com/bisq-network/bisq/commit/7bd9ac0c811a7a4e21759314e220324fc64a0698) | Commit | fix(p2p): mailbox invalid-payload handling gaps from #7632 / #7694 | wodoro |
| 2026-05-09 | [b066d777fb](https://github.com/bisq-network/bisq/commit/b066d777fbfa06416aef3ce1383186c6e42f1c41) | Commit | fix: use the passed param for deviation in chinese locale | wodoro |
| 2026-05-09 | [0c536b0cdf](https://github.com/bisq-network/bisq/commit/0c536b0cdf406865477c3390e28bfc52846a1433) | Commit | fix(offer): address review issues from #7636 max price deviation enforcement | wodoro |
| 2026-05-08 | [6378e74fd4](https://github.com/bisq-network/bisq/commit/6378e74fd4fc77bc0c35a32184a9bb5306a3835d) | Commit | Check all script signatures when seller adds buyer witnesses to the deposit transaction | HenrikJannsen |
| 2026-05-08 | [bf7457c74f](https://github.com/bisq-network/bisq/commit/bf7457c74faa9ff1099ac3a5dd610bb21abe41c4) | Commit | Use Coin safe arithmetic methods instead of raw long arithmetic | HenrikJannsen |
| 2026-05-09 | [c2659c3d70](https://github.com/bisq-network/bisq/commit/c2659c3d702b4f5c387dd260c600ef3997d0d735) | Commit | chore: fix test names | wodoro |
| 2026-05-09 | [cfbe1067f1](https://github.com/bisq-network/bisq/commit/cfbe1067f17c7a5a48e641fbf3f597c7772735d6) | Commit | Add checks for positive values in getAdjustedTxFee | HenrikJannsen |
| 2026-05-09 | [bca0c6387f](https://github.com/bisq-network/bisq/commit/bca0c6387f33eddf94aaabe8b4df446dadb0b5c9) | Commit | Add toVerifiedDerEncodedEcdsaSignature and avoid code duplication in DelayedPayoutTxSignatureValidation | HenrikJannsen |
| 2026-05-09 | [425d886a9f](https://github.com/bisq-network/bisq/commit/425d886a9f00e3bde7b4f3c97de11c389866cc0a) | Merge | Merge PR #7707: Call checkAllScriptSignaturesForTx at sellerAddsBuyerWitnessesToDepositTx | HenrikJannsen |
| 2026-05-08 | [be402d948a](https://github.com/bisq-network/bisq/commit/be402d948ad2677a475413081dc36d2ad098cb51) | Commit | Check if deposit tx is valid before taker publish the taker fee tx. | HenrikJannsen |
| 2026-05-09 | [49b5bbcea9](https://github.com/bisq-network/bisq/commit/49b5bbcea9b5a7655044bd53a9d939bfe56cbfa6) | Merge | Merge PR #7708: Check if deposit tx is valid before taker publish the taker fee tx | HenrikJannsen |
| 2026-05-09 | [dbc3cfd507](https://github.com/bisq-network/bisq/commit/dbc3cfd50739eba204a6ecf351b343c3e0062ec4) | Merge | Merge PR #7709: fix handling gaps | HenrikJannsen |
| 2026-05-09 | [df19a8fb83](https://github.com/bisq-network/bisq/commit/df19a8fb8343b68a3efeb94b2eabf7f28d8e9a57) | Merge | Merge PR #7710: fix deviation msg chinese | HenrikJannsen |
| 2026-05-09 | [fe23d7147c](https://github.com/bisq-network/bisq/commit/fe23d7147c2884857e280013c3a9bc9fbebb8d79) | Merge | Merge PR #7711: fix max price deviation review | HenrikJannsen |
| 2026-05-09 | [83a42fff9f](https://github.com/bisq-network/bisq/commit/83a42fff9f9591c72c4cb983076600672e0ca029) | Merge | Merge PR #7713: 7694 review | HenrikJannsen |
| 2026-05-09 | [e31b4bf4fe](https://github.com/bisq-network/bisq/commit/e31b4bf4fe084b1d4fce8795be2bd46245606e63) | Commit | Move deposit and payout transaction utility methods into dedicated helper classes | HenrikJannsen |
| 2026-05-09 | [f12d221572](https://github.com/bisq-network/bisq/commit/f12d221572e18d302b5febf686170ff80764429c) | Commit | Refactor: Use PayoutTransactionUtil.get2of2MultiSigRedeemScript in PayoutTxValidation | HenrikJannsen |
| 2026-05-09 | [717d9fdf9e](https://github.com/bisq-network/bisq/commit/717d9fdf9e0c2421b5c32a908804f0c0269ac222) | Commit | Harden BSQ swap fee/payout arithmetic | wodoro |
| 2026-05-09 | [02e6c870ed](https://github.com/bisq-network/bisq/commit/02e6c870ed70ec5ad223987b55c107f2cec939a2) | Merge | Merge PR #7712: harden bsq | HenrikJannsen |
| 2026-05-09 | [5702d9a3a3](https://github.com/bisq-network/bisq/commit/5702d9a3a3df65f136ca345a93f6f229a1808645) | Commit | Add null checks in validation path | HenrikJannsen |
| 2026-05-09 | [cbffe5c6e7](https://github.com/bisq-network/bisq/commit/cbffe5c6e70a22bd9f5a0f2a28e8030598a823d7) | Merge | Merge PR #7714: Extract util methods | HenrikJannsen |
| 2026-05-09 | [3504f0a25b](https://github.com/bisq-network/bisq/commit/3504f0a25b7f03d04f5b86ad42aabbeb7b127c06) | Commit | fix: raise xmr min confirmations to 2 | wodoro |
| 2026-05-09 | [04eba8269f](https://github.com/bisq-network/bisq/commit/04eba8269f41d4caa8ce2c03e26cdbd984ac98ea) | Commit | Validate canonical deposit tx fields at final boundaries | HenrikJannsen |
| 2026-05-09 | [ec7d41a730](https://github.com/bisq-network/bisq/commit/ec7d41a73082b5bcd8361156fef3da460af3a6ec) | Merge | Merge PR #7716: check canonical deposit tx | HenrikJannsen |
| 2026-05-09 | [305dae2e63](https://github.com/bisq-network/bisq/commit/305dae2e63997efd8c75ba73c55560ee312bcbfb) | Merge | Merge PR #7715: harden xmr | HenrikJannsen |
| 2026-05-09 | [ef5629b5db](https://github.com/bisq-network/bisq/commit/ef5629b5db4e64c4cd07a90b16221f2484619888) | Commit | Add additional checks for MultiSigPubKey | HenrikJannsen |
| 2026-05-09 | [0140ca187f](https://github.com/bisq-network/bisq/commit/0140ca187f59413d96c472a754205e07ffc5b0e1) | Merge | Merge PR #7717: add extra check for multiSigPubKey | HenrikJannsen |
| 2026-05-09 | [24b2f876dc](https://github.com/bisq-network/bisq/commit/24b2f876dc5db003dc16aeedcf3f99a2ab14715b) | Commit | Restore delayed payout notification sender change lost during merge | HenrikJannsen |
| 2026-05-09 | [33bf1e55c4](https://github.com/bisq-network/bisq/commit/33bf1e55c407377ec142d53ac7f5640960cfbf80) | Commit | Fix listener removal from the wrong property | HenrikJannsen |
| 2026-05-09 | [7528c3ae69](https://github.com/bisq-network/bisq/commit/7528c3ae69f4ae694eb3fa8298e060b41e68f62e) | Merge | Merge PR #7719: fix delayed payout notification sender | HenrikJannsen |
| 2026-05-09 | [1ca17c94c8](https://github.com/bisq-network/bisq/commit/1ca17c94c89d79430c8178e53c0f7d671e90d974) | Merge | Merge PR #7720: Fix listener removal from the wrong property | HenrikJannsen |
| 2026-05-09 | [88150d9b97](https://github.com/bisq-network/bisq/commit/88150d9b97eed7c342114f55e929dddbccdeae06) | Commit | Add program argument to dump BM data Add BurningManDataExportService | HenrikJannsen |
| 2026-05-09 | [a4f8083a18](https://github.com/bisq-network/bisq/commit/a4f8083a18f2fba3a523a7841d3b5a71b5c5bf97) | Commit | fix: harden httpclient | wodoro |
| 2026-05-09 | [b6b28234ea](https://github.com/bisq-network/bisq/commit/b6b28234eaac63bbcbc47148799fcd8e18caa83d) | Commit | Add dumpBurningManDataVersion program argument | HenrikJannsen |
| 2026-05-09 | [c4f4f1bc64](https://github.com/bisq-network/bisq/commit/c4f4f1bc649b56b176503d6573859009cc424434) | Commit | Remove webcam QR pairing from mobile notifications | wodoro |
| 2026-05-09 | [190c1734a4](https://github.com/bisq-network/bisq/commit/190c1734a4eacaff5a125632166c5bc70bf0c3d4) | Commit | Add Burning Man address list version support and delayed payout receiver validation | HenrikJannsen |
| 2026-05-09 | [5b3a805762](https://github.com/bisq-network/bisq/commit/5b3a805762240d4700fc926b9619d92ec1faa26b) | Merge | Merge PR #7718: harden httpclient | HenrikJannsen |
| 2026-05-09 | [9492c823b4](https://github.com/bisq-network/bisq/commit/9492c823b4f4b7314c5f50e21dc7cc8cb984a519) | Commit | Add validateDelayedPayoutTxReceivers | HenrikJannsen |
| 2026-05-09 | [0a72751c82](https://github.com/bisq-network/bisq/commit/0a72751c821c794adbe4e5ae86e296fccb1f1634) | Commit | Add 50% range check for `cappedBurnAmountShare` | HenrikJannsen |
| 2026-05-09 | [6587f91ea1](https://github.com/bisq-network/bisq/commit/6587f91ea17b8c98425258a78682d1a4eb122afa) | Merge | Merge PR #7721: remove qrcode | HenrikJannsen |
| 2026-05-09 | [dbd7406e3f](https://github.com/bisq-network/bisq/commit/dbd7406e3f86eb805dc2a077c587336b0687663e) | Commit | Clean up validation and Burning Man address list code | HenrikJannsen |
| 2026-05-09 | [1316bacd35](https://github.com/bisq-network/bisq/commit/1316bacd35d24532ba48b0582a92ea4d3cbc2585) | Commit | Remove dumpBurningManDataVersion program argument as its not really needed. | HenrikJannsen |
| 2026-05-09 | [93911b999e](https://github.com/bisq-network/bisq/commit/93911b999e0745b6ea6da89bcad98e93aabc7c83) | Merge | Merge PR #7722: add bm checkpoint | HenrikJannsen |
| 2026-05-10 | [ab9461dddb](https://github.com/bisq-network/bisq/commit/ab9461dddb0a8bfda00c6a3b0544ca8440332940) | Commit | set preferences in constructor to avoid NPE in dont ask again | wodoro |
| 2026-05-10 | [18db31a55e](https://github.com/bisq-network/bisq/commit/18db31a55ed454138c4ae334926e3b0ee541d3e1) | Merge | Merge PR #7726: fix dont show again race | HenrikJannsen |
| 2026-05-10 | [05e5bc45ce](https://github.com/bisq-network/bisq/commit/05e5bc45ce8ea984c576a5e1712f4624e2faed22) | Commit | feat: add allowClearnetHttpRequests feature flag | wodoro |
| 2026-05-10 | [69016899a9](https://github.com/bisq-network/bisq/commit/69016899a985492f1a7a8ca30d4a26e1e5146446) | Merge | Merge PR #7727: fix httpclient dev | HenrikJannsen |
| 2026-05-10 | [13c754f751](https://github.com/bisq-network/bisq/commit/13c754f75190e7c54f1ee6e9663f8795994cb77f) | Commit | Add ignorePopupsInDevMode flag and use it for conditional popup display | HenrikJannsen |
| 2026-05-10 | [aac847c141](https://github.com/bisq-network/bisq/commit/aac847c1415557da4acd91bf665736e89339ea9a) | Commit | Fix wrong validation call in BuyerProcessDepositTxAndDelayedPayoutTxMessage when deposit tx is not set yet. | HenrikJannsen |
| 2026-05-10 | [b888d3ea59](https://github.com/bisq-network/bisq/commit/b888d3ea59585b6732ca9a5f086d2fb7eb666854) | Commit | Allow clearnet if in dev mode | HenrikJannsen |
| 2026-05-08 | [439947e230](https://github.com/bisq-network/bisq/commit/439947e2309a779908b1efca8a424f82b0d5beb8) | Commit | Add a BSQ swap kill switch to the filter to allow for selective deactivate without halting all trading | KimStrand |
| 2026-05-10 | [20ac2beaf4](https://github.com/bisq-network/bisq/commit/20ac2beaf466401b4d2e39dde755cf79f6359abb) | Merge | Merge PR #7729: fix wrong access to deposit tx in BuyerProcessDepositTxAndDelayedPayoutTxMessage | HenrikJannsen |
| 2026-05-10 | [31236dc02e](https://github.com/bisq-network/bisq/commit/31236dc02e3a4cd6500fa1136d287191216cabb8) | Merge | Merge PR #7730: allow clearnet if in devmode | HenrikJannsen |
| 2026-05-10 | [5a0d33d03b](https://github.com/bisq-network/bisq/commit/5a0d33d03b48ecf3f5e8f7f7f69cea20268bd088) | Commit | Apply isIgnorePopupsInDevMode to most other popups. | HenrikJannsen |
| 2026-05-10 | [ce75733c14](https://github.com/bisq-network/bisq/commit/ce75733c14e2de7595a4594716e42693af49e041) | Merge | Merge PR #7731: add missing disable bsq swap filter commit | HenrikJannsen |
| 2026-05-10 | [15bb8d895a](https://github.com/bisq-network/bisq/commit/15bb8d895a45ee1bf165ce9b60877a72a3d477eb) | Merge | Merge PR #7728: add ignorePopupsInDevMode flag | HenrikJannsen |
| 2026-05-08 | [3f01854fda](https://github.com/bisq-network/bisq/commit/3f01854fdadd032cd5c997de6bbdeacf95301cf7) | Commit | Update Bisq build and runtime to Java 17 | KimStrand |
| 2026-05-09 | [56e7d68973](https://github.com/bisq-network/bisq/commit/56e7d689730ef8cd9d522467b34040a907929197) | Commit | Use javafx 21 and fix some jfoenix classes | HenrikJannsen |
| 2026-05-10 | [a88c6bed9f](https://github.com/bisq-network/bisq/commit/a88c6bed9f53dc07cb4e6ecec8c3d212fe28632a) | Commit | Add mockito-inline | HenrikJannsen |
| 2026-05-10 | [57068d180b](https://github.com/bisq-network/bisq/commit/57068d180b2bfb54c12d3e61466f927c001ccdb0) | Commit | Update library dependencies | HenrikJannsen |
| 2026-05-10 | [f399611920](https://github.com/bisq-network/bisq/commit/f3996119200b7e09c4b32401089e25b8dae4a79f) | Commit | Keep compatible Bouncy Castle artifact where newer runtime breaks bitcoinj | HenrikJannsen |
| 2026-05-10 | [d553f05956](https://github.com/bisq-network/bisq/commit/d553f05956a453cdf0412ac64a92e073a8835c1e) | Commit | Update protobuf related libs | HenrikJannsen |
| 2026-05-10 | [6292a014f7](https://github.com/bisq-network/bisq/commit/6292a014f7d9d31f39013a77505de524c440921f) | Commit | Use https://github.com/bisq-network/bitcoind/tree/update_to_java21 for bitcoind submodule | HenrikJannsen |
| 2026-05-10 | [f80fb8c7e5](https://github.com/bisq-network/bisq/commit/f80fb8c7e5afd117b4ec3580b1d5473faee9f1ec) | Commit | Set Java 21 for CI | HenrikJannsen |
| 2026-05-10 | [a16fdc4735](https://github.com/bisq-network/bisq/commit/a16fdc473590622b9451542565c182361739ca17) | Commit | Remove unused runtime/dependency update class | HenrikJannsen |
| 2026-05-10 | [3fa7e9cdb4](https://github.com/bisq-network/bisq/commit/3fa7e9cdb49167e8449a4439ffd02fca24319e39) | Commit | Use Azul Zulu JDK 21 for Debian release builds | HenrikJannsen |
| 2026-05-10 | [09b1d0e3b7](https://github.com/bisq-network/bisq/commit/09b1d0e3b79f5f644b650637efea3ee349678074) | Commit | Clean up leftover runtime version update | HenrikJannsen |
| 2026-05-10 | [97c8419263](https://github.com/bisq-network/bisq/commit/97c8419263d883e064b3f9886c89cbc82fb3f0a2) | Commit | Declare explicit Bouncy Castle provider dependency | HenrikJannsen |
| 2026-05-10 | [f7fea268b7](https://github.com/bisq-network/bisq/commit/f7fea268b7d98a6a0d1af45a75b7d5b4c60f4d77) | Commit | Remove `--add-opens=java.base/java.lang.reflect=ALL-UNNAMED` | HenrikJannsen |
| 2026-05-10 | [bcc0051f83](https://github.com/bisq-network/bisq/commit/bcc0051f836166545877bd4f75175190f376e2ff) | Commit | Correct build/runtime documentation instruction | HenrikJannsen |
| 2026-05-10 | [f244cbf659](https://github.com/bisq-network/bisq/commit/f244cbf6599812f186003707018dbc45a2004df3) | Commit | Use bitcoind https://github.com/bisq-network/bitcoind/commit/ed2f1e6572a04c2d83fd21e32ef1277a57c7c31d | HenrikJannsen |
| 2026-05-10 | [225ab67ff0](https://github.com/bisq-network/bisq/commit/225ab67ff0cba27193cb78c2fa0169a19ae13e89) | Commit | Remove not needed add-exports | HenrikJannsen |
| 2026-05-10 | [ea857e0fa8](https://github.com/bisq-network/bisq/commit/ea857e0fa8695f7fecbfce9d9e09676ae719848b) | Commit | Fix Debian packaging script for updated runtime | HenrikJannsen |
| 2026-05-10 | [b061128d9f](https://github.com/bisq-network/bisq/commit/b061128d9fe186254844571c71e99abb67b2b784) | Commit | Fix formatting in dependency/runtime update | HenrikJannsen |
| 2026-05-10 | [56b7b1768b](https://github.com/bisq-network/bisq/commit/56b7b1768bb063b7c107f603fd512ef1c4a5c652) | Commit | Add fingerprint validation for Azul's signing key before importing. | HenrikJannsen |
| 2026-05-10 | [bd24fabac6](https://github.com/bisq-network/bisq/commit/bd24fabac6f57eb3922d0c4e42a0fc3f8c70f4e6) | Commit | Remove travis as not used anymore | HenrikJannsen |
| 2026-05-10 | [adf40ba653](https://github.com/bisq-network/bisq/commit/adf40ba65351acbc49c6ce27e6455b67381f86b5) | Commit | Fix dependency metadata casing | HenrikJannsen |
| 2026-05-10 | [a4e0fc0e04](https://github.com/bisq-network/bisq/commit/a4e0fc0e048975e19c4552a7a1c2a1b6389bf7dc) | Commit | Update dependency verification metadata | HenrikJannsen |
| 2026-05-10 | [2dad8fa745](https://github.com/bisq-network/bisq/commit/2dad8fa745f43035aae342d19b47e5008c5613dc) | Commit | Update dependency verification metadata | HenrikJannsen |
| 2026-05-10 | [d75b2a8a4e](https://github.com/bisq-network/bisq/commit/d75b2a8a4ef7583f7c822eefb508ae3a5b5cdba8) | Commit | Fix stale TradePeerTxInputValidator references and harden validation tests | wodoro |
| 2026-05-10 | [a84c82b5f5](https://github.com/bisq-network/bisq/commit/a84c82b5f5cdeda1d9a13c195aa1c560fd4ccec1) | Commit | Re-clamp trade amount against MAX_TRADE_AMOUNT in checkTradeAmount | wodoro |
| 2026-05-06 | [39020b6081](https://github.com/bisq-network/bisq/commit/39020b60819b0a5605b458ea759e69260ad06c96) | Commit | Bound peer input count, parentTransaction size, and dedup outpoints | wodoro |
| 2026-05-10 | [515858c78c](https://github.com/bisq-network/bisq/commit/515858c78c914e59a9d5daf5de15c80ba780458d) | Commit | Move libs.grpc.netty.shaded dependency to bridge module | HenrikJannsen |
| 2026-05-10 | [0677f3a85a](https://github.com/bisq-network/bisq/commit/0677f3a85a37dcd44826170681549bfd0d9275b5) | Commit | Add the missing kotlin-stdlib-common 2.3.20 artifacts to this metadata file. | HenrikJannsen |
| 2026-05-10 | [aba1061ece](https://github.com/bisq-network/bisq/commit/aba1061eceaf7c9eb81bb4d2e4be782f88450879) | Commit | Update bitcoind to df1fc66f41f2fa3f56a821a4c05aacb4450c59a8 | HenrikJannsen |
| 2026-05-10 | [a0e5eb6f46](https://github.com/bisq-network/bisq/commit/a0e5eb6f46a143d4a5fe1cb01dff5ef0c378001d) | Commit | Fix build by importing VersionCatalogsExtension | HenrikJannsen |
| 2026-05-10 | [cb0c7ba2f1](https://github.com/bisq-network/bisq/commit/cb0c7ba2f1437ec6c835108f7f13a8680d273fe9) | Merge | Merge PR #7725: update dependencies | HenrikJannsen |
| 2026-05-10 | [0d68432b89](https://github.com/bisq-network/bisq/commit/0d68432b89782725443c7bfcc0952f46e6486c34) | Commit | Use latest bitcoinj with upstream P2WPKH verification fix | HenrikJannsen |
| 2026-05-10 | [4557341e1e](https://github.com/bisq-network/bisq/commit/4557341e1e4d9d0f43207ee66420870758b995bb) | Merge | Merge PR #7732: deposit tx validation cleanup | HenrikJannsen |
| 2026-05-10 | [47a34c8bc5](https://github.com/bisq-network/bisq/commit/47a34c8bc59c147b8d57e0c5baa4284f58aefce7) | Merge | Merge PR #7733: 11 input size and dedup | HenrikJannsen |
| 2026-05-10 | [5194ee06b1](https://github.com/bisq-network/bisq/commit/5194ee06b1c225b1f4a8356a5612be7322c5cb93) | Merge | Merge PR #7734: 02 reclamp trade amount | HenrikJannsen |
| 2026-05-10 | [b228511853](https://github.com/bisq-network/bisq/commit/b228511853f635f0df9a391d8ab6fc68950ce7ef) | Commit | Fix follow-ups from #7642 sender pub key verification review | wodoro |
| 2026-05-10 | [b6170834c2](https://github.com/bisq-network/bisq/commit/b6170834c285ea8f18452c67e8e52d4dde9201e9) | Merge | Merge PR #7735: update bitcoinj | HenrikJannsen |
| 2026-05-10 | [ff0536270b](https://github.com/bisq-network/bisq/commit/ff0536270bf765db2e8fc09a8a4a2e3de2b615f4) | Commit | Unreserve open offer when take-offer request fails | wodoro |
| 2026-05-10 | [8b4be47645](https://github.com/bisq-network/bisq/commit/8b4be476452523bb947fe77d9a825cc4f5ffd995) | Merge | Merge PR #7736: 7642 followups | HenrikJannsen |
| 2026-05-10 | [5c33297308](https://github.com/bisq-network/bisq/commit/5c332973086f5035fa109e9b3fdea497dfc886b9) | Merge | Merge PR #7738: pr 7644 followups race and cleanup | HenrikJannsen |
| 2026-05-10 | [1d51f0d895](https://github.com/bisq-network/bisq/commit/1d51f0d89590226073062511936aafdaf83e693f) | Commit | Tighten visibility and document magic vsize constants in fee validation | wodoro |
| 2026-05-10 | [0a6f67d987](https://github.com/bisq-network/bisq/commit/0a6f67d9875dd138a2414160b3a6d3f70e386398) | Commit | Restore JFXProgressBarSkin shadow class | wodoro |
| 2026-05-10 | [46947dd448](https://github.com/bisq-network/bisq/commit/46947dd44868eb3e31fa49b347a02a0a0ade4d9e) | Merge | Merge PR #7741: restore jfx progressbar skin shadow | HenrikJannsen |
| 2026-05-11 | [4d57133fea](https://github.com/bisq-network/bisq/commit/4d57133fea30b4ce905bbfb42d02f90a9ac49a3d) | Commit | Reject legacy UTXOs at deposit funding to prevent mid-trade abort | wodoro |
| 2026-05-11 | [b2dcfe9185](https://github.com/bisq-network/bisq/commit/b2dcfe91855d1bbf2e88541816a3e7a1828e98fb) | Commit | Fix follow-ups from #7665 payment account hash review | wodoro |
| 2026-05-11 | [5cddcae77e](https://github.com/bisq-network/bisq/commit/5cddcae77e8dc2d213246044b942c0362b669414) | Commit | Drop asymmetric return values from mediated payout validation | wodoro |
| 2026-05-10 | [ce35dff714](https://github.com/bisq-network/bisq/commit/ce35dff714505f53117d8f48e85b1b2ebdb4ff39) | Commit | Update netlayer to 4b5be1369b818a7d6e09ba041f911388a4745937 | HenrikJannsen |
| 2026-05-11 | [5af6c77c06](https://github.com/bisq-network/bisq/commit/5af6c77c062da543c6ecbed8e58cde75ccc0bdd5) | Merge | Merge PR #7740: update to latest tor version | HenrikJannsen |
| 2026-05-11 | [fcc6cf2f1a](https://github.com/bisq-network/bisq/commit/fcc6cf2f1aa57ca7898975a55e1ce0c280f51e01) | Commit | Align Kotlin version and dependency verification metadata | KimStrand |
| 2026-05-11 | [d122fdd733](https://github.com/bisq-network/bisq/commit/d122fdd73347344114010dadea9c33e972c0cc48) | Merge | Merge PR #7746: update kotlin version | HenrikJannsen |
| 2026-05-11 | [efef40ef4d](https://github.com/bisq-network/bisq/commit/efef40ef4d5d7eca36b7656778588cf640192008) | Merge | Merge PR #7739: fee validation followups | HenrikJannsen |
| 2026-05-11 | [c7b8dda84e](https://github.com/bisq-network/bisq/commit/c7b8dda84e93f532f09339ca1b9ee3ea6b0c1c78) | Commit | Update netlayer to commit 47ea493b9eb6df2cad64499d5b5076c83a0d786c | HenrikJannsen |
| 2026-05-11 | [1e50e2656d](https://github.com/bisq-network/bisq/commit/1e50e2656d4d3c216fb50ac97985199a7f08acb7) | Merge | Merge PR #7749: Update netlayer | HenrikJannsen |
| 2026-05-11 | [77989a2256](https://github.com/bisq-network/bisq/commit/77989a2256627eb0bd28e02f2f3d3b63abe58af3) | Commit | Remove exit warning popup. | HenrikJannsen |
| 2026-05-11 | [623ce41c31](https://github.com/bisq-network/bisq/commit/623ce41c31038f2004407f9d13433e8ba1debdc5) | Merge | Merge PR #7750: remove the exit warning popup | HenrikJannsen |
| 2026-05-11 | [b3fcbcfaf0](https://github.com/bisq-network/bisq/commit/b3fcbcfaf0a44781ee05dbb52b44758eb4ea123c) | Commit | Update bitcoinj to 6c32c0629d4ac7ecc95889eb1a46fa0d77a4e15a | HenrikJannsen |
| 2026-05-11 | [245cf87fb3](https://github.com/bisq-network/bisq/commit/245cf87fb301dae0e536c6f93e594f19962df2ab) | Commit | Force public-key point decoding when validating compressed multisig keys after the bitcoinj update | HenrikJannsen |
| 2026-05-11 | [bdae8c51d4](https://github.com/bisq-network/bisq/commit/bdae8c51d4643483862a3490bfa32ab8588f71ed) | Commit | Update dependency verification metadata | HenrikJannsen |
| 2026-05-11 | [936eeb2e4f](https://github.com/bisq-network/bisq/commit/936eeb2e4f25fed634e43b5c041c037b9e7896e4) | Merge | Merge PR #7747: use updated bitcoinj | HenrikJannsen |
| 2026-05-11 | [512dbc8c4d](https://github.com/bisq-network/bisq/commit/512dbc8c4d49e89094c6f0ed0824f153e083efc2) | Merge | Merge PR #7743: 7655 followups legacy utxo gate | HenrikJannsen |
| 2026-05-11 | [9e5d431e62](https://github.com/bisq-network/bisq/commit/9e5d431e62a9af4130c76390137749d782319132) | Merge | Merge PR #7744: pr 7665 followups | HenrikJannsen |
| 2026-05-11 | [84c64c2901](https://github.com/bisq-network/bisq/commit/84c64c2901a601481126a4a4fe1dcf4673ec097c) | Merge | Merge PR #7745: mediated payout validation cleanup | HenrikJannsen |
| 2026-05-12 | [a0602f89f0](https://github.com/bisq-network/bisq/commit/a0602f89f00873043fbd101e06a0cc25d98d5c97) | Commit | Update logback/slf4j and align bitcoind with the current Java 21 branch | HenrikJannsen |
| 2026-05-12 | [5970e84f75](https://github.com/bisq-network/bisq/commit/5970e84f75a55751525c5e81b121e7f9e12271a2) | Merge | Merge PR #7751: Update logback and slf4j versions | HenrikJannsen |
| 2026-05-12 | [bbcaddf1ef](https://github.com/bisq-network/bisq/commit/bbcaddf1efadb188483f55fdb9270da5272b7a79) | Commit | Update guava and lombok | HenrikJannsen |
| 2026-05-12 | [62642fb2cd](https://github.com/bisq-network/bisq/commit/62642fb2cd7d745ba09f4f0970d239dfd922cc3f) | Merge | Merge PR #7752: Update guava and lombok | HenrikJannsen |
| 2026-05-12 | [3563f5900f](https://github.com/bisq-network/bisq/commit/3563f5900fcffd9c14cc128d46d79b2304691216) | Commit | Update checker-qual to 4.1.0 | HenrikJannsen |
| 2026-05-12 | [0dde9a9189](https://github.com/bisq-network/bisq/commit/0dde9a918968502a6a5b173f193c82fe768f74a9) | Commit | Update glassfish-jaxb to 4.0.8 | HenrikJannsen |
| 2026-05-12 | [d2a9a6fe6a](https://github.com/bisq-network/bisq/commit/d2a9a6fe6aeddc394f6f80f4757f4d3dc0b0a5a2) | Commit | Update qrgen to 1.4 | HenrikJannsen |
| 2026-05-12 | [488ea24fbd](https://github.com/bisq-network/bisq/commit/488ea24fbd149525edf018d84d11b925ab3eef28) | Merge | Merge PR #7753: update checker qual to 4.1.0 | HenrikJannsen |
| 2026-05-12 | [b5d97b5a6b](https://github.com/bisq-network/bisq/commit/b5d97b5a6b3c0cb85a79fa93a293082b55c222b6) | Merge | Merge PR #7754: Update glassfish jaxb to 4.0.8 | HenrikJannsen |
| 2026-05-12 | [f16e5d285e](https://github.com/bisq-network/bisq/commit/f16e5d285e4dce8ee041c853a9318b31f76ab8fe) | Merge | Merge PR #7755: Update qrgen to 1.4 | HenrikJannsen |
| 2026-05-12 | [6f2f2cbc1e](https://github.com/bisq-network/bisq/commit/6f2f2cbc1e4bc9e75cf16f4a28b26e64ee88e6f6) | Commit | Update jackson to 2.21.2 | HenrikJannsen |
| 2026-05-12 | [b1c6ee7e7b](https://github.com/bisq-network/bisq/commit/b1c6ee7e7b9ab587bd8d1b368660d393553f8a88) | Merge | Merge PR #7757: Update jackson to 2.21.3 | HenrikJannsen |
| 2026-05-12 | [4cfc5f1549](https://github.com/bisq-network/bisq/commit/4cfc5f1549b6238e472d51c14231985c79ae0377) | Commit | Update swagger-lib to 2.2.49 | HenrikJannsen |
| 2026-05-12 | [d9b8627757](https://github.com/bisq-network/bisq/commit/d9b86277575f3683b2c51dcaded53c17c0083643) | Merge | Merge PR #7756: Update swagger lib to 2.2.49 | HenrikJannsen |
| 2026-05-12 | [c21aca7936](https://github.com/bisq-network/bisq/commit/c21aca7936bb18d5695bd6b228a41367157fb91d) | Commit | Update hamcrest to 3.0 | HenrikJannsen |
| 2026-05-12 | [2bd9b51cdd](https://github.com/bisq-network/bisq/commit/2bd9b51cdd23fd44e2c50bc301910b62de386deb) | Merge | Merge PR #7758: Update hamcrest to 3.0 | HenrikJannsen |
| 2026-05-12 | [30b4e4a6e1](https://github.com/bisq-network/bisq/commit/30b4e4a6e13b73611d60aed4acdf8ef8be64dc38) | Commit | Update junit-jupiter to 6.0.3 | HenrikJannsen |
| 2026-05-12 | [7e18d43042](https://github.com/bisq-network/bisq/commit/7e18d4304201675f23719c9e65c162b82e67c6fc) | Commit | Update jersey-lib to 4.0.2 | HenrikJannsen |
| 2026-05-12 | [ae1f232177](https://github.com/bisq-network/bisq/commit/ae1f2321776a0574ee609c91eb13c8cfaa70254f) | Merge | Merge PR #7759: Update junit jupiter to 6.0.3 | HenrikJannsen |
| 2026-05-12 | [8a4d71f9c7](https://github.com/bisq-network/bisq/commit/8a4d71f9c7773ae4bdf7a6b107dc78d4844bcbf9) | Merge | Merge PR #7760: Update jersey lib to 4.0.2 | HenrikJannsen |
| 2026-05-12 | [75c114b1e3](https://github.com/bisq-network/bisq/commit/75c114b1e3b86bf64fbc310739d63943dfa0b439) | Commit | Update openjfx-javafx-plugin to 0.1.0 | HenrikJannsen |
| 2026-05-12 | [fac939eba6](https://github.com/bisq-network/bisq/commit/fac939eba63eab6bdc6708ecd965026b75fdf841) | Merge | Merge PR #7761: Update openjfx javafx plugin to 0.1.0 | HenrikJannsen |
| 2026-05-12 | [cf70be1d98](https://github.com/bisq-network/bisq/commit/cf70be1d98668a2ee9cb612cf60020205d4e6a6c) | Commit | Create SECURITY.md | bisqadmin |
| 2026-05-12 | [86b53a8345](https://github.com/bisq-network/bisq/commit/86b53a83455a58f1455eda2fa21b4ff6086dfec6) | Commit | Add Gradle dependency signature verification report | HenrikJannsen |
| 2026-05-12 | [77768e8fe1](https://github.com/bisq-network/bisq/commit/77768e8fe1fdc9114f803e6e3eecd277d3f3952b) | Commit | Add Gradle wrapper distribution checksum | HenrikJannsen |
| 2026-05-12 | [d594b6e5c2](https://github.com/bisq-network/bisq/commit/d594b6e5c27b6383c003c35d51bcf44e6beed746) | Commit | Add reproducible archive and jar hash checks | HenrikJannsen |
| 2026-05-12 | [46cf4a096d](https://github.com/bisq-network/bisq/commit/46cf4a096d2dabce553c5d3fba7d965811dbfc8b) | Merge | Merge PR #7762: Add Gradle dependency signature verification report | HenrikJannsen |
| 2026-05-12 | [14f02f9b5a](https://github.com/bisq-network/bisq/commit/14f02f9b5afae21b9296011725496c17b3830713) | Commit | Apply review comments to reproducible build checks | HenrikJannsen |
| 2026-05-12 | [570bd90ad5](https://github.com/bisq-network/bisq/commit/570bd90ad5e5312320ac238acf86c1859ddd0d25) | Merge | Merge PR #7763: improve reproducable build system | HenrikJannsen |
| 2026-05-12 | [3047a928ed](https://github.com/bisq-network/bisq/commit/3047a928edca958348dce5d266a932c4decda4b0) | Commit | feat: add cve scan task | wodoro |
| 2026-05-12 | [2a743ef608](https://github.com/bisq-network/bisq/commit/2a743ef6087dfb2a2b677078160413b44c489c62) | Commit | Trust Gradle source distribution signature | HenrikJannsen |
| 2026-05-12 | [03b23a1ac3](https://github.com/bisq-network/bisq/commit/03b23a1ac32ed55bf426f94fcd73aaf1df4c9c1e) | Merge | Merge PR #7766: Add Gradle source distribution signature | HenrikJannsen |
| 2026-05-12 | [7979963544](https://github.com/bisq-network/bisq/commit/79799635441afea5ef2bc2bfe5b4316e356b8038) | Commit | Harden payout address validation | KimStrand |
| 2026-05-12 | [d9ac709d12](https://github.com/bisq-network/bisq/commit/d9ac709d126c0dad3fba6434feaf64c877eea39e) | Merge | Merge PR #7765: cve | HenrikJannsen |
| 2026-05-12 | [39e6caa5c1](https://github.com/bisq-network/bisq/commit/39e6caa5c1c4684c0fb49ff5b13f79630e473b5c) | Merge | Merge PR #7764: payout address validation hardening | HenrikJannsen |
| 2026-05-12 | [2bd785211c](https://github.com/bisq-network/bisq/commit/2bd785211c35df71d087077d8b8a91a6f36e6c0d) | Commit | Fix payment account deserialization | Takahiro Nagasawa |
| 2026-05-13 | [5586944bcd](https://github.com/bisq-network/bisq/commit/5586944bcd17ea64528d8505b69a425b62f6bd95) | Commit | Revert "Update data stores for v1.9.23" | HenrikJannsen |
| 2026-05-13 | [854b153bce](https://github.com/bisq-network/bisq/commit/854b153bcece6d878d3d9964de4293b2b898a58f) | Merge | Merge PR #7776: Revert resources commit 9df2d9 | HenrikJannsen |
| 2026-05-13 | [8b37325c84](https://github.com/bisq-network/bisq/commit/8b37325c8402059a56fc952b2138eb3a3d56bae4) | Commit | Set version 1.10.0 | HenrikJannsen |
| 2026-05-13 | [8d9f2f0357](https://github.com/bisq-network/bisq/commit/8d9f2f0357cb1929afa4c3fe4063f834095f0e35) | Commit | Add test for version checks | HenrikJannsen |
| 2026-05-13 | [570259d90a](https://github.com/bisq-network/bisq/commit/570259d90a9d363fb479465b4aa5f0268ceedfb0) | Commit | Update bitcoind to commit a922416084c88bfb5fff76acec71e7a72af0bc5b | HenrikJannsen |
| 2026-05-13 | [00426cc795](https://github.com/bisq-network/bisq/commit/00426cc795c37e023c67bd4a5aadcf9f00a520c3) | Merge | Merge PR #7780: Update bitcoind to commit a92241608 | HenrikJannsen |
| 2026-05-13 | [707771cb33](https://github.com/bisq-network/bisq/commit/707771cb333a34dfc10c9e9a217aec64f52e6a77) | Merge | Merge PR #7778: set version 1.10.0 | HenrikJannsen |
| 2026-05-13 | [5bf2db0b10](https://github.com/bisq-network/bisq/commit/5bf2db0b10cec6c25b998874db1c700901befacf) | Merge | Merge PR #7779: Add test for version checks | HenrikJannsen |
| 2026-05-13 | [52c7bb7745](https://github.com/bisq-network/bisq/commit/52c7bb77457974df23c3109b3ad75f747a1839e1) | Commit | Disable dispute log file transfer | KimStrand |
| 2026-05-13 | [90387f6a02](https://github.com/bisq-network/bisq/commit/90387f6a02b1c5fe0dc0bced5c8c0b6e7698434f) | Commit | Update bitcoind to 65d7e4a435c95427f818922ba44e7a27ab5a3619 | HenrikJannsen |
| 2026-05-13 | [7f952fb1e8](https://github.com/bisq-network/bisq/commit/7f952fb1e8a4537acc6ae7fb380baa7ae03f4f87) | Merge | Merge PR #7784: Update bitcoind and verification metadata | HenrikJannsen |
| 2026-05-13 | [fe9da08f52](https://github.com/bisq-network/bisq/commit/fe9da08f52a714222dfdefe72da96f1b5d527ec5) | Commit | Pin down JFXProgressBarSkin as in osx build it is not included in binary | HenrikJannsen |
| 2026-05-13 | [76d5d46e2d](https://github.com/bisq-network/bisq/commit/76d5d46e2dfdd4a99a63659a73be0e927464b911) | Merge | Merge PR #7785: pin jfoenix class | HenrikJannsen |
| 2026-05-13 | [7d312b4be3](https://github.com/bisq-network/bisq/commit/7d312b4be336d739b842f639de6e68707368a422) | Commit | Revert "Pin down JFXProgressBarSkin as in osx build it is not included in binary" | HenrikJannsen |
| 2026-05-13 | [17346968b3](https://github.com/bisq-network/bisq/commit/17346968b30efc6c3e1765cdffa8ed12f1da0480) | Commit | Add ``--add-exports=javafx.graphics` | HenrikJannsen |
| 2026-05-13 | [4411e726e8](https://github.com/bisq-network/bisq/commit/4411e726e8c055d0aa043c93d31ca874b658a2ef) | Merge | Merge PR #7786: add jvm arg | HenrikJannsen |
| 2026-05-13 | [2970c7696d](https://github.com/bisq-network/bisq/commit/2970c7696d6d7b1a59a947cdeac0415d93773c65) | Merge | Merge PR #7783: disable dispute log file transfer | HenrikJannsen |
| 2026-05-14 | [5d974b4076](https://github.com/bisq-network/bisq/commit/5d974b4076e5982023f3f57410dcb38e8157107d) | Merge | Merge PR #7769: fix payment account deserialization | HenrikJannsen |
| 2026-05-13 | [e6d9e67171](https://github.com/bisq-network/bisq/commit/e6d9e67171e93d5d1b7b04c76fbc6e76a010a8b1) | Commit | Remove dispute log transfer and chat attachments | KimStrand |
| 2026-05-13 | [fa8bf7edd4](https://github.com/bisq-network/bisq/commit/fa8bf7edd48b76edabdf97f55f5f7bb36868ac20) | Commit | Fix chat scroll OOM and window sizing | KimStrand |
| 2026-05-14 | [dae777386d](https://github.com/bisq-network/bisq/commit/dae777386d7431e82b123e78dcb94e6ca8a79d4d) | Merge | Merge PR #7787: remove file transfer | HenrikJannsen |
| 2026-05-14 | [9cafc7c9f7](https://github.com/bisq-network/bisq/commit/9cafc7c9f7ce7b12859e84e4960fd2c9587fcde7) | Merge | Merge PR #7789: fix chat javafx21 scroll sizing | HenrikJannsen |
| 2026-05-12 | [5022ee6cc5](https://github.com/bisq-network/bisq/commit/5022ee6cc5184ff7f8eee27c673dd739298c9866) | Commit | Add release manifest generation for Java payloads | HenrikJannsen |
| 2026-05-12 | [7e28de750d](https://github.com/bisq-network/bisq/commit/7e28de750d9a34d18b9ed65ec6524e476ebef22d) | Commit | Add release manifest verification task | HenrikJannsen |
| 2026-05-12 | [6296ac5c65](https://github.com/bisq-network/bisq/commit/6296ac5c65304445e8a755b243b7950fdf681101) | Commit | Add release payload manifests and verification | HenrikJannsen |
| 2026-05-12 | [f087857dd3](https://github.com/bisq-network/bisq/commit/f087857dd3f111387861d97e3bc1364b8ea5bc30) | Commit | Publish release manifests from CI | HenrikJannsen |
| 2026-05-12 | [cac375485d](https://github.com/bisq-network/bisq/commit/cac375485d4806515ec6dbebd2b8330a2a6d5326) | Commit | Pin GitHub Actions to commit SHAs | HenrikJannsen |
| 2026-05-12 | [17e85601f2](https://github.com/bisq-network/bisq/commit/17e85601f26377ba090bc26ed0bd61bc570a0c26) | Commit | Pin CI runner images and JDK patch version | HenrikJannsen |
| 2026-05-12 | [4a50dd2a2e](https://github.com/bisq-network/bisq/commit/4a50dd2a2e26fcd85b16e3bb867ee258c7e02442) | Commit | Verify GitHub Actions workflow hardening | HenrikJannsen |
| 2026-05-12 | [eea51b7627](https://github.com/bisq-network/bisq/commit/eea51b76275560a22bc6a3d2055da73771081c74) | Commit | Gate checksum-only dependency artifacts | HenrikJannsen |
| 2026-05-12 | [2147180d73](https://github.com/bisq-network/bisq/commit/2147180d739eb7bfe5bea9f9d977f95cc0d8eb7c) | Commit | Verify Gradle wrapper inputs | HenrikJannsen |
| 2026-05-12 | [3a27659070](https://github.com/bisq-network/bisq/commit/3a27659070865f68dd9fbfd665a6b37b49a4e445) | Commit | Document reproducible release verification | HenrikJannsen |
| 2026-05-12 | [a6210067e1](https://github.com/bisq-network/bisq/commit/a6210067e164fab5ab7f42efe884aeef7fda27dd) | Commit | Add aggregate release verification task | HenrikJannsen |
| 2026-05-12 | [044b6e96ce](https://github.com/bisq-network/bisq/commit/044b6e96ce6513edebbda795d78df504fa3db623) | Commit | Verify the release build runtime | HenrikJannsen |
| 2026-05-12 | [b91a4b2177](https://github.com/bisq-network/bisq/commit/b91a4b2177a3c0088fa084cb21457e38fef5ec8d) | Commit | Accept release manifest directories | HenrikJannsen |
| 2026-05-12 | [b9a71a3b60](https://github.com/bisq-network/bisq/commit/b9a71a3b60f6cccbf5d48138bfb850d85ba272bc) | Commit | Disable persisted checkout credentials | HenrikJannsen |
| 2026-05-12 | [55b732a343](https://github.com/bisq-network/bisq/commit/55b732a3435aff46001ab0c1f150b3abaf0a9092) | Commit | Add a release evidence bundle | HenrikJannsen |
| 2026-05-12 | [a89256acf3](https://github.com/bisq-network/bisq/commit/a89256acf3586e19faa502ac88448baab09cf269) | Commit | Avoid eager startBisqApp realization | HenrikJannsen |
| 2026-05-12 | [fc7f809585](https://github.com/bisq-network/bisq/commit/fc7f809585b75076a6d6ea7be9ea86e624032db6) | Commit | Add installer manifest verification | HenrikJannsen |
| 2026-05-12 | [ba48163362](https://github.com/bisq-network/bisq/commit/ba48163362e73a485cdb52bc09e832d388482378) | Commit | Record release build tool diagnostics | HenrikJannsen |
| 2026-05-12 | [66a44d045f](https://github.com/bisq-network/bisq/commit/66a44d045f4327d20e69f69c6396c99dd5c7e3ba) | Commit | Add Linux release-builder image definition | HenrikJannsen |
| 2026-05-12 | [dd16f35c69](https://github.com/bisq-network/bisq/commit/dd16f35c69beef47058548cd1084fba5025b1aeb) | Commit | Document release manifest match policy | HenrikJannsen |
| 2026-05-12 | [8784919114](https://github.com/bisq-network/bisq/commit/8784919114db6ebc756e31fefaf485bd3905b97e) | Commit | Add installer evidence bundle task | HenrikJannsen |
| 2026-05-12 | [56ec08f460](https://github.com/bisq-network/bisq/commit/56ec08f460cf53769a1d9f98d1f83260bc8f21b1) | Commit | Require checksum fallback rationales | HenrikJannsen |
| 2026-05-12 | [6410a9f04a](https://github.com/bisq-network/bisq/commit/6410a9f04aa2f2a911dcc982414765e1c3badbbf) | Commit | Add manual Linux installer evidence workflow | HenrikJannsen |
| 2026-05-12 | [71d636feb9](https://github.com/bisq-network/bisq/commit/71d636feb954568b23d883c5c142095226e82cf2) | Commit | Add macOS installer evidence workflow job | HenrikJannsen |
| 2026-05-12 | [2589e2b6bf](https://github.com/bisq-network/bisq/commit/2589e2b6bfd4f0c6fec1d386fde0e9a1022cb56b) | Commit | Add Windows installer evidence workflow job | HenrikJannsen |
| 2026-05-12 | [0451a92719](https://github.com/bisq-network/bisq/commit/0451a927199fa94ddd723bb359924c00bff5e754) | Commit | Pin release-builder apt snapshot | HenrikJannsen |
| 2026-05-12 | [f20f521edd](https://github.com/bisq-network/bisq/commit/f20f521eddbfc4ca1462e3c587c26991ecccfa05) | Commit | Add installer build info evidence | HenrikJannsen |
| 2026-05-12 | [b81b621718](https://github.com/bisq-network/bisq/commit/b81b621718b74607ee3aaaba8efb6f3d105b6195) | Commit | Add installer structure evidence report | HenrikJannsen |
| 2026-05-12 | [afce4f16b7](https://github.com/bisq-network/bisq/commit/afce4f16b79c03bc6f29e0538396451fb1c2c727) | Commit | Accept evidence ZIPs for manifest verification | HenrikJannsen |
| 2026-05-12 | [3a3ac72dca](https://github.com/bisq-network/bisq/commit/3a3ac72dca57b4ddd90315ca6ac3ef9efa521145) | Commit | Record platform installer tool diagnostics | HenrikJannsen |
| 2026-05-12 | [c131ebd1d4](https://github.com/bisq-network/bisq/commit/c131ebd1d44426d5bed737f4b13b2a5d736cd127) | Commit | Expand installer structure diagnostics | HenrikJannsen |
| 2026-05-12 | [d0b05762b2](https://github.com/bisq-network/bisq/commit/d0b05762b2be126f98924cb4425c52528dbb050c) | Commit | Record Linux installer archive metadata | HenrikJannsen |
| 2026-05-12 | [ece47a342e](https://github.com/bisq-network/bisq/commit/ece47a342e172f49da1070ecb8170a32ec4226d2) | Commit | Record installer file type diagnostics | HenrikJannsen |
| 2026-05-12 | [4b2aedaf93](https://github.com/bisq-network/bisq/commit/4b2aedaf9347c286c82fef842bf30465f1189f6e) | Commit | Validate installer evidence bundle contents | HenrikJannsen |
| 2026-05-12 | [cb4140a36c](https://github.com/bisq-network/bisq/commit/cb4140a36cc35eaeb6a7aaf276502e1e050ef8e7) | Commit | Document installer structure report statuses | HenrikJannsen |
| 2026-05-12 | [d45af87969](https://github.com/bisq-network/bisq/commit/d45af87969019ed34de52c71ed98c5b4241d287d) | Commit | Record Debian installer tar listings | HenrikJannsen |
| 2026-05-12 | [30c959fa2b](https://github.com/bisq-network/bisq/commit/30c959fa2b4d7371436d5de59fb74aa67942c404) | Commit | Document Linux installer rebuild comparison | HenrikJannsen |
| 2026-05-12 | [28e656074b](https://github.com/bisq-network/bisq/commit/28e656074b58227e4b55dd2a3f0c245454a54d83) | Commit | Add manual Linux release-builder workflow | HenrikJannsen |
| 2026-05-12 | [6924e3b55a](https://github.com/bisq-network/bisq/commit/6924e3b55ad4c0043a6fc8eb751016d23db6c7d6) | Commit | Verify release-builder image definition | HenrikJannsen |
| 2026-05-12 | [23aae7d7f4](https://github.com/bisq-network/bisq/commit/23aae7d7f45a0ea9630a8839d2e8543d882c437c) | Commit | Record precise Debian tar metadata | HenrikJannsen |
| 2026-05-12 | [d18ad7652e](https://github.com/bisq-network/bisq/commit/d18ad7652e1fbd9c5e47e561dc17013733391365) | Commit | Record Debian archive member hashes | HenrikJannsen |
| 2026-05-12 | [47a34a0c55](https://github.com/bisq-network/bisq/commit/47a34a0c558b23c315158cea9b4418ec45b30fdb) | Commit | Record RPM package checks | HenrikJannsen |
| 2026-05-12 | [48bcc5081d](https://github.com/bisq-network/bisq/commit/48bcc5081d8b9d1bd63c6e3741e4e808b8d33a06) | Commit | Record RPM payload archive listings | HenrikJannsen |
| 2026-05-12 | [cb3d0841fd](https://github.com/bisq-network/bisq/commit/cb3d0841fdc988513818ec52e9fe369fb289e6f3) | Commit | Record rpm2cpio diagnostics | HenrikJannsen |
| 2026-05-12 | [425c1cc330](https://github.com/bisq-network/bisq/commit/425c1cc330431877d3e295b62bfa833080ab5ec1) | Commit | Record DMG verification diagnostics | HenrikJannsen |
| 2026-05-13 | [66680f4144](https://github.com/bisq-network/bisq/commit/66680f4144e66643e1bc87c0f2c0d940a6af4f47) | Commit | Record PKG archive diagnostics | HenrikJannsen |
| 2026-05-13 | [8c3c9b4bc4](https://github.com/bisq-network/bisq/commit/8c3c9b4bc4d910545d39bfa0f4a19e7f18157233) | Commit | Record Windows installer file metadata | HenrikJannsen |
| 2026-05-13 | [8840a61df4](https://github.com/bisq-network/bisq/commit/8840a61df4fe914bbd10ccb0aa7d47322b327bf2) | Commit | Record Windows MSI properties | HenrikJannsen |
| 2026-05-13 | [8a4428a85b](https://github.com/bisq-network/bisq/commit/8a4428a85b69807a7ead4df939a5b1a87674cd07) | Commit | Record Windows PE header metadata | HenrikJannsen |
| 2026-05-13 | [aac3813bf6](https://github.com/bisq-network/bisq/commit/aac3813bf621fd096eaf7818c00a12b64a9d66ba) | Commit | Record DMG checksum diagnostics | HenrikJannsen |
| 2026-05-13 | [d51d930a83](https://github.com/bisq-network/bisq/commit/d51d930a8390393a4c9b56eecdb8b1cc95ac6a77) | Commit | Document installer structure diff triage | HenrikJannsen |
| 2026-05-13 | [87e6f20ba5](https://github.com/bisq-network/bisq/commit/87e6f20ba5537b4e1988c4e87ef666df51302b97) | Commit | Record DMG partition map diagnostics | HenrikJannsen |
| 2026-05-13 | [3b45dc250e](https://github.com/bisq-network/bisq/commit/3b45dc250e228eaec1cf0c6f74bbc802317fe6d2) | Commit | Record PowerShell Core diagnostics | HenrikJannsen |
| 2026-05-13 | [55b7380722](https://github.com/bisq-network/bisq/commit/55b738072283a967db03e0a7517a04c81002c907) | Commit | Use PowerShell Core fallback for Windows evidence | HenrikJannsen |
| 2026-05-13 | [8f7fe93548](https://github.com/bisq-network/bisq/commit/8f7fe93548cbcf25f5e3858c84549ce8739d25fb) | Commit | Validate installer evidence bundles | HenrikJannsen |
| 2026-05-13 | [8f3c05f93a](https://github.com/bisq-network/bisq/commit/8f3c05f93af179c601fb6052085d4435ed6cc7ed) | Commit | Document installer evidence ZIP contents | HenrikJannsen |
| 2026-05-13 | [0e01d9f6c8](https://github.com/bisq-network/bisq/commit/0e01d9f6c8dbd4e6f750d441c879beea79d2b372) | Commit | Summarize installer structure evidence | HenrikJannsen |
| 2026-05-13 | [fc97563cb9](https://github.com/bisq-network/bisq/commit/fc97563cb9d520d38dadb78f38308d52aa902d3b) | Commit | Compare installer evidence bundles | HenrikJannsen |
| 2026-05-13 | [79740b5323](https://github.com/bisq-network/bisq/commit/79740b5323027e33399d2504f90310ead74b797e) | Commit | Record release-builder identity in build info | HenrikJannsen |
| 2026-05-13 | [18edab1396](https://github.com/bisq-network/bisq/commit/18edab1396561ea500058e6a0e3c0725a3385ef8) | Commit | Generate installer evidence in Linux release builder | HenrikJannsen |
| 2026-05-13 | [c06eb474af](https://github.com/bisq-network/bisq/commit/c06eb474af1553a60bcfe96060bd006963c55e4f) | Commit | Validate release evidence bundles | HenrikJannsen |
| 2026-05-13 | [423a2f5986](https://github.com/bisq-network/bisq/commit/423a2f5986932def92c4ecd2653d2ac2868eb6bd) | Commit | Compare release evidence bundles | HenrikJannsen |
| 2026-05-13 | [0da4f1aacc](https://github.com/bisq-network/bisq/commit/0da4f1aacc8f760003a8e716e164f7cfea1063fd) | Commit | Require sorted dependency checksum allowlist | HenrikJannsen |
| 2026-05-13 | [0d6fdae314](https://github.com/bisq-network/bisq/commit/0d6fdae31424cba54798bb16df1a461cb76251ea) | Commit | Fail on jpackage installer errors | HenrikJannsen |
| 2026-05-13 | [5c44cbc317](https://github.com/bisq-network/bisq/commit/5c44cbc3174320cc7f6cf6e7fd19166b2aa6ce4f) | Commit | Compare Linux release-builder evidence in CI | HenrikJannsen |
| 2026-05-13 | [247d6c2d86](https://github.com/bisq-network/bisq/commit/247d6c2d86ed1dc9e6f0bcd90c54ac9c1a0a3842) | Commit | Harden Linux release-builder apt snapshot setup | HenrikJannsen |
| 2026-05-13 | [bd725ebd9d](https://github.com/bisq-network/bisq/commit/bd725ebd9dc15bd902e4ed93ff4d6cd5cc21e41b) | Commit | Validate installer evidence workflow bundles | HenrikJannsen |
| 2026-05-13 | [b676d4fcb5](https://github.com/bisq-network/bisq/commit/b676d4fcb5e19592b73727d9ff854908ec4f19d7) | Commit | Require expected installer formats | HenrikJannsen |
| 2026-05-13 | [1375b88ab8](https://github.com/bisq-network/bisq/commit/1375b88ab896103b713ff2441199533aec6bbe21) | Commit | Remove stale installer artifacts before packaging | HenrikJannsen |
| 2026-05-13 | [341a47eda7](https://github.com/bisq-network/bisq/commit/341a47eda7d8a22a7d748578fce691c67469439d) | Commit | Document validated Linux installer evidence | HenrikJannsen |
| 2026-05-13 | [e1e28ab170](https://github.com/bisq-network/bisq/commit/e1e28ab170fa09fe96654cccd8f3c0d3170a4fc0) | Commit | Verify Linux release-builder workflow contract | HenrikJannsen |
| 2026-05-13 | [d7e50a9dcc](https://github.com/bisq-network/bisq/commit/d7e50a9dcc0c501b12398226a68ede3a6aa8181a) | Commit | Fix Linux release-builder apt bootstrap | HenrikJannsen |
| 2026-05-13 | [1e7adc0794](https://github.com/bisq-network/bisq/commit/1e7adc07941eaa701e09a46575cadd9286e4e238) | Commit | Mount release-builder worktrees with git metadata | HenrikJannsen |
| 2026-05-13 | [23d4a2c998](https://github.com/bisq-network/bisq/commit/23d4a2c99879c9a20875f014728f70da120f2498) | Commit | Fix Linux installer evidence bundle validation | HenrikJannsen |
| 2026-05-13 | [a92072bb1e](https://github.com/bisq-network/bisq/commit/a92072bb1ec9dbea6d34dbd3e21c563218a6c2e7) | Commit | Normalize Linux RPM installer metadata | HenrikJannsen |
| 2026-05-13 | [31a7cad15f](https://github.com/bisq-network/bisq/commit/31a7cad15f41677c40b5748757142ab18bdda8c1) | Commit | Repack Linux RPM installers deterministically | HenrikJannsen |
| 2026-05-13 | [9f3912d94d](https://github.com/bisq-network/bisq/commit/9f3912d94dbaeaf4cdb5eff924bbca7574dd64e5) | Commit | Split reproducible build documentation | HenrikJannsen |
| 2026-05-13 | [c6275917fd](https://github.com/bisq-network/bisq/commit/c6275917fdaffa5f16b3f49bdaf132e1f83afcb5) | Commit | Move reproducible build docs into directory | HenrikJannsen |
| 2026-05-14 | [0fcd8a6f4b](https://github.com/bisq-network/bisq/commit/0fcd8a6f4bd53b16d504e18889e7505dfc5a54d1) | Commit | Add hardening fix to .gitattributes | HenrikJannsen |
| 2026-05-14 | [12e19f7224](https://github.com/bisq-network/bisq/commit/12e19f7224c9c00cb68f714925a5badd48dcbc6a) | Commit | Document APT CA bootstrap | HenrikJannsen |
| 2026-05-14 | [0712c8e97c](https://github.com/bisq-network/bisq/commit/0712c8e97c277d80f07f143d9d8cb8c1e45cd8ca) | Commit | Harden APT CA bootstrap | HenrikJannsen |
| 2026-05-14 | [ad912b7990](https://github.com/bisq-network/bisq/commit/ad912b7990fec838632dc2c956851a5f083dfe8e) | Commit | Pin HTTPS Ubuntu apt snapshots | HenrikJannsen |
| 2026-05-14 | [03e5424478](https://github.com/bisq-network/bisq/commit/03e542447840064ad91ce53c1007a3fd5637de22) | Commit | Clarify Gradle stderr capture | HenrikJannsen |
| 2026-05-14 | [8076fc0cf0](https://github.com/bisq-network/bisq/commit/8076fc0cf0e59845194eaad61ed9f20fe9845b18) | Commit | Avoid fake Bisq maintainer email | HenrikJannsen |
| 2026-05-14 | [8e63415e03](https://github.com/bisq-network/bisq/commit/8e63415e03e4d79a86d2cad3efe4dd922ff662f2) | Commit | Serialize release evidence workflows | HenrikJannsen |
| 2026-05-14 | [792cb941ea](https://github.com/bisq-network/bisq/commit/792cb941eab48dfe9e8a7d305c1f9201e370137b) | Commit | Scope release-builder Docker mounts | HenrikJannsen |
| 2026-05-14 | [074e5ce231](https://github.com/bisq-network/bisq/commit/074e5ce231cac027195492b061ee6a9a8043c7b0) | Commit | Stabilize release build info | HenrikJannsen |
| 2026-05-14 | [0fb6e72c53](https://github.com/bisq-network/bisq/commit/0fb6e72c532b075fd2d667880c7fbe90204c3bb1) | Merge | Merge PR #7790: reproducible build for deb and rpm | HenrikJannsen |
| 2026-05-14 | [159d9975d2](https://github.com/bisq-network/bisq/commit/159d9975d2f89111e122326bdce4dce5788890f3) | Commit | Fix trader chat y-position listener cleanup | KimStrand |
| 2026-05-14 | [bd505b7425](https://github.com/bisq-network/bisq/commit/bd505b742599f583b67a65e0a2ec24473de364ea) | Merge | Merge PR #7792: fix trader chat y position listener cleanup | HenrikJannsen |
| 2026-05-14 | [027e890d3c](https://github.com/bisq-network/bisq/commit/027e890d3c1354d5549b2e217eafccc0cb914eec) | Commit | Deactivate remove button if no dev alert is present. | HenrikJannsen |
| 2026-05-14 | [728654ccb1](https://github.com/bisq-network/bisq/commit/728654ccb102dafb6c0da4293878885bfccbfa54) | Merge | Merge PR #7793: deactivate remove button when no dev alert is present | HenrikJannsen |
| 2026-05-14 | [62131a206e](https://github.com/bisq-network/bisq/commit/62131a206e9c0a1e510645c81ca428f1f1b7cc1e) | Commit | Update netlayer to commit 1e261ee5d453659bd10973f0b67007ffb33d0001 | HenrikJannsen |
| 2026-05-14 | [c87ad63c65](https://github.com/bisq-network/bisq/commit/c87ad63c65c508476834cba43d4bc3b8e34a3dde) | Merge | Merge PR #7794: update netlayer | HenrikJannsen |
| 2026-05-14 | [0453a38976](https://github.com/bisq-network/bisq/commit/0453a389766ae19756cfeb40037a9439034b0478) | Commit | Fix packaged macOS JavaFX JFoenix exports | HenrikJannsen |
| 2026-05-14 | [1f6ebda67a](https://github.com/bisq-network/bisq/commit/1f6ebda67a9bdc98c913626d6b9acb873a5bde2f) | Merge | Merge PR #7795: add missing jvm arg | HenrikJannsen |
| 2026-05-15 | [b12e2273df](https://github.com/bisq-network/bisq/commit/b12e2273dff72066110d3f1490672bc9cd9a48ff) | Commit | Update netlayer to commit 09c287b4573020a19f84507cb4fbbc9e78001383 | HenrikJannsen |
| 2026-05-15 | [081124c57f](https://github.com/bisq-network/bisq/commit/081124c57f87e600c2b4c865d8b31c41dab10b9c) | Merge | Merge PR #7796: update netlayer | HenrikJannsen |
| 2026-05-15 | [4690a9f5ad](https://github.com/bisq-network/bisq/commit/4690a9f5ade798322b5072f08e34d950ef9ed2f3) | Commit | # macOS Dual Architecture Support | HenrikJannsen |
| 2026-05-15 | [f245f8e46a](https://github.com/bisq-network/bisq/commit/f245f8e46a300bb9481fb75e54c3d1a91bbd7e5b) | Commit | Use deterministic macOS DMG rename paths | HenrikJannsen |
| 2026-05-15 | [d05fcd9142](https://github.com/bisq-network/bisq/commit/d05fcd914278e4e0ba0f6bd50a904acf8bbc8661) | Commit | Clarify finalized macOS hash outputs | HenrikJannsen |
| 2026-05-15 | [431134e868](https://github.com/bisq-network/bisq/commit/431134e8689124c2c3865dacc430918717161934) | Commit | Remove noisy installer test log | HenrikJannsen |
| 2026-05-15 | [544b0337b6](https://github.com/bisq-network/bisq/commit/544b0337b676edd6d06c6330c008a6f15dc8ce6d) | Commit | Reconcile dependency checksum fallback allowlist | HenrikJannsen |
| 2026-05-15 | [b9aff0e559](https://github.com/bisq-network/bisq/commit/b9aff0e559d8cb87da0f7e7577c98aeeaa9bff17) | Commit | Fix finalize release directory change | HenrikJannsen |
| 2026-05-15 | [756bdc7fe9](https://github.com/bisq-network/bisq/commit/756bdc7fe9ce05773cb736007da63dab0a5ba840) | Commit | Align macOS release build outputs | HenrikJannsen |
| 2026-05-15 | [3ad2682a8c](https://github.com/bisq-network/bisq/commit/3ad2682a8c4606a93907a5600fc46ccbd8d58f4e) | Commit | Publish native attributes for JavaFX variants | HenrikJannsen |
| 2026-05-15 | [da29be9a5e](https://github.com/bisq-network/bisq/commit/da29be9a5e0d9be62295b9385a083985302fe0b2) | Commit | Use Red Hat instead of Redhat | HenrikJannsen |
| 2026-05-15 | [4d03efe2cf](https://github.com/bisq-network/bisq/commit/4d03efe2cf62cf51c95a4b50811149f05909a41d) | Commit | Handle missing macOS updater architecture | HenrikJannsen |
| 2026-05-15 | [496aa3724b](https://github.com/bisq-network/bisq/commit/496aa3724b9590fefaaf49a98c3889a2edec08e4) | Commit | Handle missing Gradle architecture property | HenrikJannsen |
| 2026-05-15 | [3fce2c0bf5](https://github.com/bisq-network/bisq/commit/3fce2c0bf5106a053daaddff0555b213518ad2df) | Merge | Merge pull request #7797 from HenrikJannsen/macOS-Dual-Architecture-Support | HenrikJannsen |
| 2026-05-15 | [ba2ed271d1](https://github.com/bisq-network/bisq/commit/ba2ed271d14610df973dfb065e7fd0aca06e8b26) | Commit | Add 1.10.0 release notes | HenrikJannsen |
| 2026-05-15 | [96c0dc400a](https://github.com/bisq-network/bisq/commit/96c0dc400ab35efc6b7ddc4124e057cb79265ee7) | Merge | Merge pull request #7801 from HenrikJannsen/Add-1.10.0-release-notes | HenrikJannsen |
| 2026-05-15 | [a4cd0d9a2a](https://github.com/bisq-network/bisq/commit/a4cd0d9a2a3553b45a50465182b135a7ec0f9588) | Commit | Use scroll pane for update text | HenrikJannsen |
| 2026-05-15 | [2eb52a7a14](https://github.com/bisq-network/bisq/commit/2eb52a7a148ece631d949157742bae8df5ee47f1) | Commit | Document in-app update download flow | HenrikJannsen |
| 2026-05-15 | [370b48b37c](https://github.com/bisq-network/bisq/commit/370b48b37c1c1cd57a9c85d78f3b24b07df180aa) | Commit | Support dual macOS installer selection in updater | HenrikJannsen |
| 2026-05-15 | [0da22a184b](https://github.com/bisq-network/bisq/commit/0da22a184b139718a7a71bb0b3a396e3a701579f) | Commit | Add default values in User (got a NP at testing) | HenrikJannsen |
| 2026-05-15 | [eba31cf3ef](https://github.com/bisq-network/bisq/commit/eba31cf3efbe4351ca59f9057bd768af23360623) | Merge | Merge pull request #7803 from HenrikJannsen/add-support-for-dual-osx-in-app-download | HenrikJannsen |
| 2026-05-15 | [c2850685c5](https://github.com/bisq-network/bisq/commit/c2850685c5c92762d32554b441134b9547f94913) | Commit | Add GitHub release readiness check | HenrikJannsen |
| 2026-05-15 | [97b470fb60](https://github.com/bisq-network/bisq/commit/97b470fb609016d3420b00e22fa7ad17fae829e3) | Commit | The sellers trade fee is 0 in BSQ swaps. | HenrikJannsen |
| 2026-05-15 | [20a0f78019](https://github.com/bisq-network/bisq/commit/20a0f78019437bf8dbe880732d740cec82a23f71) | Merge | Merge pull request #7808 from HenrikJannsen/fix-fee-validation | HenrikJannsen |
| 2026-05-15 | [8c9aa932f3](https://github.com/bisq-network/bisq/commit/8c9aa932f3ea3a80233d0c22db328010af04d8d1) | Commit | Update release notes | HenrikJannsen |
| 2026-05-15 | [aafa01e291](https://github.com/bisq-network/bisq/commit/aafa01e291bb43f6492101c08b6c334574117f15) | Merge | Merge pull request #7809 from HenrikJannsen/update-release-notes | HenrikJannsen |
| 2026-05-15 | [df514f1603](https://github.com/bisq-network/bisq/commit/df514f1603b190cbe693264fc68b1bf909ef4208) | Commit | Fix test | HenrikJannsen |
| 2026-05-15 | [0247920168](https://github.com/bisq-network/bisq/commit/024792016800be67ee0ae37180255996bcb857d5) | Merge | Merge pull request #7811 from HenrikJannsen/fix-test | HenrikJannsen |
| 2026-05-15 | [66f04a4d77](https://github.com/bisq-network/bisq/commit/66f04a4d7756fff5d473ad19b1825a004a352b3d) | Commit | Update resources | HenrikJannsen |
| 2026-05-15 | [c2a4c8d2de](https://github.com/bisq-network/bisq/commit/c2a4c8d2de4dffd936c76dee8bb62aae78ce64a1) | Commit | Remove the tagged resources as the seed nodes run on old version and it would trigger lots of requests if the user app has the historical resource file but the seed node not. | HenrikJannsen |
| 2026-05-15 | [284fe9d14a](https://github.com/bisq-network/bisq/commit/284fe9d14ab155c6e6d94e19e664c576ae4f405d) | Merge | Merge pull request #7810 from HenrikJannsen/update-resources | HenrikJannsen |

## Credits

Contributors represented in this commit range: Alejandro García, Alva Swanson, bisqadmin, Christoph Atteneder, dependabot[bot], helixx87, HenrikJannsen, KimStrand, M. Caviar, mustardcaviar, Steven Barclay, suddenwhipvapor, Takahiro Nagasawa, tat twam asi, thecockatiel, viresinnumer1s, wodoro.
