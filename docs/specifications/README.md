# Domain specifications

This directory is the canonical location for specifications of intended domain behaviour.

A specification describes **what** the system must do and **why**. It is authoritative for intended
behaviour and takes precedence over behaviour inferred from the current implementation or from tests.
Implementation-oriented documentation ("how a particular part works") belongs in package-local
`*.md` files or in `docs/`, and should reference the specification here rather than duplicating it.

Where a rule is currently unspecified, disputed, or known to be violated by the implementation, the
specification says so explicitly instead of describing the implementation as if it were intended.

## Index

### `application/`

| Specification | Covers |
|---|---|
| [`application/alert-admission.md`](application/alert-admission.md) | Which alert messages a node accepts and how they are signed, including the version format of update alerts. |
| [`application/graceful-shutdown.md`](application/graceful-shutdown.md) | Graceful application shutdown, persistence completion, controlled process exit, and the JVM shutdown-hook backstop. |
| [`application/data-directory-backup.md`](application/data-directory-backup.md) | What "Backup now" copies from the data directory, how it treats links and special files, and who can read the backup. |
| [`application/data-directory-access.md`](application/data-directory-access.md) | Who can read the data directory: the owner-only mode that the node sets at every start. |
| [`application/key-ring-storage.md`](application/key-ring-storage.md) | Who can read the private keys of the key ring and their rolling backups. |

### `api/`

| Specification | Covers |
|---|---|
| [`api/market-price.md`](api/market-price.md) | How the API answers a market price request from the prices the node holds, without waiting for a price provider, and when a price is unavailable. |
| [`api/daemon-api-access.md`](api/daemon-api-access.md) | Who can reach the gRPC API of the daemon: the required password, the loopback default address, and the known gaps. |


### `account/`

| Specification | Covers |
|---|---|
| [`account/account-age-reputation.md`](account/account-age-reputation.md) | Ownership proofs and bridge verification for importing a Bisq 1 account-age witness into Bisq 2 reputation. |
| [`account/signed-witness-reputation.md`](account/signed-witness-reputation.md) | Ownership proofs and bridge verification for exporting Bisq 1 signed account-age witnesses as Bisq 2 signed-witness reputation. |
| [`account/signed-witness-admission.md`](account/signed-witness-admission.md) | Admission rules and date validation for signed witnesses entering the local store, including the witness received from the trading peer. |
| [`account/banned-accounts.md`](account/banned-accounts.md) | When an account shows as banned: decided by the key of its owner, never by the owner key of a signed witness. |

### `dao/`

| Specification | Covers |
|---|---|
| [`dao/bonds.md`](dao/bonds.md) | The BSQ bond primitive: lockup and unlock transactions, the bond state machine, slashing/confiscation, and the invariants every bond consumer may rely on. |
| [`dao/bond-lockup-spend.md`](dao/bond-lockup-spend.md) | The consensus rule for spending bond lockup outputs, including hard-fork activation, invalid-spend burning, and parsed block-data integrity. |
| [`dao/bonded-roles.md`](dao/bonded-roles.md) | Role proposals, independent role-lockup lifecycles, and proposal-key registrations bound to exact collateral for Bisq 2. |
| [`dao/bonded-reputation.md`](dao/bonded-reputation.md) | Reputation lockups, how they are collected, and what the Bisq 1 → Bisq 2 bridge exports. |
| [`dao/proposal-validation.md`](dao/proposal-validation.md) | Append-only proposal admission, activated consensus data-field validation, and startup/live consistency. |
| [`dao/merit.md`](dao/merit.md) | Merit as vote weight: what a merit claim must prove against DAO state, decay, uniqueness per cycle, and the activation boundary. |
| [`dao/vote-result-validation.md`](dao/vote-result-validation.md) | Validation and fault-isolation rules for untrusted data decrypted during DAO vote-result calculation, and which proposals a result records. |
| [`dao/dao-state-checkpoints.md`](dao/dao-state-checkpoints.md) | Bundled DAO state hashes, verification boundaries, mismatch recovery, and checkpoint generation. |
| [`dao/seed-node-state-hashes.md`](dao/seed-node-state-hashes.md) | Which connections the node trusts as seed nodes when DAO, proposal and blind vote state hashes are exchanged, and how many hashes a response holds. |
| [`dao/lite-node-blocks.md`](dao/lite-node-blocks.md) | Which BSQ blocks a lite node uses and relays, signed blocks of trusted block providers only, and how many blocks a response holds. |
| [`dao/bitcoind-script-types.md`](dao/bitcoind-script-types.md) | How a full node maps the script types that Bitcoin Core reports, including types of newer Bitcoin Core versions. |
| [`dao/dao-state-resync-warning.md`](dao/dao-state-resync-warning.md) | The desktop warning when the DAO state needs a resync, how often it is shown, and which user actions do not start until the resync. |

