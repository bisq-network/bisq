# Bisq 1.10.5 Release Notes

Security-focused release notes for the Bisq 1.10.5 integration branch.

## Scope

This release is based on the integration branch fork point `5d93ef99d5a55eddc979b324f981ef8ab500b487` and covers the range through `0b9b0732f8437fea0957cc847327afc0ccade25d`. The `v1.10.3` tag is on a separate ancestry line, so the fork point—not a simple tag range—is the reproducible scope for this branch.

- Reachable commits in scope: 538
- Non-merge commits: 408
- Merge commits: 130
- Files changed from the fork point: 864
- Generated on: 2026-08-19

## Compatibility and Operator Notes

- The application and packaging version is `1.10.5`.
- DAO hard-fork 3 and three independently scheduled voting/proposal rules use mainnet height
  `963_350` (`3_000_000` on Bitcoin testnet and `1` on regtest and DAO test networks). The voting
  rules select by RESULT height; proposal validation selects the first RESULT block of the proposal's
  cycle.
- Bundled DAO, witness, trade-statistics, Burning Man, and checkpoint resources were refreshed and audited as part of release preparation. Operators should use the release resource-audit checks before publishing binaries.
- DAO and P2P hash/signature validation is stricter. Nodes participating in the updated DAO block, witness, and bridge flows should be upgraded together where the protocol requires it.
- Legacy arbitration registration and active legacy-arbitration handling are disabled; persisted legacy arbitrator state is no longer used for new operation.
- This is a security-hardening release. Upgrade before relying on the updated filter, bridge, witness, bonded-role, or DAO validation paths.

## Notable Changes

### Canonical Hashing and DAO Integrity

Hash and signature preimages now use explicit canonical encodings across DAO state, protected storage, offers, payment accounts, mailbox data, dispute agents, alerts, developer filters, blind votes, merit data, and related P2P payloads. Map ordering, protobuf field handling, numeric bounds, and legacy hash fallbacks are covered by parity and regression tests.

DAO block providers can sign raw blocks and lite nodes can verify those signatures. Signed and unsigned block payloads are checked for consistency, trusted providers and seeds are supported, and DAO state checkpoints and post-parse checkpoint checks were expanded. Proposal, blind-vote, trade-statistics, and other OP_RETURN or payload hashes are validated before the data is accepted.

Representative commits:

