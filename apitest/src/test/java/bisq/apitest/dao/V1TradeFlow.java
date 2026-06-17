/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.apitest.dao;

import bisq.cli.GrpcClient;

import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reusable v1 (fiat) take-offer + payment + payout flow, factored out so multiple
 * scenario tests can drive it with arbitrary maker/taker pairings and currencies
 * without duplicating the (subtle) tx-confirmation ordering and sync gates.
 *
 * <p>The flow mirrors {@link TradeScenarioTest#runV1Trade} but is parameterised on
 * maker/taker/currency so a single helper covers every role combination
 * (maker-buyer, maker-seller, taker-buyer, taker-seller) and every account type.
 *
 * <p>Who is the BTC buyer is fixed by the offer direction, not by which peer is the
 * maker: a SELL offer means the maker sells BTC (maker = BTC seller, taker = BTC
 * buyer); a BUY offer is the reverse.
 */
final class V1TradeFlow {

    private V1TradeFlow() {
    }

    /**
     * Safety-net timeout for each state-gated wait. NOT a pacing delay — every wait
     * returns the instant its signal flips (a few seconds when the wallet is current).
     *
     * <p>Sized above the 30s the few-trade {@link TradeScenarioTest} uses for headroom on a
     * loaded box. The deep wallet-confirmation stall the soak used to hit (bloom-filter race
     * at trade ~34) is now prevented at its source by
     * {@link DaoTestUtils#confirmTxAfterFilterPropagation}, so confirmations land in seconds;
     * this is purely a safety net.
     */
    private static final long CONFIRM_TIMEOUT_MS = 120_000;

    /**
     * Execute one full v1 trade and assert BTC moved in the expected direction.
     *
     * @param dao                   chain/tx helper bound to the stack
     * @param maker                 daemon that placed {@code offer}
     * @param taker                 daemon taking the offer
     * @param offer                 the maker's already-placed offer (see
     *                              {@link DaoTestUtils#placeV1OfferWhenReady})
     * @param currency              the offer's fiat currency code (e.g. "USD")
     * @param btcAmountSats         amount to take, in satoshis
     * @param takerPaymentAccountId the taker's payment-account id for this currency
     * @return the completed {@link TradeInfo}
     */
    static TradeInfo runV1Trade(DaoTestUtils dao,
                                GrpcClient maker,
                                GrpcClient taker,
                                OfferInfo offer,
                                String currency,
                                long btcAmountSats,
                                String takerPaymentAccountId) {
        boolean makerSellsBtc = "SELL".equalsIgnoreCase(offer.getDirection());
        GrpcClient btcSeller = makerSellsBtc ? maker : taker;
        GrpcClient btcBuyer = makerSellsBtc ? taker : maker;

        // Snapshot full wallet BTC (available + reserved + locked) before the trade.
        // available alone is misleading because the security-deposit refund flows from
        // reserved back to available, so a seller's available can rise even as they send
        // BTC. The full total nets deposits out and reflects only net BTC moved.
        long makerBtcBefore = totalBtc(maker);
        long takerBtcBefore = totalBtc(taker);

        // Confirm the maker's offer-fee tx so its outputs are spendable by the deposit tx.
        // Use filter-propagation gating: the maker broadcast this tx, and awaitMakerReadyToRespond
        // below waits for the maker's wallet to see it confirmed — a plain mine could lose the
        // bloom-filter race and leave the maker-fee perpetually "pending" in the maker's wallet.
        if (!offer.getOfferFeePaymentTxId().isEmpty()) {
            dao.confirmTxAfterFilterPropagation(maker, offer.getOfferFeePaymentTxId());
        }
        // Wait for the taker to see the offer on the P2P offer book (either direction list).
        DaoTestUtils.await(() -> taker.getOffers("BUY", currency).stream()
                        .anyMatch(o -> o.getId().equals(offer.getId()))
                        || taker.getOffers("SELL", currency).stream()
                        .anyMatch(o -> o.getId().equals(offer.getId())),
                60_000, "taker sees offer " + offer.getId());

        TradeInfo trade = takeOfferWhenReady(maker, taker, offer, takerPaymentAccountId, btcAmountSats);
        assertNotNull(trade);

        // Confirm the taker-fee tx before the deposit tx, otherwise the deposit tx spends
        // outputs not yet on chain → bitcoind rejects with bad-txns-inputs-missingorspent.
        // Taker-fee only needs to be on-chain (bitcoind-side) so the deposit can spend it;
        // no wallet-confirmation signal is gated on it, so plain confirmTx is fine.
        DaoTestUtils.await(() -> !taker.getTrade(trade.getTradeId()).getTakerFeeTxId().isEmpty(),
                CONFIRM_TIMEOUT_MS, "taker has taker_fee_tx_id for " + trade.getTradeId());
        dao.confirmTx(taker, taker.getTrade(trade.getTradeId()).getTakerFeeTxId());

        // The BTC seller publishes the deposit tx (SellerPublishesDepositTx), and we gate on
        // its wallet seeing the deposit confirmed. Mine via confirmTxAfterFilterPropagation so
        // the seller's bitcoinj bloom filter reaches bitcoind before the confirming block —
        // otherwise the merkleblock can omit the deposit and the seller's SPV wallet never
        // marks it confirmed (the soak's trade-~34 stall). See DaoTestUtils for the full race.
        DaoTestUtils.await(() -> !btcSeller.getTrade(trade.getTradeId()).getDepositTxId().isEmpty(),
                CONFIRM_TIMEOUT_MS, "btc-seller has deposit_tx_id for " + trade.getTradeId());
        dao.confirmTxAfterFilterPropagation(btcSeller, btcSeller.getTrade(trade.getTradeId()).getDepositTxId());
        DaoTestUtils.await(() -> btcSeller.getTrade(trade.getTradeId()).getIsDepositConfirmed(),
                CONFIRM_TIMEOUT_MS, "deposit confirmed for trade " + trade.getTradeId());

        btcBuyer.confirmPaymentStarted(trade.getTradeId());
        DaoTestUtils.await(() -> btcSeller.getTrade(trade.getTradeId()).getIsPaymentStartedMessageSent(),
                CONFIRM_TIMEOUT_MS, "seller sees payment-started for " + trade.getTradeId());
        btcSeller.confirmPaymentReceived(trade.getTradeId());
        DaoTestUtils.await(() -> !btcSeller.getTrade(trade.getTradeId()).getPayoutTxId().isEmpty(),
                CONFIRM_TIMEOUT_MS, "seller has payout_tx_id for " + trade.getTradeId());
        // Payout tx is published by the BTC seller; same filter-propagation gating so its
        // wallet (and the buyer's, which has watched the multisig since the deposit) registers
        // the payout rather than racing the block.
        dao.confirmTxAfterFilterPropagation(btcSeller, btcSeller.getTrade(trade.getTradeId()).getPayoutTxId());
        DaoTestUtils.await(() -> btcBuyer.getTrade(trade.getTradeId()).getIsPayoutPublished(),
                CONFIRM_TIMEOUT_MS, "payout published for " + trade.getTradeId());

        btcBuyer.closeTrade(trade.getTradeId());

        // Mine a confirmation so the payout tx counts toward available_balance, then assert
        // net BTC moved from seller to buyer.
        dao.generateBlocks(1);
        long makerBtcAfter = totalBtc(maker);
        long takerBtcAfter = totalBtc(taker);
        if (makerSellsBtc) {
            assertTrue(takerBtcAfter > takerBtcBefore,
                    "BTC buyer (taker) total must increase: before=" + takerBtcBefore + " after=" + takerBtcAfter);
            assertTrue(makerBtcAfter < makerBtcBefore,
                    "BTC seller (maker) total must decrease: before=" + makerBtcBefore + " after=" + makerBtcAfter);
        } else {
            assertTrue(makerBtcAfter > makerBtcBefore,
                    "BTC buyer (maker) total must increase: before=" + makerBtcBefore + " after=" + makerBtcAfter);
            assertTrue(takerBtcAfter < takerBtcBefore,
                    "BTC seller (taker) total must decrease: before=" + takerBtcBefore + " after=" + takerBtcAfter);
        }
        return trade;
    }

    /**
     * Take the offer, retrying while the maker NACKs the OfferAvailabilityRequest because
     * its bitcoinj wallet is momentarily out of sync.
     *
     * <p>{@code OpenOfferManager.handleOfferAvailabilityRequest} answers with only a NACK
     * (no OfferAvailabilityResponse) when {@code WalletsSetup.isChainHeightSyncedWithinTolerance}
     * is false — i.e. the maker's wallet best-chain height is more than 3 blocks behind its
     * peers. Under a long soak the wallet's block download over bitcoind's P2P link stalls
     * for seconds at a time (see {@link DaoTestUtils#confirmTx}), so a take issued in that
     * window times out client-side with "peer has not responded". The stall is transient:
     * the wallet catches up within a few seconds. The NACK happens in the availability
     * phase, before any trade is created or funds reserved, so a retry is side-effect free
     * — the same rationale and pattern as {@link DaoTestUtils#placeV1OfferWhenReady}.
     */
    private static TradeInfo takeOfferWhenReady(GrpcClient maker, GrpcClient taker, OfferInfo offer,
                                                String takerPaymentAccountId, long btcAmountSats) {
        // The deadline bounds how many RETRY attempts we make on a transient availability NACK,
        // not the exact wall-clock: it is checked at the top of each iteration, and the
        // awaitMakerReadyToRespond + takeOffer of an in-flight attempt carry their own timeouts,
        // so the last attempt can run somewhat past it. That is fine for a test safety net — the
        // readiness waits resolve in seconds once the maker's chain-sync guard is satisfied (see
        // the WalletsSetup.isChainHeightSyncedWithinTolerance fix), so retries are rare.
        long deadline = System.currentTimeMillis() + 180_000;
        RuntimeException last = null;
        while (System.currentTimeMillis() < deadline) {
            awaitMakerReadyToRespond(maker, offer);
            try {
                return taker.takeOffer(offer.getId(), takerPaymentAccountId, "BTC", btcAmountSats);
            } catch (RuntimeException ex) {
                if (!isMakerNotYetSynced(ex)) throw ex;
                last = ex;
                // Brief pause before re-gating and retrying the take.
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new AssertionError("take never accepted (offer " + offer.getId()
                + ") — maker stayed out of sync past the ~180s retry budget", last);
    }

    /** True if the take failed only because the maker had not yet re-synced its wallet
     *  (the transient availability NACK described on {@link #takeOfferWhenReady}). */
    private static boolean isMakerNotYetSynced(RuntimeException ex) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return msg.contains("peer has not responded") || msg.contains("not synced");
    }

    /**
     * Block until the maker is in the synced state its OfferAvailabilityRequest guard
     * requires. {@code OpenOfferManager.handleOfferAvailabilityRequest} replies with ONLY
     * a NACK (no OfferAvailabilityResponse) when the maker's DAO state or BTC wallet chain
     * height is not yet synced; the taker's protocol only resolves on an
     * OfferAvailabilityResponse, so a NACK leaves it to time out after 90s. Gate on every
     * signal the maker's guard reads being observably true before letting the taker request.
     *
     * <p>The decisive one for the soak is {@code isChainHeightSyncedWithinTolerance}: under
     * sustained load the maker's bitcoinj wallet best-chain height trails its peers by more
     * than the 3-block tolerance for a while (SPV block download lags the chain tip). Polling
     * the daemon's own wallet-sync gRPC here blocks until the wallet has actually caught up,
     * which is exactly the condition the maker checks — eliminating the "chain is not synced"
     * NACK at its source rather than racing it with a retry.
     */
    private static void awaitMakerReadyToRespond(GrpcClient maker, OfferInfo offer) {
        DaoTestUtils.await(maker::getDaoStatus, 60_000, "maker DAO state ready and in sync");
        awaitMakerWalletSynced(maker);
        String makerFeeTxId = offer.getOfferFeePaymentTxId();
        if (makerFeeTxId != null && !makerFeeTxId.isEmpty()) {
            DaoTestUtils.await(() -> !maker.getTransaction(makerFeeTxId).getIsPending(),
                    CONFIRM_TIMEOUT_MS, "maker BTC wallet confirmed maker-fee tx " + makerFeeTxId);
        }
    }

    /**
     * Block until the maker's bitcoinj wallet reports chain-height-synced-within-tolerance —
     * the wallet-side half of the maker's offer-availability guard.
     *
     * <p>Degrades gracefully for cross-version (compat) runs: the {@code GetWalletSyncStatus}
     * gRPC is new, so a maker running an older release answers UNIMPLEMENTED. In that case we
     * skip this gate and fall back to the pre-existing signals (DAO status + maker-fee
     * confirmed) plus the take-retry, exactly as before the gRPC existed. Rate-limit / other
     * transient errors are retried; only a genuine "never synced" trips the timeout.
     */
    private static void awaitMakerWalletSynced(GrpcClient maker) {
        long deadline = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (maker.isWalletChainHeightSyncedWithinTolerance()) return;
            } catch (StatusRuntimeException ex) {
                if (ex.getStatus().getCode() == Status.Code.UNIMPLEMENTED) return; // older peer
                // else transient (e.g. rate limit) — keep polling
            } catch (RuntimeException transientEx) {
                // transient — keep polling
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("timed out waiting for: maker BTC wallet chain height synced within tolerance");
    }

    /** Sum of available + reserved + locked BTC — the wallet's full BTC footprint. */
    static long totalBtc(GrpcClient c) {
        var b = c.getBalances().getBtc();
        return b.getAvailableBalance() + b.getReservedBalance() + b.getLockedBalance();
    }
}