### `dispute/`

| Specification | Covers |
|---|---|
| [`dispute/mediation-payout-finality.md`](dispute/mediation-payout-finality.md) | When the payout of a mediation can no longer change, and what the nodes of the mediator and of the traders do with a result that would change it. |
| [`dispute/peer-opened-dispute.md`](dispute/peer-opened-dispute.md) | Which disputes that the agent opens for the other trader a trader's node accepts, and when a new mediation is refused. |
| [`dispute/dispute-message-sender-key.md`](dispute/dispute-message-sender-key.md) | The sender key that dispute messages carry, and why a message without it is dropped. |
| [`dispute/contract-dispute-agents.md`](dispute/contract-dispute-agents.md) | The mediator and refund agent named in the contract, and why a dispute for another agent or with a contract without them is refused. |
| [`dispute/mediation-result-confirmation.md`](dispute/mediation-result-confirmation.md) | Rejecting stale Accept/Reject actions and requiring fresh review of a replacement mediation result with the same payout. |
| [`dispute/refund-claimant-authentication.md`](dispute/refund-claimant-authentication.md) | Escrow-key proof that a refund claimant participated in the trade, including normal and exceptional payout authorization. |
| [`dispute/refund-direct-payout.md`](dispute/refund-direct-payout.md) | One-time refund receipt consumption, durable payout reservation, amount limits, and inbound refund state. |

### `network/`

| Specification | Covers |
|---|---|
| [`network/bisq2-bridge-block-continuity.md`](network/bisq2-bridge-block-continuity.md) | Snapshot and live-stream continuity for DAO block data exported to Bisq 2 oracle nodes. |
| [`network/connection-out-of-memory.md`](network/connection-out-of-memory.md) | What the input handler of a connection does at an `OutOfMemoryError`: it passes the error on, so that a headless node shuts down. |
| [`network/get-data-response-size-limit.md`](network/get-data-response-size-limit.md) | How the size limit of a `GetDataResponse` decides which items it holds, when it is marked as truncated, and that an item above the limit is left out. |
| [`network/initial-data-process-once.md`](network/initial-data-process-once.md) | From which response of the initial data a node applies account age witnesses, signed witnesses and trade statistics. |
| [`network/inventory-requests.md`](network/inventory-requests.md) | How often a node counts its data for the inventory request of any peer: at most once in 10 seconds. |
| [`network/local-bitcoin-node.md`](network/local-bitcoin-node.md) | When a Bitcoin node on localhost is used as the only peer of the wallet, and why a pruned node or one without bloom filters is not. |
| [`network/onion-service-identity.md`](network/onion-service-identity.md) | Where the key of the node's onion service is kept, when a key may be generated, and how it is protected, so that the onion address does not change. |
| [`network/owner-disconnect-backdating.md`](network/owner-disconnect-backdating.md) | Backdating of offers when the connection to their owner is lost, allowed only for outbound connections. |
| [`network/peer-timestamp-validation.md`](network/peer-timestamp-validation.md) | Overflow-safe freshness validation for peer-controlled protocol timestamps. |
| [`network/received-messages.md`](network/received-messages.md) | How a connection reads a received message, the size checks before the message reaches the handlers, and the size limits of the senders of large responses. |
| [`network/reported-peer-dates.md`](network/reported-peer-dates.md) | The date of a peer in the peer lists: the local time of reception for reported peers, and the limits applied when the persisted list is read. |
| [`network/storage-entry-signatures.md`](network/storage-entry-signatures.md) | When a node verifies the signature of a received storage entry, and why a signature that failed its verification is not verified again for a copy of the entry. |
| [`network/tor-startup.md`](network/tor-startup.md) | Starting Tor or connecting to a running Tor, the torrc entries of the user, and the bridges and pluggable transports. |
| [`network/tor-stream-isolation.md`](network/tor-stream-isolation.md) | Which connections may share a Tor circuit, and what `--torStreamIsolation` changes for the HTTP requests. |