- [53094c5d56](https://github.com/bisq-network/bisq/commit/53094c5d56) - Add canonical schemas for DAO and related hash/signing paths.
- [b814f3743d](https://github.com/bisq-network/bisq/commit/b814f3743d) - Add DAO block signature verification.
- [df5c181159](https://github.com/bisq-network/bisq/commit/df5c181159) - Validate blind-vote OP_RETURN commitments.
- [790470e43a](https://github.com/bisq-network/bisq/commit/790470e43a) - Verify proposal OP_RETURN payload hashes.

### DAO Voting and Proposal Validation

Malformed decrypted vote plaintext is isolated to the voter that supplied it. A duplicate proposal
transaction ID no longer aborts the entire vote-result cycle, while an honest voter in the same cycle
continues to contribute ballots, stake, and merit. Duplicate local ballot objects are merged only
when their canonical proposals are identical; conflicting proposals remain a visible DAO-state
integrity failure.

Vote result reconstruction now preserves the exact blind-vote list committed by the on-chain
majority. Undecryptable merit data contributes zero merit without removing that voter's ballots or
stake, and equal blind-vote transaction IDs use the complete canonical payload as a deterministic
tie-break in both vote reveal and result processing.

Proposal common fields are checked during initial store projection, provisional startup entries are
revalidated after parsing, and full common and type-specific validation is enforced again at every
consensus consumer. Activation is selected for the entire proposal cycle by its first RESULT block,
so live, restarting, and resyncing nodes derive the same ballot universe.

Representative commits:

- [d5b07f628f](https://github.com/bisq-network/bisq/commit/d5b07f628f) - Isolate duplicate proposal IDs to the malformed voter.
- [c4f72d633f](https://github.com/bisq-network/bisq/commit/c4f72d633f) - Preserve majority-committed blind votes with malformed merit.
- [e4c9bcb626](https://github.com/bisq-network/bisq/commit/e4c9bcb626) - Enforce activated proposal data validation consistently.
- [0b9b0732f8](https://github.com/bisq-network/bisq/commit/0b9b0732f8) - Harden duplicate ballot reconstruction and make the historical audit reproducible.

### Witness, Reputation, and Bridge Authentication

Account-age and signed-witness bridge requests are bound to ownership proofs and qualifying trust chains. Signed witness admission checks the originating trade, owner, dates, and signature authenticity; arbitrator witness signatures and DSA-signed witness results are verified. Bridge exports avoid exposing unnecessary witness ownership data, and legacy owner swaps are handled explicitly.

Seed-only bridge data requests now require an outbound seed response, and Bisq 1 block delivery is processed contiguously. Gaps, duplicate events, and missing block ranges are recovered rather than passed downstream as complete state.

Representative commits:

- [1a29f64353](https://github.com/bisq-network/bisq/commit/1a29f64353) - Verify signed-witness ownership at the bridge.
- [a14d69bbe6](https://github.com/bisq-network/bisq/commit/a14d69bbe6) - Verify arbitrator witness signatures.
- [81ae22e7e5](https://github.com/bisq-network/bisq/commit/81ae22e7e5) - Validate DSA-signed witness results.
- [645087e247](https://github.com/bisq-network/bisq/commit/645087e247) - Make BSQ block delivery contiguous.

### Bonded Roles, Merit, and Burning Man

Bonded-role registration is tied to proposal keys, the correct proposal transaction, and the corresponding lockup lifecycle. Bond lockups can only be spent by unlock transactions, illegal collateral spends become terminal, and roles are revalidated against DAO updates. Bonded reputation and merit calculations receive cycle-wide uniqueness, height-aware arithmetic, and division-boundary protections.

Burning Man address resources and fee-receiver routing were updated, including refund-angel allow-list entries. DAO resource audits and state-hash checkpoints were refreshed through the current integration height.

Representative commits:

- [2938343f13](https://github.com/bisq-network/bisq/commit/2938343f13) - Authorize bonded roles with proposal keys.
- [62ca18eb4a](https://github.com/bisq-network/bisq/commit/62ca18eb4a) - Restrict bond lockup outputs to unlock transactions.
- [c41fa8fd28](https://github.com/bisq-network/bisq/commit/c41fa8fd28) - Count a merit issuance at most once per cycle.
- [ace90d9235](https://github.com/bisq-network/bisq/commit/ace90d9235) - Apply the current Burning Man address list to BTC fee selection.

### Trade, Dispute, and Network Validation

Fiat trade settlement now requires authoritative buyer payment-account validation and can recover validation state safely across upgrades and restarts. Deposit and delayed-payout transactions are bound to the expected trade and inputs, and mempool broadcast rejection no longer fails an already-broadcast payout path.

Support and dispute messages authenticate decrypted sender keys against the expected trade peer, trader, mediator, refund agent, or arbitrator. Invalid dispute payloads are rejected before state mutation. Malformed zero-price offers are rejected to prevent offer-book denial of service, network data hashes are checked against payloads, and mailbox signature-key validation is restricted to the relevant messages.

Representative commits:

- [e7bbfa7d9f](https://github.com/bisq-network/bisq/commit/e7bbfa7d9f) - Require buyer account validation before fiat deposit publication.
- [925e6c5e68](https://github.com/bisq-network/bisq/commit/925e6c5e68) - Harden deposit transaction and delayed-payout binding.
- [4a26f5a37e](https://github.com/bisq-network/bisq/commit/4a26f5a37e) - Harden dispute-agent message authentication.
- [a57db0307a](https://github.com/bisq-network/bisq/commit/a57db0307a) - Reject malformed zero-price offers.

### Reliability, Resource Use, and Packaging

Shutdown paths are single-entry and complete node-specific cleanup before exit. RPC requests and logs handle shutdown state more clearly, the desktop instance lock prevents concurrent runs, and heap sizing can scale with machine RAM. Trade-statistics queries release resources and discard superseded asynchronous results.

JavaFX runtime dependencies for bridge and headless applications, packaging output paths, QR generation, gRPC module separation, regtest reorganization tooling, Windows backup handling, and CI action versions were updated. Deprecated Java APIs and obsolete serialization fields were cleaned up without changing the intended wire representations.

## Tests, Documentation, and Release Data

The scope adds or updates regression coverage for canonical encodings, DAO block signatures, witness ownership and signature checks, bond and merit rules, decrypted vote isolation, majority blind-vote reconstruction, proposal consensus validation, filter and seed-node validation, trade settlement, dispute authentication, shutdown, resource handling, and cross-version or long-running end-to-end flows. Specifications were added or updated for DAO hashing, vote-result payload validation, proposal validation, merit, bonds, reputation, signed witnesses, bridge continuity, peer timestamps, and fiat payment-account validation.

Release data includes versioned 1.10.5 stores, refreshed DAO and Burning Man resources, state-hash checkpoints, and the activation updates. The bundled audit now reproduces the 947 stored blind-vote payloads, 935 successful on-chain reveals, absence of duplicate decrypted proposal IDs, and absence of merit decryption failures through height `963_120`; later data remains a release-gate audit obligation.

## First-Parent Integration Inventory

The table lists the 138 first-parent integration commits in scope. The range above remains the authoritative audit range and includes all 538 reachable commits; side-parent histories are not repeated in this compact inventory.

| Date | Commit | Type | Summary | Author |
| --- | --- | --- | --- | --- |
| 2026-05-28 | [67be76cb34](https://github.com/bisq-network/bisq/commit/67be76cb340b477f1144b7d84b9349a64246cb31) | Merge | Merge pull request #7853 from HenrikJannsen/merge-v1.10.1-branch-into-main | HenrikJannsen |
| 2026-05-28 | [3bfe2def2b](https://github.com/bisq-network/bisq/commit/3bfe2def2bad1f12862ab14d314564a0941e0624) | Merge | Merge pull request #7854 from HenrikJannsen/Fix-Mockito-static-stubbing-in-fee-tests | HenrikJannsen |
| 2026-05-28 | [0d40b55810](https://github.com/bisq-network/bisq/commit/0d40b558108c29a39242240142eda0765239f70d) | Merge | Merge pull request #7855 from HenrikJannsen/Remove-pull_request_template.md | HenrikJannsen |
| 2026-05-30 | [0aaef2b5ef](https://github.com/bisq-network/bisq/commit/0aaef2b5ef257f3b236f37f8037179f03ec49a3a) | Merge | Merge pull request #7857 from HenrikJannsen/Add-JavaFX-dependency-to-bridge-module | HenrikJannsen |
| 2026-05-30 | [06c2e5c50f](https://github.com/bisq-network/bisq/commit/06c2e5c50f35d8f9d43d8083c41570094e8c38e9) | Merge | Merge pull request #7858 from HenrikJannsen/Add-JavaFX-base-runtime-to-headless-P2P-apps | HenrikJannsen |
| 2026-05-31 | [c87e7b839b](https://github.com/bisq-network/bisq/commit/c87e7b839bda025268af41be44c139c38a6e6955) | Merge | Merge pull request #7859 from HenrikJannsen/Add-DAO-hash-JDK-comparison-tooling | HenrikJannsen |
| 2026-06-02 | [e7b5029872](https://github.com/bisq-network/bisq/commit/e7b502987206e7dd8eeae45c4e432fa7076eab27) | Merge | Merge pull request #7873 from wodoro/replace-jfoenix-with-pure-javafx | HenrikJannsen |
| 2026-06-02 | [6a43f43f63](https://github.com/bisq-network/bisq/commit/6a43f43f6383916db237f3f0ac0b62a226f5a783) | Merge | Merge pull request #7874 from wodoro/remove_fontawesome | HenrikJannsen |
| 2026-06-03 | [5ee9f34c0e](https://github.com/bisq-network/bisq/commit/5ee9f34c0e3051ba914529e1a6b79019396aad20) | Merge | Merge pull request #7881 from bisq-network/dependabot/github_actions/actions/checkout-6.0.3 | HenrikJannsen |
| 2026-06-03 | [be03aafe43](https://github.com/bisq-network/bisq/commit/be03aafe4327adecdb0579b68fc8ef2d15e42676) | Merge | Merge pull request #7861 from alvasw/fix_regtest | Alejandro García |
| 2026-06-03 | [c51c4da03d](https://github.com/bisq-network/bisq/commit/c51c4da03df8e2c5bae154d448bec2a4948765fe) | Merge | Merge pull request #7862 from alvasw/create_dao_regtest_extraction_task | Alejandro García |
| 2026-06-03 | [ba0aab180b](https://github.com/bisq-network/bisq/commit/ba0aab180b99fb57d52145f5edfef2fc5459fd6a) | Merge | Merge pull request #7863 from alvasw/regtest_Support_custom_bitcoind_ports_and_re-org_testing | Alejandro García |
| 2026-06-03 | [7f810dbe99](https://github.com/bisq-network/bisq/commit/7f810dbe99c4d328be98a65f1b33bfd51fefff8b) | Merge | Merge pull request #7864 from alvasw/regtest_Add_2nd_bitcoind_node_for_local_re-org_testing | Alejandro García |
| 2026-06-03 | [59f79d9b1e](https://github.com/bisq-network/bisq/commit/59f79d9b1e149b1b7b1d966402f4beb7722e9c4f) | Merge | Merge pull request #7871 from alvasw/build_remove_obsolete_macOS_JDK_overrides_and_runtime_image_setup | Alejandro García |
| 2026-06-03 | [2d95b8e8ec](https://github.com/bisq-network/bisq/commit/2d95b8e8eca47285dea0ddf03af3cf123e50de3d) | Merge | Merge pull request #7872 from alvasw/build_remove_broken_generateHashes_task | Alejandro García |
| 2026-06-04 | [851889ddf9](https://github.com/bisq-network/bisq/commit/851889ddf9ffff1f3fc7e556d5d02831f8874ba3) | Merge | Merge pull request #7882 from HenrikJannsen/remove-extraDatMaps-if-it-was-null | HenrikJannsen |
| 2026-06-04 | [22fa6b070a](https://github.com/bisq-network/bisq/commit/22fa6b070aa1fbde726a936552956324c66f18e6) | Merge | Merge pull request #7883 from HenrikJannsen/remove-extraDatMaps-if-it-was-empty-map | HenrikJannsen |
| 2026-06-04 | [64faea8d27](https://github.com/bisq-network/bisq/commit/64faea8d275c94ad16060c6da8c2544e5426fc8c) | Merge | Merge pull request #7884 from HenrikJannsen/use-treemap-for-extraDatMaps-if-map-had-only-1-entry | HenrikJannsen |
| 2026-06-04 | [8bff5bd49c](https://github.com/bisq-network/bisq/commit/8bff5bd49c4522d89a6b4a9e209c0f86d9ca65b1) | Merge | Merge pull request #7886 from HenrikJannsen/remove-TradeStatistics2 | HenrikJannsen |
| 2026-06-04 | [fe88e226cc](https://github.com/bisq-network/bisq/commit/fe88e226cc5f1821d49f33a6f15d6871e09bc68a) | Merge | Merge pull request #7885 from HenrikJannsen/use-custom-sorted-maps-for-extraDatMaps-if-map-have-more-then-1-entry | HenrikJannsen |
| 2026-06-04 | [1bd5a73480](https://github.com/bisq-network/bisq/commit/1bd5a7348088e745a364e9cc2cc84a3dc4be2237) | Merge | Merge pull request #7887 from wodoro/fix-jfoenix-badges | HenrikJannsen |
| 2026-06-04 | [6b81290a53](https://github.com/bisq-network/bisq/commit/6b81290a5339564f51cade37a109694ebbe5983a) | Merge | Merge pull request #7865 from alvasw/regtest-startRegtestAlice_Add_dependency_to_startMediatorTask | Alejandro García |
| 2026-06-04 | [6623428773](https://github.com/bisq-network/bisq/commit/6623428773eba492cc67c7b621ce1b8658445ee0) | Merge | Merge pull request #7866 from alvasw/regtest_Call_Gradle_tasks_in_Makefile | Alejandro García |
| 2026-06-04 | [adee6ce66d](https://github.com/bisq-network/bisq/commit/adee6ce66d11c040bacad9c3df3809747697cfca) | Merge | Merge pull request #7868 from alvasw/packaging_use_root_packaging_directory_for_build_outputs | Alejandro García |
| 2026-06-04 | [a980c2d6f4](https://github.com/bisq-network/bisq/commit/a980c2d6f4b8459674421ad226c3cd547f1a7017) | Merge | Merge pull request #7869 from alvasw/packaging_pass_--temp_argument_to_jpackage | Alejandro García |
| 2026-06-05 | [ae35e133d6](https://github.com/bisq-network/bisq/commit/ae35e133d6c2800d0a20ff8864128750f84a7069) | Merge | Merge pull request #7888 from wodoro/fix-windows-backup-locked-spvchain | HenrikJannsen |
| 2026-06-05 | [b728e36ee4](https://github.com/bisq-network/bisq/commit/b728e36ee4a62f3cea285ccc5d0cfc8614359289) | Merge | Merge pull request #7889 from HenrikJannsen/improve-filter | HenrikJannsen |
| 2026-06-07 | [01213992ee](https://github.com/bisq-network/bisq/commit/01213992ee7ebb6bf6309fb511c8f569ff958c22) | Merge | Merge pull request #7894 from HenrikJannsen/Adapt-AGENTS-instructions-for-Bisq-1 | HenrikJannsen |
| 2026-06-07 | [6292243f1b](https://github.com/bisq-network/bisq/commit/6292243f1be227a534230054dcfa4f4abec8b015) | Merge | Merge pull request #7895 from HenrikJannsen/Add-Burning-Man-Model-Technical-Spec | HenrikJannsen |
| 2026-06-07 | [f9a9127c54](https://github.com/bisq-network/bisq/commit/f9a9127c548cbe1b456b2a658fd922078c596203) | Merge | Merge pull request #7891 from wodoro/fix-jfoenix-component-styles | HenrikJannsen |
| 2026-06-07 | [1b69acdca1](https://github.com/bisq-network/bisq/commit/1b69acdca121d9b23dce5332107a5e3b20ebc825) | Merge | Merge pull request #7893 from HenrikJannsen/Add-deny-list-policy-enforcement | HenrikJannsen |
| 2026-06-07 | [66089cba5a](https://github.com/bisq-network/bisq/commit/66089cba5aa7bf6529d2ae602079160b21363616) | Merge | Merge pull request #7892 from alvasw/build_create_separate_Gradle_task_for_each_build_target | Alejandro García |
| 2026-06-08 | [187eacab1b](https://github.com/bisq-network/bisq/commit/187eacab1beaca762fbffe245514584fa2b26256) | Merge | Merge pull request #7897 from wodoro/improve_deps | HenrikJannsen |
| 2026-06-09 | [a3017e555a](https://github.com/bisq-network/bisq/commit/a3017e555a0dfc92fae6b6cc15727714a8c9e732) | Merge | Merge pull request #7903 from wodoro/lax_filter | HenrikJannsen |
| 2026-06-09 | [59b5e2add4](https://github.com/bisq-network/bisq/commit/59b5e2add4d886d504e8d33d081e103323fbb677) | Merge | Merge pull request #7906 from wodoro/grpc_check | HenrikJannsen |
| 2026-06-10 | [fbf8b0cf53](https://github.com/bisq-network/bisq/commit/fbf8b0cf53f2a251d604e34e4c4f0d237d5831a6) | Merge | Merge pull request #7905 from HenrikJannsen/Restore-weighted-BTC-fee-receiver-routing | HenrikJannsen |
| 2026-06-11 | [dd6562f80e](https://github.com/bisq-network/bisq/commit/dd6562f80e698dce12d3dd3a3bf995108c23b50c) | Merge | Merge pull request #7908 from wodoro/qr_fix | HenrikJannsen |
| 2026-06-11 | [6f3836ccea](https://github.com/bisq-network/bisq/commit/6f3836cceaae0fe52e8675b2dc77de72026b0027) | Merge | Merge pull request #7909 from HenrikJannsen/improve-shutdown-handling-in-RpcService | HenrikJannsen |
| 2026-06-11 | [a1229126fd](https://github.com/bisq-network/bisq/commit/a1229126fdaf2d1a1727c0482893d08c0d9b9e69) | Merge | Merge pull request #7911 from HenrikJannsen/improve-shutdown-handling-in-RpcService | HenrikJannsen |
| 2026-06-11 | [fa85dcc809](https://github.com/bisq-network/bisq/commit/fa85dcc809ba85c7c4b0adee1281f5779abc9328) | Merge | Merge pull request #7910 from HenrikJannsen/optimize-encoding-of-spentInfoMap | HenrikJannsen |
| 2026-06-13 | [76c10af839](https://github.com/bisq-network/bisq/commit/76c10af839c8a11c0d61d76079b0739a56a62920) | Merge | Merge pull request #7913 from wodoro/e2e-compat-cross-version-test | HenrikJannsen |
| 2026-06-17 | [1edd2ea3d2](https://github.com/bisq-network/bisq/commit/1edd2ea3d2b1761c294932ce4493045488ac4465) | Merge | Merge pull request #7914 from wodoro/e2e_long_tests | HenrikJannsen |
| 2026-06-17 | [c3f71e26e8](https://github.com/bisq-network/bisq/commit/c3f71e26e80cdd5a28d64fddca660080ea9807e3) | Merge | Merge pull request #7600 from 5andr0/master | HenrikJannsen |
| 2026-06-17 | [ef6bc0e754](https://github.com/bisq-network/bisq/commit/ef6bc0e7545f1e3b0fe8bb3dd64d4214273c0058) | Merge | Merge pull request #7918 from HenrikJannsen/improve-dispute-message-authentication | HenrikJannsen |
| 2026-06-17 | [2a3ff8b874](https://github.com/bisq-network/bisq/commit/2a3ff8b874e64ae843a5fdce071886a4d771a4d7) | Merge | Merge pull request #7919 from HenrikJannsen/add-senderSignaturePubKey-to-dispute-messages | HenrikJannsen |
| 2026-06-17 | [645143d04b](https://github.com/bisq-network/bisq/commit/645143d04b2aa0475b99d548b9599a006804c694) | Merge | Merge pull request #7920 from HenrikJannsen/add-dispute-agents-pubKeys-to-contract | HenrikJannsen |
| 2026-06-17 | [2dae90f5e9](https://github.com/bisq-network/bisq/commit/2dae90f5e9e69baf332d71711ad204e51500c9cb) | Merge | Merge pull request #7921 from HenrikJannsen/remove-legacy-arbitration-code | HenrikJannsen |
| 2026-06-19 | [4031ece2d3](https://github.com/bisq-network/bisq/commit/4031ece2d32fcbecea84fbbe9c886eeede9a78dc) | Merge | Merge pull request #7927 from HenrikJannsen/cherry-pick-v1.10.2-commits | HenrikJannsen |
| 2026-06-20 | [ac8d9ab2dd](https://github.com/bisq-network/bisq/commit/ac8d9ab2ddd33d066a4e465042c47d349e98f175) | Merge | Merge pull request #7928 from HenrikJannsen/Reject-networkdata-if-provided-hash-not-matches-payload-hash | HenrikJannsen |
| 2026-06-21 | [15b60a1dd3](https://github.com/bisq-network/bisq/commit/15b60a1dd3f359f0740c654d2511f3db04a786f1) | Merge | Merge pull request #7924 from wodoro/instance_lock | HenrikJannsen |
| 2026-06-21 | [7aa090c732](https://github.com/bisq-network/bisq/commit/7aa090c7322f5e07e2bfbf80bcb91c8a0ff32a1e) | Merge | Merge pull request #7929 from wodoro/fix/trade-complete-dialog-unclickable | HenrikJannsen |
| 2026-06-24 | [f9dab862f7](https://github.com/bisq-network/bisq/commit/f9dab862f79db573b2264fcccac3565d109dfabc) | Merge | Merge pull request #7931 from bisq-network/dependabot/github_actions/actions/checkout-7.0.0 | HenrikJannsen |
| 2026-06-24 | [8efea0a403](https://github.com/bisq-network/bisq/commit/8efea0a403b283a43bd43aaa3e54a8c143a6539f) | Merge | Merge pull request #7932 from bisq-network/dependabot/github_actions/actions/setup-java-5.3.0 | HenrikJannsen |
| 2026-06-25 | [b9fd8d8c76](https://github.com/bisq-network/bisq/commit/b9fd8d8c767e37ba4022f032e53f82573c610032) | Merge | Merge pull request #7934 from wodoro/docs/build-instructions-update-7571 | HenrikJannsen |
| 2026-07-04 | [1502dc0fe5](https://github.com/bisq-network/bisq/commit/1502dc0fe528c9a8236f05ff84ebc47d74039e3d) | Merge | Merge pull request #7939 from wodoro/fix/mediation-dispute-validation | HenrikJannsen |
| 2026-07-07 | [8547496185](https://github.com/bisq-network/bisq/commit/8547496185d706652501b6ac016b8f1137e00f27) | Merge | Merge pull request #7948 from HenrikJannsen/cherrypicked-1.10.3-commits | HenrikJannsen |
| 2026-07-07 | [884c77aa74](https://github.com/bisq-network/bisq/commit/884c77aa749129705bacbb88f6095d6ec41a4ae7) | Merge | Merge pull request #7949 from HenrikJannsen/Add-option-for-removing-all-alerts | HenrikJannsen |
| 2026-07-08 | [9034025521](https://github.com/bisq-network/bisq/commit/90340255213c696c0813cae4fdc16beb51a4947d) | Merge | Merge pull request #7923 from HenrikJannsen/remove-arbitrator-data-from-various-domain-objects | HenrikJannsen |
| 2026-07-08 | [eef0d1c2e5](https://github.com/bisq-network/bisq/commit/eef0d1c2e5b07789ca786be11fbafb5002381eb0) | Merge | Merge pull request #7937 from bisq-network/dependabot/github_actions/actions/setup-java-5.4.0 | HenrikJannsen |
| 2026-07-08 | [9507e03ad8](https://github.com/bisq-network/bisq/commit/9507e03ad8a7ce27cafd57ed11b372af6476525d) | Merge | Merge pull request #7946 from bisq-network/dependabot/github_actions/docker/setup-buildx-action-4.2.0 | HenrikJannsen |
| 2026-07-08 | [2972d41b68](https://github.com/bisq-network/bisq/commit/2972d41b688153960eee01c319cb460d5c314d4f) | Merge | Merge pull request #7938 from bisq-network/dependabot/github_actions/actions/cache-6.1.0 | HenrikJannsen |
| 2026-07-08 | [493015fb2b](https://github.com/bisq-network/bisq/commit/493015fb2b79a7e8f1c10473ef4f15ca703578b9) | Merge | Merge pull request #7952 from HenrikJannsen/Improve-DisputeManager | HenrikJannsen |
| 2026-07-08 | [c278368215](https://github.com/bisq-network/bisq/commit/c2783682155a6a7c2bcf58646cfe2955fed9727b) | Merge | Merge pull request #7951 from fenlark/resend-payment-started-msg-at-startup | HenrikJannsen |
| 2026-07-08 | [93cc44ff3d](https://github.com/bisq-network/bisq/commit/93cc44ff3d83f59e8cd4a173bcc08ce9075cc30a) | Merge | Merge pull request #7953 from KimStrand/fix-installer-evidence-path | HenrikJannsen |
| 2026-07-09 | [8ff0f9f43e](https://github.com/bisq-network/bisq/commit/8ff0f9f43ec7970dd96b11789373cc980bed8785) | Merge | Merge pull request #5 from bisq-network/harden-seed-node-requests | HenrikJannsen |
| 2026-07-09 | [a2bb59e5ac](https://github.com/bisq-network/bisq/commit/a2bb59e5ac9bb591aabf879df6020c638be211ad) | Merge | Merge pull request #7 from bisq-network/dsa-signed-witness-validation | HenrikJannsen |
| 2026-07-09 | [dab27fe717](https://github.com/bisq-network/bisq/commit/dab27fe717ed619098587d3f6665f6a7d3fb40ff) | Merge | Merge pull request #6 from bisq-network/verify-arbitrator-witness-signatures | HenrikJannsen |
| 2026-07-09 | [00a374e852](https://github.com/bisq-network/bisq/commit/00a374e85236cd2aaac249e839bc04c65753852a) | Merge | Merge pull request #10 from bisq-network/add-new-bm-addresses-json-file | HenrikJannsen |
| 2026-07-10 | [44809f95dd](https://github.com/bisq-network/bisq/commit/44809f95dd69a42baf02a0916f5e8c1527ffd001) | Merge | Merge pull request #13 from bisq-network/Revert-hash-check-in-TradeStatistics3 | HenrikJannsen |
| 2026-07-10 | [8baa2ba9cd](https://github.com/bisq-network/bisq/commit/8baa2ba9cd587ea8301b985f0046746d5573a4e4) | Merge | Merge pull request #8 from bisq-network/fix_offerbook_dos | HenrikJannsen |
| 2026-07-10 | [790470e43a](https://github.com/bisq-network/bisq/commit/790470e43a0f6edaa5a5f17bc89decf5d2975855) | Merge | Merge pull request #9 from bisq-network/verify-proposal-opReturn-data | HenrikJannsen |
| 2026-07-10 | [e686af32b5](https://github.com/bisq-network/bisq/commit/e686af32b5faa90782d6865d91433a9128aca4c7) | Merge | Merge pull request #11 from bisq-network/Apply-BM-address-list-to-BTC-fee-receiver-selection | HenrikJannsen |
| 2026-07-10 | [43875b4400](https://github.com/bisq-network/bisq/commit/43875b4400b565723a9f1ba0d23b25d5928c9912) | Merge | Merge pull request #12 from bisq-network/Validate-blind-vote-OP_RETURN-commitments | HenrikJannsen |
| 2026-07-10 | [d52dff5e45](https://github.com/bisq-network/bisq/commit/d52dff5e45b7a976634a03d41dad71b1e78e2da0) | Merge | Merge pull request #16 from bisq-network/Fix-test | HenrikJannsen |
| 2026-07-15 | [6f11250d3e](https://github.com/bisq-network/bisq/commit/6f11250d3e5e54115750993df2ac8d39ad983ce1) | Merge | Merge pull request #18 from bisq-network/sign-bsq-blocks-for-lite-nodes | HenrikJannsen |
| 2026-07-15 | [a0de5bda43](https://github.com/bisq-network/bisq/commit/a0de5bda430ca1d60d3dda7e4a7c33f1759d3242) | Merge | Merge pull request #20 from bisq-network/Remove-Activation-Date-For-Legacy-Contract | HenrikJannsen |
| 2026-07-16 | [b94383d50b](https://github.com/bisq-network/bisq/commit/b94383d50b35fccb4e254241da504ada4ab3bb12) | Merge | Merge pull request #24 from bisq-network/add-refundangel-addresses-to-whitelist | HenrikJannsen |
| 2026-07-16 | [0e4d3509d5](https://github.com/bisq-network/bisq/commit/0e4d3509d5d38b30d0e9a152fec9ecb70b23a418) | Merge | Merge pull request #26 from bisq-network/Remove-test-for-sorted-address-list | HenrikJannsen |
| 2026-07-16 | [ee6e1b46c6](https://github.com/bisq-network/bisq/commit/ee6e1b46c69b6f21b6cdacd998274ee480197799) | Merge | Merge pull request #25 from bisq-network/sort-burning-man-address-list-v3 | HenrikJannsen |
| 2026-07-18 | [2a208ddb1d](https://github.com/bisq-network/bisq/commit/2a208ddb1d5906cdc7b056e1ebf1a25e880fd08d) | Merge | Merge pull request #27 from bisq-network/improve-logs | HenrikJannsen |
| 2026-07-18 | [5e1c788a39](https://github.com/bisq-network/bisq/commit/5e1c788a39b63401deacd70b310d0f10100432ca) | Merge | Merge pull request #23 from bisq-network/preserve-vote-reveal-stake | HenrikJannsen |
| 2026-07-18 | [094a2abd47](https://github.com/bisq-network/bisq/commit/094a2abd47acc72ab3493851e481ea46bb1afd4c) | Merge | Merge pull request #28 from bisq-network/Add-test-for-DAO-block-signature-from-second-trusted-node | HenrikJannsen |
| 2026-07-18 | [7964b05d08](https://github.com/bisq-network/bisq/commit/7964b05d0802f4e80fb2498e8fdb843deac0d32a) | Merge | Merge pull request #29 from bisq-network/enable-trusted-seeds | HenrikJannsen |
| 2026-08-04 | [2610b257f1](https://github.com/bisq-network/bisq/commit/2610b257f194d3df67b1c79ec113043c83289292) | Commit | Based on master at commit 6feef9d2f30da86a7d86b19658bcf6f3f93c180a | HenrikJannsen |
| 2026-08-04 | [543762aa60](https://github.com/bisq-network/bisq/commit/543762aa60f2ea48baf3437e52e76b0f234726bf) | Merge | Merge pull request #38 from bisq-network/Replace_deprecated_Charsets.UTF_8_with_StandardCharsets.UTF_8 | Alva Swanson |
| 2026-08-04 | [4199e8b624](https://github.com/bisq-network/bisq/commit/4199e8b6246e2bd5850363249ffc2f839c905da0) | Merge | Merge pull request #39 from bisq-network/Suppress_deprecation_warnings_for_ArbitrationManager | Alva Swanson |
| 2026-08-04 | [071c542c2d](https://github.com/bisq-network/bisq/commit/071c542c2d5f97649707787e7b39d0f58afc3fc8) | Merge | Merge pull request #40 from bisq-network/Suppress_legacy_DaoState_serialization_warnings | Alva Swanson |
| 2026-08-04 | [d75e39af93](https://github.com/bisq-network/bisq/commit/d75e39af935476f9b6e42882c41803531596e5de) | Merge | Merge pull request #41 from bisq-network/Suppress_OfferPayload.mediatorNodeAddresses_warnings | Alva Swanson |
| 2026-08-04 | [d819e3febd](https://github.com/bisq-network/bisq/commit/d819e3febd08f667cd7d2db29d818d26287aed2c) | Merge | Merge pull request #42 from bisq-network/common_Replace_Locale_constructor_call_with_Locale.of | Alva Swanson |
| 2026-08-05 | [e5a76fb218](https://github.com/bisq-network/bisq/commit/e5a76fb218e2c0d28b12d228c65be3038ca39be1) | Merge | Merge pull request #44 from bisq-network/use-linux-instead-of-linux-aarch64-for-JavaFX-artifact | HenrikJannsen |
| 2026-08-05 | [80460a2499](https://github.com/bisq-network/bisq/commit/80460a2499c4901b0b2c1b67031368338c3e784a) | Merge | Merge pull request #45 from bisq-network/check-daostatehash-checkpoints-after-block-parsing | HenrikJannsen |
| 2026-08-05 | [de2568b50e](https://github.com/bisq-network/bisq/commit/de2568b50e7a4a451bfada85040eabc90bc1c526) | Merge | Merge pull request #46 from bisq-network/core_Replace_RegExUtils.replacePattern_with_CharSequence_version | Alva Swanson |
| 2026-08-05 | [ba9d85de28](https://github.com/bisq-network/bisq/commit/ba9d85de28aba0a8344b602af43cedae082a4eaa) | Merge | Merge pull request #47 from bisq-network/core_Replace_Locale_constructor_call_with_Locale.of | Alva Swanson |
| 2026-08-05 | [24cce5d40e](https://github.com/bisq-network/bisq/commit/24cce5d40e573d31fcbf63b2469a99b754d73889) | Merge | Merge pull request #48 from bisq-network/core_Replace_deprecated_Charsets.UTF_8_with_StandardCharsets.UTF_8 | Alva Swanson |
| 2026-08-05 | [6ff5b80b39](https://github.com/bisq-network/bisq/commit/6ff5b80b391494c5474251b97a2695c409095ff4) | Merge | Merge pull request #49 from bisq-network/core_Suppress_deprecation_warnings_for_ArbitrationManager | Alva Swanson |
| 2026-08-05 | [e1716483ae](https://github.com/bisq-network/bisq/commit/e1716483ae322e739179681a25bf546437ddf986) | Merge | Merge pull request #50 from bisq-network/core_Suppress_getMediatorNodeAddresses_deprecation_warnings | Alva Swanson |
| 2026-08-06 | [980f477db3](https://github.com/bisq-network/bisq/commit/980f477db37632d24861a94ae0c2fcb9310ab2c6) | Merge | Merge pull request #51 from bisq-network/Suppress_deprecation_warnings_for_ChaseQuickPayAccountPayload | Alva Swanson |
| 2026-08-06 | [317223eb44](https://github.com/bisq-network/bisq/commit/317223eb448669264417d45ce1c16f8679726be3) | Merge | Merge pull request #52 from bisq-network/Suppress_deprecation_warnings_for_OKPayAccountPayload | Alva Swanson |
| 2026-08-06 | [14b151e7fe](https://github.com/bisq-network/bisq/commit/14b151e7fe557b42ed9ae67174205c9734f1c95a) | Merge | Merge pull request #53 from bisq-network/Suppress_deprecation_warnings_for_CashAppAccountPayload | Alva Swanson |
| 2026-08-06 | [39fc0fa61c](https://github.com/bisq-network/bisq/commit/39fc0fa61c3f717ab11b4c18eec05ef9ef0b597d) | Merge | Merge pull request #54 from bisq-network/Suppress_deprecation_warnings_for_VenmoAccountPayload | Alva Swanson |
| 2026-08-06 | [c8c45a91ef](https://github.com/bisq-network/bisq/commit/c8c45a91ef793e8f997378d093687c9a8fcdb4cb) | Merge | Merge pull request #55 from bisq-network/Suppress_deprecation_warnings_for_VerseAccountPayload | Alva Swanson |
| 2026-08-06 | [b47d114f66](https://github.com/bisq-network/bisq/commit/b47d114f66c8075dab8fa52923880067482c318c) | Merge | Merge pull request #56 from bisq-network/Suppress_deprecation_warnings_in_PaymentAccountPayloadCanonicalSchemas | Alva Swanson |
| 2026-08-07 | [1950be062c](https://github.com/bisq-network/bisq/commit/1950be062c724231edd664af9113ccbb58dbdcc5) | Merge | Merge pull request #59 from bisq-network/Add-dao-state-hash-check-points | HenrikJannsen |
| 2026-08-07 | [47f5bf02b3](https://github.com/bisq-network/bisq/commit/47f5bf02b3abc5b1a3725cd5aebce6dbdcecdc2f) | Merge | Merge pull request #58 from bisq-network/Avoid-excessive-logs | HenrikJannsen |
| 2026-08-08 | [62cb6fcfc7](https://github.com/bisq-network/bisq/commit/62cb6fcfc7b52d9d013d14239682cd6d743310f5) | Merge | Merge pull request #60 from bisq-network/set-version-1.10.5 | HenrikJannsen |
| 2026-08-08 | [ea3d11a7e0](https://github.com/bisq-network/bisq/commit/ea3d11a7e08458f615b9fbcc78dfa096dace12b2) | Merge | Merge pull request #61 from bisq-network/update-agents-files | HenrikJannsen |
| 2026-08-11 | [c7f3a9b5a4](https://github.com/bisq-network/bisq/commit/c7f3a9b5a43cbc4427819ac5955e1e0b012fe830) | Merge | Merge pull request #63 from bisq-network/Let-the-max-heap-scale-with-the-machine-RAM | HenrikJannsen |
| 2026-08-12 | [ff0e32ee2a](https://github.com/bisq-network/bisq/commit/ff0e32ee2a3edf20647dc5f1d30ea11ecfad92bf) | Merge | Merge pull request #65 from bisq-network/handle-mempool-broadcast-rejection | HenrikJannsen |
| 2026-08-12 | [b016bc72e5](https://github.com/bisq-network/bisq/commit/b016bc72e57407c1e22f02a388a85952a86eea73) | Merge | Merge pull request #62 from bisq-network/Only-allow-a-bond-lockup-output-to-be-spent-by-an-unlock-tx | HenrikJannsen |
| 2026-08-13 | [dfcd1452f7](https://github.com/bisq-network/bisq/commit/dfcd1452f72b8ef7949d67662f01e1dc2809d238) | Merge | Merge pull request #57 from bisq-network/use-bond-proposal-tx-instead-of-lockup-tx-for-bond-registration-binding-(report14) | HenrikJannsen |
| 2026-08-14 | [26bb2a6c5e](https://github.com/bisq-network/bisq/commit/26bb2a6c5e3196c3bdb3e3be043f0482b21381df) | Merge | Merge pull request #70 from bisq-network/fixes-cycle-wide-merit-multiplication | HenrikJannsen |
| 2026-08-14 | [db7e7e5306](https://github.com/bisq-network/bisq/commit/db7e7e5306992c26decd04e0af1611266237e93e) | Merge | Merge pull request #72 from bisq-network/fix-dead-lock-at-shutdown | HenrikJannsen |
| 2026-08-14 | [06b859e7bd](https://github.com/bisq-network/bisq/commit/06b859e7bdd2828b0ca69f0216afeaf8efd2d729) | Merge | Merge pull request #71 from bisq-network/Enforce-upgrade-safe-buyer-payment-account-validation-across-fiat-trade-settlement-(report9) | HenrikJannsen |
| 2026-08-14 | [413467d18b](https://github.com/bisq-network/bisq/commit/413467d18bd884dcda554d3ce8679b6130a367d0) | Merge | Merge pull request #73 from bisq-network/make-date-checks-overflow-safe | HenrikJannsen |
| 2026-08-14 | [daab0d9334](https://github.com/bisq-network/bisq/commit/daab0d9334736fd9f3ea462552bc3254c3e92da4) | Merge | Merge pull request #66 from bisq-network/fix-missing-validations-for-account-age-and-witness-(report2) | HenrikJannsen |
| 2026-08-14 | [9cd7b34bba](https://github.com/bisq-network/bisq/commit/9cd7b34bba5f4419a064190fc5873cd43170de29) | Merge | Merge pull request #74 from bisq-network/release-trade-statistics-table-on-deactivate | HenrikJannsen |
| 2026-08-14 | [bd4707d9c9](https://github.com/bisq-network/bisq/commit/bd4707d9c9cb8570dbddc0650b7fde18256c9734) | Merge | Merge pull request #75 from bisq-network/Discard-superseded-trade-statistics-async-results | HenrikJannsen |
| 2026-08-14 | [8883a5c0fe](https://github.com/bisq-network/bisq/commit/8883a5c0fe9dcaa432d3e0131c73d7ec0e20e40f) | Merge | Merge pull request #76 from bisq-network/Avoid-materializing-a-Price-per-trade-statistic | HenrikJannsen |
| 2026-08-14 | [cee419fccc](https://github.com/bisq-network/bisq/commit/cee419fccc0c4ac768e336135ce3f5fef5922e87) | Merge | Merge pull request #77 from bisq-network/Accept-a-signed-witness-from-a-trade-only-if-it-matches-that-trade | HenrikJannsen |
| 2026-08-14 | [d6b3f0e205](https://github.com/bisq-network/bisq/commit/d6b3f0e205ba66f7e1e9d4917cf3a3206a2178f9) | Merge | Merge pull request #78 from bisq-network/Check-isPubKeyValid-only-for-own-mailbox-messages | HenrikJannsen |
| 2026-08-15 | [4d67a3701a](https://github.com/bisq-network/bisq/commit/4d67a3701a9a455d29ea33744c412f029275735d) | Merge | Merge pull request #84 from bisq-network/Run-node-specific-shutdown-once-and-keep-the-requested-exit-status | HenrikJannsen |
| 2026-08-15 | [6f884ef564](https://github.com/bisq-network/bisq/commit/6f884ef5647bbc9ec5b2debbf999bdc7575ec259) | Merge | Merge pull request #82 from bisq-network/Validate-peer-timestamps-with-overflow-safe-bounds | HenrikJannsen |
| 2026-08-15 | [0881420733](https://github.com/bisq-network/bisq/commit/08814207332306073fd41ce86b3e46797e576697) | Merge | Merge pull request #83 from bisq-network/Add-release-check-list | HenrikJannsen |
| 2026-08-15 | [50ca817eb0](https://github.com/bisq-network/bisq/commit/50ca817eb0e68ac45ad8600c5bb5011d75433fdc) | Merge | Merge pull request #85 from bisq-network/update-resource-files | HenrikJannsen |
| 2026-08-15 | [4fbdb6f93b](https://github.com/bisq-network/bisq/commit/4fbdb6f93b34df801badf9e95d702a9396789ac9) | Merge | Merge pull request #86 from bisq-network/update-HF-dates-and-update-audit | HenrikJannsen |
| 2026-08-15 | [5b2e1c4df9](https://github.com/bisq-network/bisq/commit/5b2e1c4df937e95a5b9c325bec669e0c90667030) | Merge | Merge pull request #87 from bisq-network/fix-test-and-improve-docs | HenrikJannsen |
| 2026-08-19 | [1322669cf8](https://github.com/bisq-network/bisq/commit/1322669cf8d85a875aca4e3da5fee37b59040b67) | Merge | Merge pull request #88 from bisq-network/hardening-witness-bridge-lockup-and-dao-audit | HenrikJannsen |
| 2026-08-19 | [8667ba36ca](https://github.com/bisq-network/bisq/commit/8667ba36ca3b67f5f1bd0413fa28b0f41267c7a7) | Merge | Merge pull request #89 from bisq-network/update-bm_address-whitelist | HenrikJannsen |
| 2026-08-19 | [92f9dd1d80](https://github.com/bisq-network/bisq/commit/92f9dd1d80b85de93ff6cd7d012c494c93438aa7) | Merge | Merge pull request #90 from bisq-network/Update-DAO-state-hash-checkpoints | HenrikJannsen |
| 2026-08-19 | [12816a1823](https://github.com/bisq-network/bisq/commit/12816a1823a928d03461937594995c981f6db9ff) | Merge | Merge pull request #91 from bisq-network/Update-resource-files | HenrikJannsen |
| 2026-08-19 | [6cc35ae02d](https://github.com/bisq-network/bisq/commit/6cc35ae02d75e12298f90aa6ff2c271356d277a1) | Merge | Merge pull request #92 from bisq-network/Update-ACTIVATE_HARD_FORK_3_HEIGHT_MAINNET-to-963_350 | HenrikJannsen |
| 2026-08-19 | [9942e5b056](https://github.com/bisq-network/bisq/commit/9942e5b0569e6c73bccca2680099b73be99f2db0) | Commit | Audit bundled DAO history through height 963120 | HenrikJannsen |
| 2026-08-19 | [2f5ce3e866](https://github.com/bisq-network/bisq/commit/2f5ce3e86642f871a7dba1decfe6e0bd1c7a4c25) | Commit | Add Bisq 1.10.5 release notes | HenrikJannsen |
| 2026-08-19 | [65403427e8](https://github.com/bisq-network/bisq/commit/65403427e8ee798ece95976266ffafd4007b29a6) | Commit | Allow unsorted Burning Man address lists | HenrikJannsen |
| 2026-08-19 | [d5b07f628f](https://github.com/bisq-network/bisq/commit/d5b07f628f1180107ae1cccb0c137d8281f74a28) | Commit | Isolate duplicate proposal IDs to the malformed voter | HenrikJannsen |
| 2026-08-19 | [c4f72d633f](https://github.com/bisq-network/bisq/commit/c4f72d633fd54c8d6bd50002ae6c323959a6dff7) | Commit | Preserve majority-committed blind votes with malformed merit | HenrikJannsen |
| 2026-08-19 | [e4c9bcb626](https://github.com/bisq-network/bisq/commit/e4c9bcb6267c671dbd09827ff3bb0fa834f2774a) | Commit | Enforce activated proposal data validation consistently | HenrikJannsen |
| 2026-08-19 | [0b9b0732f8](https://github.com/bisq-network/bisq/commit/0b9b0732f8437fea0957cc847327afc0ccade25d) | Commit | Harden duplicate ballot reconstruction and audits | HenrikJannsen |

## Release Gate

Before publication, run the project release checklist, resource audits, focused security tests, full supported-platform builds, and signature verification. Record explicit approval for every independently scheduled DAO rule and preserve the synced-node audit of proposal and vote data after bundled height `963_120` through activation. This note was generated from repository history and does not itself certify those release checks.
