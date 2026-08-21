# Filter and deny-list policy

Bisq has two policy sources for emergency bans and feature switches:

* The signed network `Filter`, managed by `FilterManager` and distributed over P2P.
* The static startup `DenyList`, loaded from `core/src/main/resources/denylist/<network>.denylist`.

Use `FilterPolicyService` for policy decisions in application code. It merges the static deny-list and the active signed filter where both sources apply. Direct `FilterManager` usage should be limited to filter lifecycle concerns, filter property listeners, developer filter publishing, and compatibility paths where the signed filter object itself is required.

## Signed network filter

The signed network filter is mutable operational policy. Privileged developers publish it through the filter window. It can ban offers, trading peers, network peers, currencies, payment methods, payment account data, witness signer keys, mediators, refund agents, infrastructure nodes, fee receiver addresses, minimum fees, PoW settings, API or mempool validation, BSQ swaps, auto-confirm, and DAO/trading versions.

Only one active network filter is used. `FilterManager` accepts signed filters from trusted developer keys, rejects signatures that do not verify, rejects filters too far in the future, and prefers the newest valid filter by creation date.

Some filter node settings are persisted into the local options file because they must be available before the next P2P or wallet startup:

* `bannedSeedNodes`
* `bannedBtcNodes`
* `bannedPriceRelayNodes`
* `filterProvidedSeedNodes`
* `filterProvidedBtcNodes`

Those persisted values are filter state, not user preferences. `FilterManager` clears them from the local options file when `--ignoreNetworkFilter=true` is set so stale signed-filter state cannot affect later startup. Explicitly configured `bannedSeedNodes` and `bannedBtcNodes` values that are present in the runtime config are still honored by the seed and Bitcoin node startup providers.

First-run lag with `--ignoreNetworkFilter=true`: `FilterManager.onAllServicesInitialized` rewrites the options file *during* the current startup, after `WalletsSetup` and `DefaultSeedNodeRepository` have already read `config.bannedBtcNodes` / `config.bannedSeedNodes` / `config.bannedPriceRelayNodes`. The keys are dual-purpose (used both for filter-persisted state and for operator-supplied bans), so a user enabling the flag for the first time will still see the previously persisted filter-derived bans for this run. The persisted state is cleared from the options file before the next run.

## Static deny-list

The deny-list is release-bundled policy for startup or fallback cases where waiting for a network filter is too late or not reliable. It is loaded by `DenyList` from the classpath resource matching the configured base-currency network, for example `denylist/btc_mainnet.denylist`.

The deny-list currently supports:

* trading node bans
* network peer bans
* banned currencies
* banned payment methods
* banned account witness signer pub keys
* banned mediators and refund agents
* banned seed nodes
* banned price relay nodes
* banned Bitcoin nodes
* banned auto-confirm explorers
* required trading version

Set `--ignoreDenyList=true` to skip loading the bundled resource. This is intended for development and controlled recovery scenarios.
Set `--denyListResource=<classpath-resource>` only for tests or controlled recovery when a non-network-specific resource must be loaded explicitly.

## Startup behavior

Startup policy is split by timing:

* Seed nodes are selected before a live network filter can arrive. `DefaultSeedNodeRepository` applies the static deny-list and configured seed-node bans.
* Bitcoin nodes are selected during wallet setup. `BtcNodesSetupPreferences` applies the static deny-list and configured Bitcoin-node bans.
* Price relay nodes are initialized before live filter updates. `PriceFeedNodeAddressProvider` applies the static deny-list and persisted signed-filter price-relay bans, then `FilterManager` can update runtime price relay bans after a valid network filter arrives.
* P2P network peer bans use the `BanFilter` predicate registered by `FilterManager`, which includes static deny-list network-peer bans and active signed-filter network-peer bans.

When `--ignoreNetworkFilter=true` is set, live network filters are not loaded and filter-provided seed or Bitcoin nodes are ignored. Static deny-list bans (including deny-list seed, Bitcoin, and price-relay nodes) remain in effect; use `--ignoreDenyList=true` to skip the static resource instead. Runtime `bannedSeedNodes` and `bannedBtcNodes` options still apply as local operator bans. Manual user options such as custom seed nodes or custom Bitcoin nodes are not part of signed-filter state and are not disabled by that flag.

## Payment account filters

Payment account filters use `PaymentAccountFilter` entries in this format:

```text
PAYMENT_METHOD_ID|getMethodName|value
```

Network-published payment account filters should use tagged SHA-256 values:

```text
PAYMENT_METHOD_ID|getMethodName|sha256-v1:<64 hex chars>
```

`PaymentAccountFilterMatcher` hashes runtime account payload values with a domain-separated preimage:

```text
bisq:PaymentAccountFilter:v1
PAYMENT_METHOD_ID
getMethodName
canonicalized value
```

Only the account value is canonicalized by trimming and lowercasing. The payment method id and getter name remain part of the hash domain exactly as supplied.

The filter window stores plaintext preimages only in local `UserPayload` fields so developers can edit filters later. Those preimages are not included in the signed network `Filter`; the network filter receives only hash values. Plaintext filter values remain supported by the matcher only as transitional compatibility for already-published filters.

## Dispute agent filters

Mediator and refund-agent filters are applied by their dispute-agent services before managers refresh their observable maps. On each refresh, managers rebuild the user's accepted dispute-agent lists from the filtered maps. This keeps offer payload creation and later trade validation from using agents that were removed only by policy and not by P2P storage removal.

Arbitrator filters are legacy. The `Filter.arbitrators` protobuf field is deprecated so old protobuf payloads can still be deserialized, but runtime policy ignores those values and new `Filter` serialization does not emit them.

Bisq v1 offer placement validates dispute-agent availability before funding or publishing the offer:

* the offer's accepted mediator list must include at least one currently allowed mediator;
* the local refund-agent manager must have at least one currently allowed refund agent, because refund agents are selected when the maker answers an offer availability request.

If filters remove all mediators or refund agents after an offer has already been published, the maker answers later availability requests with `NO_MEDIATORS` or `NO_REFUND_AGENTS` instead of selecting a banned agent.

## Adding or changing policy

When adding a policy category, prefer this order:

1. Add the signed-filter field if it must be updateable over P2P.
2. Add the deny-list field only if the policy must apply before a network filter can arrive or must be bundled with a release.
3. Expose the decision through `FilterPolicyService`.
4. Route consumers through `FilterPolicyService`, not directly through `FilterManager`.
5. Add tests for signed-filter behavior, deny-list behavior, and the merged behavior when both sources exist.
6. If the policy persists filter-derived node state to config, make startup readers honor `--ignoreNetworkFilter=true`.

Keep deny-list values normalized to the format expected by the consuming component. Node-address lists use `host:port`. Price relay bans use the bare onion service id without protocol, slash, or `.onion`.
