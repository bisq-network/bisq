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

package bisq.apitest.method.trade;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.apitest.config.ApiTestConfig.XMR;
import static bisq.cli.table.builder.TableType.OFFER_TBL;
import static bisq.core.trade.model.bisq_v1.Trade.Phase.PAYOUT_PUBLISHED;
import static bisq.core.trade.model.bisq_v1.Trade.Phase.WITHDRAWN;
import static bisq.core.trade.model.bisq_v1.Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG;
import static bisq.core.trade.model.bisq_v1.Trade.State.WITHDRAW_COMPLETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.OfferDirection.BUY;



import bisq.cli.table.builder.TableBuilder;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TakeSellXMROfferTest extends AbstractTradeTest {

    // Alice is maker / xmr seller (btc buyer), Bob is taker / xmr buyer (btc seller).

    // Maker and Taker fees are in BTC.
    private static final String TRADE_FEE_CURRENCY_CODE = BTC;

    private static final String WITHDRAWAL_TX_MEMO = "Bob's trade withdrawal";

    @BeforeAll
    public static void setUp() {
        setUp(false);
        createXmrPaymentAccounts();
        EXPECTED_PROTOCOL_STATUS.init();
    }

    @Test
    @Order(1)
    public void testTakeAlicesBuyBTCForXMROffer(final TestInfo testInfo) {
        try {
            // Alice is going to SELL XMR, but the Offer direction = BUY because it is a
            // BTC trade;  Alice will BUY BTC for XMR.  Alice will send Bob XMR.
            // Confused me, but just need to remember there are only BTC offers.
            var btcTradeDirection = BUY.name();
            double priceMarginPctInput = 1.50;
            var alicesOffer = aliceClient.createMarketBasedPricedOffer(btcTradeDirection,
                    XMR,
                    20_000_000L,
                    10_500_000L,
                    priceMarginPctInput,
                    defaultBuyerSecurityDepositPct.get(),
                    alicesXmrAcct.getId(),
                    TRADE_FEE_CURRENCY_CODE,
                    NO_TRIGGER_PRICE);
            log.debug("Alice's SELL XMR (BUY BTC) Offer:\n{}", new TableBuilder(OFFER_TBL, alicesOffer).build());
            assertTrue(alicesOffer.getIsMakerApiUser());
            genBtcBlocksThenWait(1, 4000);
            var offerId = alicesOffer.getId();
            assertTrue(alicesOffer.getIsCurrencyForMakerFeeBtc());

            var alicesXmrOffers = aliceClient.getMyOffers(btcTradeDirection, XMR);
            assertEquals(1, alicesXmrOffers.size());

            var intendedTradeAmount = 10_500_000L;
            var trade = takeAlicesOffer(offerId,
                    bobsXmrAcct.getId(),
                    TRADE_FEE_CURRENCY_CODE,
                    intendedTradeAmount);
            alicesXmrOffers = aliceClient.getMyOffersSortedByDate(XMR);
            assertEquals(0, alicesXmrOffers.size());

            trade = bobClient.getTrade(tradeId);
            assertEquals(intendedTradeAmount, trade.getTradeAmountAsLong());
            assertTrue(trade.getIsTakerApiUser());
            assertTrue(trade.getOffer().getIsMakerApiUser());
            verifyTakerDepositNotConfirmed(trade);
            logTrade(log, testInfo, "Alice's Maker/Seller View", aliceClient.getTrade(tradeId));
            logTrade(log, testInfo, "Bob's Taker/Buyer View", bobClient.getTrade(tradeId));
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(2)
    public void testPaymentMessagingPreconditions(final TestInfo testInfo) {
        try {
            // Alice is maker / xmr seller (btc buyer), Bob is taker / xmr buyer (btc seller).
            // Verify payment sent and rcvd msgs are sent by the right peers:  buyer and seller.
            verifyPaymentSentMsgIsFromBtcBuyerPrecondition(log, bobClient);
            verifyPaymentReceivedMsgIsFromBtcSellerPrecondition(log, aliceClient);

            // Verify xmr payment sent and rcvd msgs cannot be sent before trade deposit tx is confirmed.
            verifyPaymentSentMsgDepositTxConfirmedPrecondition(log, aliceClient);
            verifyPaymentReceivedMsgDepositTxConfirmedPrecondition(log, bobClient);

            // Now generate the BTC block to confirm the taker deposit tx.
            genBtcBlocksThenWait(1, 2_500);
            waitForTakerDepositConfirmation(log, testInfo, bobClient, tradeId);

            // Verify the seller can only send a payment rcvd msg after the payment started msg.
            verifyPaymentReceivedMsgAfterPaymentSentMsgPrecondition(log, bobClient);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(3)
    public void testAlicesConfirmPaymentStarted(final TestInfo testInfo) {
        try {
            var trade = aliceClient.getTrade(tradeId);
            assertTrue(trade.getIsTakerApiUser());
            assertTrue(trade.getOffer().getIsMakerApiUser());
            waitForTakerDepositConfirmation(log, testInfo, aliceClient, trade.getTradeId());
            log.debug("Alice sends XMR payment to Bob for trade {}", trade.getTradeId());
            aliceClient.confirmPaymentStarted(trade.getTradeId());
            sleep(3_500);

            waitUntilBuyerSeesPaymentStartedMessage(log, testInfo, aliceClient, tradeId);
            logTrade(log, testInfo, "Alice's Maker/Seller View (Payment Sent)", aliceClient.getTrade(tradeId));
            logTrade(log, testInfo, "Bob's Taker/Buyer View (Payment Sent)", bobClient.getTrade(tradeId));
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(4)
    public void testBobsConfirmPaymentReceived(final TestInfo testInfo) {
        try {
            waitUntilSellerSeesPaymentStartedMessage(log, testInfo, bobClient, tradeId);

            var trade = bobClient.getTrade(tradeId);
            assertTrue(trade.getIsTakerApiUser());
            assertTrue(trade.getOffer().getIsMakerApiUser());
            sleep(2_000);
            // If we were trading BSQ, Bob would verify payment has been sent to his
            // Bisq / BSQ wallet, but we can do no such checks for XMR payments.
            // All XMR transfers are done outside Bisq.
            log.debug("Bob verifies XMR payment was received from Alice, for trade {}", trade.getTradeId());
            bobClient.confirmPaymentReceived(trade.getTradeId());
            sleep(3_000);

            trade = bobClient.getTrade(tradeId);
            // Warning:  trade.getOffer().getState() might be AVAILABLE, not OFFER_FEE_PAID.
            EXPECTED_PROTOCOL_STATUS.setState(SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG)
                    .setPhase(PAYOUT_PUBLISHED)
                    .setPayoutPublished(true)
                    .setPaymentReceivedMessageSent(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Alice's Maker/Seller View (Payment Received)", aliceClient.getTrade(tradeId));
            logTrade(log, testInfo, "Bob's Taker/Buyer View (Payment Received)", bobClient.getTrade(tradeId));
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(5)
    public void testAlicesBtcWithdrawalToExternalAddress(final TestInfo testInfo) {
        try {
            genBtcBlocksThenWait(1, 1_000);

            var trade = aliceClient.getTrade(tradeId);
            logTrade(log, testInfo, "Alice's view before withdrawing BTC funds to external wallet", trade);

            String toAddress = bitcoinCli.getNewBtcAddress();
            aliceClient.withdrawFunds(tradeId, toAddress, WITHDRAWAL_TX_MEMO);
            bobClient.closeTrade(tradeId);
            genBtcBlocksThenWait(1, 1_000);

            trade = aliceClient.getTrade(tradeId);
            EXPECTED_PROTOCOL_STATUS.setState(WITHDRAW_COMPLETED)
                    .setPhase(WITHDRAWN)
                    .setCompleted(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Alice's Maker/Seller View (Done)", aliceClient.getTrade(tradeId));
            logTrade(log, testInfo, "Bob's Taker/Buyer View (Done)", bobClient.getTrade(tradeId));
            logBalances(log, testInfo);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }
}