### `offer/`

| Specification | Covers |
|---|---|
| [`offer/take-offer-refusals.md`](offer/take-offer-refusals.md) | What the taker is told when the maker refuses an availability request, and what the client remembers about the maker afterwards. |
| [`offer/offer-edit-and-removal.md`](offer/offer-edit-and-removal.md) | The completion contracts of offer edit and removal, and the preconditions they impose on wallet restore, wallet emptying and BSQ swap republishing. |
| [`offer/offer-book-offer-id.md`](offer/offer-book-offer-id.md) | When an offer replaces a listed offer with the same ID in the offer book: only for the same owner. |
| [`offer/bsq-offer-price-range.md`](offer/bsq-offer-price-range.md) | Which BSQ offers the BSQ offer book hides because their price is far from the average BSQ price of 90 days, the factor in the preferences, and the hint when the range hides every offer. |

### `payment/`

| Specification | Covers |
|---|---|
| [`payment/custom-payment-method.md`](payment/custom-payment-method.md) | The payment method for payment methods which Bisq does not support: the name and the account data as free texts, which accounts and offers belong together, the checks on the account of the peer, the trade limit and the warnings. |

### `payment/`

| Specification | Covers |
|---|---|
| [`payment/stablecoin-payment-methods.md`](payment/stablecoin-payment-methods.md) | Stablecoins as payment methods of their peg currency: the listed coins and networks, the meaning of an offer, the account data, and which rules of fiat payment methods apply. |

### `trade/`

