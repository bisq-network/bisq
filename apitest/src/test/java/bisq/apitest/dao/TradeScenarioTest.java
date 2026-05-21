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

import protobuf.PaymentAccount;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * After the DAO suite runs, exercise the trade flow Alice→Bob across four offer kinds:
 *   - v1 BTC sell (Alice maker BTC seller / fiat buyer; Bob taker)
 *   - v1 BTC buy  (Alice maker BTC buyer  / fiat seller; Bob taker)
 *   - BSQ swap sell (Alice maker sells BTC for BSQ; Bob taker)
 *   - BSQ swap buy  (Alice maker buys BTC with BSQ; Bob taker)
 *
 * Uses F2F payment accounts (simplest CLI-creatable account, minimal validation).
 *
 * Note: trade completion needs mediator + refund agent registered. The arb container's
 * entrypoint does this on startup; DisputeAgentBootstrapTest verifies it.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TradeScenarioTest extends DaoTestBase {

    private static final String COUNTRY = "US";
    private static final String CURRENCY = "USD";
    private static final long BTC_AMOUNT_SATS = 1_250_000L; // 0.0125 BTC
    private static final String FIXED_FIAT_PRICE = "50000"; // USD/BTC
    private static final String BSQ_SWAP_PRICE = "0.00005"; // BTC per BSQ
    private static final double SECURITY_DEPOSIT_PCT = 15.0;

    private static PaymentAccount aliceF2F;
    private static PaymentAccount bobF2F;

    private PaymentAccount ensureF2F(GrpcClient c, String accountName) {
        for (PaymentAccount existing : c.getPaymentAccounts()) {
            if (accountName.equals(existing.getAccountName())) return existing;
        }
        String json = "{\n"
                + "  \"_COMMENTS_\": \"dao-test F2F account\",\n"
                + "  \"paymentMethodId\": \"F2F\",\n"
                + "  \"accountName\": \"" + accountName + "\",\n"
                + "  \"city\": \"Anytown\",\n"
                + "  \"contact\": \"Morse Code\",\n"
                + "  \"country\": \"" + COUNTRY + "\",\n"
                + "  \"extraInfo\": \"DAO-test\"\n"
                + "}\n";
        return c.createPaymentAccount(json);
    }

    private void ensureAccounts() {
        if (aliceF2F == null) aliceF2F = ensureF2F(alice, "alice-f2f");
        if (bobF2F == null)   bobF2F   = ensureF2F(bob,   "bob-f2f");
    }

    @Test
    @Order(1)
    public void aliceSellsBtc_bobTakes_v1() {
        ensureAccounts();
        OfferInfo offer = alice.createFixedPricedOffer(
                "SELL", CURRENCY, BTC_AMOUNT_SATS, BTC_AMOUNT_SATS, FIXED_FIAT_PRICE,
                SECURITY_DEPOSIT_PCT, aliceF2F.getId(), "BTC");
        runV1Trade(offer, /*bobIsBtcBuyer=*/ true);
    }

    @Test
    @Order(2)
    public void aliceBuysBtc_bobTakes_v1() {
        ensureAccounts();
        OfferInfo offer = alice.createFixedPricedOffer(
                "BUY", CURRENCY, BTC_AMOUNT_SATS, BTC_AMOUNT_SATS, FIXED_FIAT_PRICE,
                SECURITY_DEPOSIT_PCT, aliceF2F.getId(), "BTC");
        runV1Trade(offer, /*bobIsBtcBuyer=*/ false);
    }

    @Test
    @Order(3)
    public void aliceSellsBtcForBsq_bobTakes_swap() {
        OfferInfo offer = alice.createBsqSwapOffer(
                "SELL", BTC_AMOUNT_SATS, BTC_AMOUNT_SATS, BSQ_SWAP_PRICE);
        runSwapTrade(offer);
    }

    @Test
    @Order(4)
    public void aliceBuysBtcForBsq_bobTakes_swap() {
        OfferInfo offer = alice.createBsqSwapOffer(
                "BUY", BTC_AMOUNT_SATS, BTC_AMOUNT_SATS, BSQ_SWAP_PRICE);
        runSwapTrade(offer);
    }

    /**
     * v1 trade execution. The BTC buyer sends "payment started"; the BTC seller acknowledges
     * "payment received"; the buyer closes the trade.
     */
    private void runV1Trade(OfferInfo offer, boolean bobIsBtcBuyer) {
        // Snapshot total BTC (available + reserved + locked) before the trade.
        // available_balance alone is misleading because security-deposit refund flows
        // from reserved back to available, so the seller's available can INCREASE
        // even though they sent BTC. The full wallet total nets out deposits and
        // only reflects the net BTC moved out of (or into) the wallet.
        long aliceBtcBefore = totalBtc(alice);
        long bobBtcBefore = totalBtc(bob);

        // Confirm alice's offer-fee tx so its outputs are spendable by the deposit tx.
        if (!offer.getOfferFeePaymentTxId().isEmpty()) {
            dao.confirmTx(alice, offer.getOfferFeePaymentTxId());
        }
        // Wait for Bob to see the offer on the P2P offer book.
        DaoTestUtils.await(() -> bob.getOffers("BUY", CURRENCY).stream()
                        .anyMatch(o -> o.getId().equals(offer.getId()))
                        || bob.getOffers("SELL", CURRENCY).stream()
                        .anyMatch(o -> o.getId().equals(offer.getId())),
                60_000, "bob sees offer " + offer.getId());

        TradeInfo trade = bob.takeOffer(offer.getId(), bobF2F.getId(), "BTC", BTC_AMOUNT_SATS);
        assertNotNull(trade);
        // Confirm bob's taker-fee tx before the deposit tx, otherwise the deposit tx
        // spends outputs not yet on chain → bitcoind rejects with bad-txns-inputs-missingorspent.
        DaoTestUtils.await(() -> !bob.getTrade(trade.getTradeId()).getTakerFeeTxId().isEmpty(),
                30_000, "bob has taker_fee_tx_id for " + trade.getTradeId());
        String takerFeeTxId = bob.getTrade(trade.getTradeId()).getTakerFeeTxId();
        dao.confirmTx(bob, takerFeeTxId);
        DaoTestUtils.await(() -> !bob.getTrade(trade.getTradeId()).getDepositTxId().isEmpty(),
                30_000, "bob has deposit_tx_id for " + trade.getTradeId());
        String depositTxId = bob.getTrade(trade.getTradeId()).getDepositTxId();
        dao.confirmTx(bob, depositTxId);
        DaoTestUtils.await(() -> bob.getTrade(trade.getTradeId()).getIsDepositConfirmed(),
                30_000, "deposit confirmed for trade " + trade.getTradeId());

        GrpcClient btcBuyer = bobIsBtcBuyer ? bob : alice;
        GrpcClient btcSeller = bobIsBtcBuyer ? alice : bob;

        btcBuyer.confirmPaymentStarted(trade.getTradeId());
        DaoTestUtils.await(() -> btcSeller.getTrade(trade.getTradeId()).getIsPaymentStartedMessageSent(),
                60_000, "seller sees payment-started for " + trade.getTradeId());
        btcSeller.confirmPaymentReceived(trade.getTradeId());
        DaoTestUtils.await(() -> !btcSeller.getTrade(trade.getTradeId()).getPayoutTxId().isEmpty(),
                30_000, "seller has payout_tx_id for " + trade.getTradeId());
        String payoutTxId = btcSeller.getTrade(trade.getTradeId()).getPayoutTxId();
        dao.confirmTx(btcSeller, payoutTxId);
        DaoTestUtils.await(() -> btcBuyer.getTrade(trade.getTradeId()).getIsPayoutPublished(),
                30_000, "payout published for " + trade.getTradeId());

        // Trade is fully executed at this point (payout tx on chain, both sides aware).
        // closeTrade moves the trade record from the open list to the closed/history
        // list — it does NOT transition the trade to the WITHDRAWN phase, which only
        // happens via a subsequent withdrawFunds call. So `getIsCompleted` (which
        // reflects `isWithdrawn`) stays false. Just verify closeTrade doesn't error.
        btcBuyer.closeTrade(trade.getTradeId());

        // Mine a confirmation so the payout tx is counted in available_balance (not
        // just reserved/unconfirmed), then assert the BTC moved in the expected
        // direction. The BSQ trade fee is burned, so the BSQ side moves too, but
        // the precise BSQ delta is small and intentionally not asserted here.
        dao.generateBlocks(1);
        long aliceBtcAfter = totalBtc(alice);
        long bobBtcAfter = totalBtc(bob);
        if (bobIsBtcBuyer) {
            assertTrue(bobBtcAfter > bobBtcBefore,
                    "BTC buyer (bob) total BTC must increase: before=" + bobBtcBefore + " after=" + bobBtcAfter);
            assertTrue(aliceBtcAfter < aliceBtcBefore,
                    "BTC seller (alice) total BTC must decrease: before=" + aliceBtcBefore + " after=" + aliceBtcAfter);
        } else {
            assertTrue(aliceBtcAfter > aliceBtcBefore,
                    "BTC buyer (alice) total BTC must increase: before=" + aliceBtcBefore + " after=" + aliceBtcAfter);
            assertTrue(bobBtcAfter < bobBtcBefore,
                    "BTC seller (bob) total BTC must decrease: before=" + bobBtcBefore + " after=" + bobBtcAfter);
        }
    }

    /** Sum of available + reserved + locked BTC — the wallet's full BTC footprint. */
    private static long totalBtc(GrpcClient c) {
        var b = c.getBalances().getBtc();
        return b.getAvailableBalance() + b.getReservedBalance() + b.getLockedBalance();
    }

    /**
     * BSQ swap. Atomic single-transaction trade; no payment-started flow needed.
     */
    private void runSwapTrade(OfferInfo offer) {
        long aliceBtcBefore = totalBtc(alice);
        long bobBtcBefore = totalBtc(bob);
        long aliceBsqBefore = alice.getBalances().getBsq().getAvailableConfirmedBalance();
        long bobBsqBefore = bob.getBalances().getBsq().getAvailableConfirmedBalance();
        String direction = offer.getDirection();  // "SELL" = alice sells BTC for BSQ

        DaoTestUtils.await(() -> bob.getBsqSwapOffers("BUY").stream()
                        .anyMatch(o -> o.getId().equals(offer.getId()))
                        || bob.getBsqSwapOffers("SELL").stream()
                        .anyMatch(o -> o.getId().equals(offer.getId())),
                60_000, "bob sees swap offer " + offer.getId());

        TradeInfo trade = bob.takeBsqSwapOffer(offer.getId(), BTC_AMOUNT_SATS);
        assertNotNull(trade);
        // BSQ swap produces a single atomic tx; wait briefly for tx_id then inject.
        DaoTestUtils.await(() -> {
            TradeInfo t = bob.getTrade(trade.getTradeId());
            return !t.getBsqSwapTradeInfo().getTxId().isEmpty();
        }, 30_000, "bsq swap tx_id for " + trade.getTradeId());
        String swapTxId = bob.getTrade(trade.getTradeId()).getBsqSwapTradeInfo().getTxId();
        dao.confirmTx(bob, swapTxId);
        DaoTestUtils.await(() -> {
            TradeInfo t = bob.getTrade(trade.getTradeId());
            return t.getBsqSwapTradeInfo().getNumConfirmations() >= 1;
        }, 30_000, "bsq swap confirmed for " + trade.getTradeId());

        assertEquals(BTC_AMOUNT_SATS, bob.getTrade(trade.getTradeId())
                .getBsqSwapTradeInfo().getBtcTradeAmount());

        // Mine a confirmation so the swap outputs count toward available_balance, then
        // await each expected balance movement. The BSQ parser updates the confirmed
        // BSQ balance asynchronously after the block arrives, so a single one-shot read
        // can race the parser and observe stale values — gate on the observable state
        // change instead, with the timeout acting as the safety net.
        dao.generateBlocks(1);
        boolean aliceSellsBtc = "SELL".equalsIgnoreCase(direction);
        if (aliceSellsBtc) {
            // Alice sends BTC, receives BSQ. Bob sends BSQ, receives BTC.
            awaitBalanceMove("alice BTC must decrease", aliceBtcBefore,
                    () -> totalBtc(alice), false);
            awaitBalanceMove("bob BTC must increase", bobBtcBefore,
                    () -> totalBtc(bob), true);
            awaitBalanceMove("alice BSQ must increase", aliceBsqBefore,
                    () -> alice.getBalances().getBsq().getAvailableConfirmedBalance(), true);
            awaitBalanceMove("bob BSQ must decrease", bobBsqBefore,
                    () -> bob.getBalances().getBsq().getAvailableConfirmedBalance(), false);
        } else {
            // Alice buys BTC with BSQ. Bob sells BTC for BSQ.
            awaitBalanceMove("alice BTC must increase", aliceBtcBefore,
                    () -> totalBtc(alice), true);
            awaitBalanceMove("bob BTC must decrease", bobBtcBefore,
                    () -> totalBtc(bob), false);
            awaitBalanceMove("alice BSQ must decrease", aliceBsqBefore,
                    () -> alice.getBalances().getBsq().getAvailableConfirmedBalance(), false);
            awaitBalanceMove("bob BSQ must increase", bobBsqBefore,
                    () -> bob.getBalances().getBsq().getAvailableConfirmedBalance(), true);
        }
    }

    private static void awaitBalanceMove(String label, long before,
                                         java.util.function.LongSupplier reader,
                                         boolean expectIncrease) {
        DaoTestUtils.await(() -> {
            long now = reader.getAsLong();
            return expectIncrease ? now > before : now < before;
        }, 30_000, label + " (before=" + before + ")");
    }
}