| Specification | Covers |
|---|---|
| [`trade/altcoin-volume-precision.md`](trade/altcoin-volume-precision.md) | Rounding of altcoin trade volumes to the asset's chain precision, and the rejection of volumes that round to zero. |
| [`trade/deposit-message-delivery.md`](trade/deposit-message-delivery.md) | When the seller counts the deposit transaction message as delivered to the buyer: on the buyer's acknowledgement, with repeated sending until then, before the deposit transaction is published. |
| [`trade/buyer-payment-account-delivery.md`](trade/buyer-payment-account-delivery.md) | When the buyer counts its payment account message to the seller as delivered: on arrival or on the seller's acknowledgement, with repeated sending until then. |
| [`trade/deposit-transaction-liveness.md`](trade/deposit-transaction-liveness.md) | The block-explorer evidence rules for classifying a deposit transaction as dead and for authorizing the move to failed trades. |
| [`trade/failed-trade-protocol.md`](trade/failed-trade-protocol.md) | What the protocol of a trade stops when the trader moves the trade to the failed trades, and the new protocol of a trade that returns to the pending trades. |
| [`trade/delayed-payout-receivers.md`](trade/delayed-payout-receivers.md) | Proposal authenticity, address-list versioning, and fail-closed receiver rules for Burning Man delayed payout transactions. |
| [`trade/fiat-buyer-payment-account-validation.md`](trade/fiat-buyer-payment-account-validation.md) | Contract-bound buyer payment-account validation and the fiat deposit/settlement gates that depend on it. |
| [`trade/revealed-payment-account-payment-method.md`](trade/revealed-payment-account-payment-method.md) | The payment method which revealed payment account data must have: the one the peer stated in the contract, for the account data of both traders. |
| [`trade/repeated-payment-started-confirmation.md`](trade/repeated-payment-started-confirmation.md) | The buyer's repeated confirmation of the payment start: the desktop sends the message again without changing the trade state and offers the next retry after 10 seconds; the API accepts the confirmation once. |
| [`trade/repeated-payout-confirmation.md`](trade/repeated-payout-confirmation.md) | The seller's repeated payment receipt confirmation after the payout was published: it re-sends the stored payout transaction and never creates it again. A confirmation is refused once the mediated payout is signed by both traders or published. |
| [`trade/take-offer-request-admission.md`](trade/take-offer-request-admission.md) | Which take offer requests the maker accepts for an offer while the handshake of an earlier request is pending, so that a second trade does not replace the trade of a taker who may have paid its fee, and the "offer taken" answer to availability requests in that time. |
| [`trade/taker-trade-tx-fee.md`](trade/taker-trade-tx-fee.md) | The maker's bounds for the trade transaction fee stated by the taker, the taker's check against them before taking an offer, and the fee rate both use. |
| [`trade/withdrawal-completion.md`](trade/withdrawal-completion.md) | The completion invariant of a trade payout withdrawal: local wallet commit, one terminal outcome per request, and broadcast failure as a notification. |
| [`trade/refund-delayed-payout-validation.md`](trade/refund-delayed-payout-validation.md) | Refund-agent validation that a delayed payout transaction spends the deposit transaction's escrow output and pays it to the protocol receivers. |
| [`trade/trade-statistics-validity.md`](trade/trade-statistics-validity.md) | Which trade statistics a node uses, that sums and averages over them do not wrap around or fail for extreme values, that only a recent price from the price feed deactivates an offer through its trigger price, and how a statistic stores its payment method and which received statistics a node accepts by their hash. |
| [`trade/trade-statistics-market-price.md`](trade/trade-statistics-market-price.md) | The market price a node derives from trade statistics for a currency without a price from the price feed: the volume weighted average of the last day for BSQ, the newest trade by trade date for other currencies. |
| [`trade/xmr-payment-proof-timestamp.md`](trade/xmr-payment-proof-timestamp.md) | The timestamp rule of the XMR payment proof used for automatic confirmation, and why it is one-sided. |
| [`trade/bsq-swap-seller-inputs.md`](trade/bsq-swap-seller-inputs.md) | What the BSQ swap buyer verifies about the seller's inputs before it signs: each input spends exactly the described output, which is not a BSQ output, with a valid signature; and the seller's own rule not to select BSQ outputs as inputs. |
| [`trade/bsq-swap-buyer-signatures.md`](trade/bsq-swap-buyer-signatures.md) | What the BSQ swap seller verifies about the buyer's signatures before it accepts the finalized transaction, and the rejection of buyer inputs listed twice. |
| [`trade/bsq-swap-request-admission.md`](trade/bsq-swap-request-admission.md) | Which BSQ swap requests the maker accepts: only for a BSQ swap offer and with the request type of the offer's direction, checked before the offer is reserved. |
| [`trade/payout-seen-in-network.md`](trade/payout-seen-in-network.md) | When a payout transaction seen in the wallet completes a trade, and why a confirmed payout is accepted without its witness data. |
| [`trade/mediated-payout-peer-signature.md`](trade/mediated-payout-peer-signature.md) | How the peer's signature of the mediated payout is checked, and that a signature which arrives before the mediation result is kept until the result is applied. |
| [`trade/mediated-payout-detection.md`](trade/mediated-payout-detection.md) | How a trader who accepted a mediation result detects the mediated payout: from the peer's message and from its wallet, which it watches again after a restart. |
| [`trade/mediated-payout-finalization.md`](trade/mediated-payout-finalization.md) | When a trader's node publishes the mediated payout without a further click and what it waits for, why a signed acceptance cannot be rejected, and when the peer's payout message is accepted. |
| [`trade/deposit-maker-change.md`](trade/deposit-maker-change.md) | The maker's change output in the deposit transaction: the trade amounts the maker accepts and the change outputs the taker accepts, so that the deposit transaction stays relayable. |
| [`trade/deposit-taker-signature.md`](trade/deposit-taker-signature.md) | Which transaction the taker signs as deposit transaction: only one with the transaction ID of the maker's prepared deposit transaction, and the final sequence number of every deposit transaction input, so that the delayed payout transaction spends the deposit that gets published. |
