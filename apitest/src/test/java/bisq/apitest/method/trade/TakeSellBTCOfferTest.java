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

import bisq.core.payment.PaymentAccount;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.apitest.config.ApiTestConfig.USD;
import static bisq.core.trade.model.bisq_v1.Trade.Phase.PAYOUT_PUBLISHED;
import static bisq.core.trade.model.bisq_v1.Trade.Phase.WITHDRAWN;
import static bisq.core.trade.model.bisq_v1.Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG;
import static bisq.core.trade.model.bisq_v1.Trade.State.WITHDRAW_COMPLETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.Offer.State.OFFER_FEE_PAID;
import static protobuf.OfferDirection.SELL;

@Disabled
@SuppressWarnings("ConstantConditions")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TakeSellBTCOfferTest extends AbstractTradeTest {

    // Alice is maker/seller, Bob is taker/buyer.

    // Maker and Taker fees are in BTC.
    private static final String TRADE_FEE_CURRENCY_CODE = BTC;

    private static final String WITHDRAWAL_TX_MEMO = "Bob's trade withdrawal";

    @Test
    @Order(1)
    public void testTakeAlicesSellOffer(final TestInfo testInfo) {
        try {
            PaymentAccount alicesUsdAccount = createDummyF2FAccount(aliceClient, "US");
            var alicesOffer = aliceClient.createMarketBasedPricedOffer(SELL.name(),
                    USD,
                    12_500_000L,
                    12_500_000L, // min-amount = amount
                    0.00,
                    defaultBuyerSecurityDepositPct.get(),
                    alicesUsdAccount.getId(),
                    TRADE_FEE_CURRENCY_CODE,
                    NO_TRIGGER_PRICE);
            var offerId = alicesOffer.getId();
            assertTrue(alicesOffer.getIsCurrencyForMakerFeeBtc());

            // Wait for Alice's AddToOfferBook task.
            // Wait times vary;  my logs show >= 2-second delay, but taking sell offers
            // seems to require more time to prepare.
            sleep(3_000); // TODO loop instead of hard code a wait time
            var alicesUsdOffers = aliceClient.getMyOffersSortedByDate(SELL.name(), USD);
            assertEquals(1, alicesUsdOffers.size());

            PaymentAccount bobsUsdAccount = createDummyF2FAccount(bobClient, "US");
            var trade = takeAlicesOffer(offerId,
                    bobsUsdAccount.getId(),
                    TRADE_FEE_CURRENCY_CODE,
                    0L,
                    false);
            sleep(2_500);  // Allow available offer to be removed from offer book.
            var takeableUsdOffers = bobClient.getOffersSortedByDate(SELL.name(), USD, false);
            assertEquals(0, takeableUsdOffers.size());

            trade = bobClient.getTrade(tradeId);
            assertEquals(alicesOffer.getAmount(), trade.getTradeAmountAsLong());
            verifyTakerDepositNotConfirmed(trade);
            logTrade(log, testInfo, "Alice's Maker/Buyer View", aliceClient.getTrade(tradeId));
            logTrade(log, testInfo, "Bob's Taker/Seller View", bobClient.getTrade(tradeId));
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(2)
    public void testPaymentMessagingPreconditions(final TestInfo testInfo) {
        try {
            // Alice is maker / btc seller, Bob is taker / btc buyer.
            // Verify payment sent and rcvd msgs are sent by the right peers:  buyer and seller.
            verifyPaymentSentMsgIsFromBtcBuyerPrecondition(log, aliceClient);
            verifyPaymentReceivedMsgIsFromBtcSellerPrecondition(log, bobClient);

            // Verify fiat payment sent and rcvd msgs cannot be sent before trade deposit tx is confirmed.
            verifyPaymentSentMsgDepositTxConfirmedPrecondition(log, bobClient);
            verifyPaymentReceivedMsgDepositTxConfirmedPrecondition(log, aliceClient);

            // Now generate the BTC block to confirm the taker deposit tx.
            genBtcBlocksThenWait(1, 2_500);
            waitForTakerDepositConfirmation(log, testInfo, bobClient, tradeId);

            // Verify the seller can only send a payment rcvd msg after the payment started msg.
            verifyPaymentReceivedMsgAfterPaymentSentMsgPrecondition(log, aliceClient);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(3)
    public void testBobsConfirmPaymentStarted(final TestInfo testInfo) {
        try {
            var trade = bobClient.getTrade(tradeId);
            verifyTakerDepositConfirmed(trade);
            bobClient.confirmPaymentStarted(tradeId);
            sleep(6_000);
            waitUntilBuyerSeesPaymentStartedMessage(log, testInfo, bobClient, tradeId);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(4)
    public void testAlicesConfirmPaymentReceived(final TestInfo testInfo) {
        try {
            waitUntilSellerSeesPaymentStartedMessage(log, testInfo, aliceClient, tradeId);

            var trade = aliceClient.getTrade(tradeId);
            aliceClient.confirmPaymentReceived(trade.getTradeId());
            sleep(3_000);
            trade = aliceClient.getTrade(tradeId);
            assertEquals(OFFER_FEE_PAID.name(), trade.getOffer().getState());
            EXPECTED_PROTOCOL_STATUS.setState(SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG)
                    .setPhase(PAYOUT_PUBLISHED)
                    .setPayoutPublished(true)
                    .setPaymentReceivedMessageSent(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Alice's view after confirming fiat payment received", trade);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(5)
    public void testBobsBtcWithdrawalToExternalAddress(final TestInfo testInfo) {
        try {
            genBtcBlocksThenWait(1, 1_000);

            var trade = bobClient.getTrade(tradeId);
            logTrade(log, testInfo, "Bob's view before withdrawing funds to external wallet", trade);
            String toAddress = bitcoinCli.getNewBtcAddress();
            bobClient.withdrawFunds(tradeId, toAddress, WITHDRAWAL_TX_MEMO);
            aliceClient.closeTrade(tradeId);
            genBtcBlocksThenWait(1, 1_000);
            trade = bobClient.getTrade(tradeId);
            EXPECTED_PROTOCOL_STATUS.setState(WITHDRAW_COMPLETED)
                    .setPhase(WITHDRAWN)
                    .setCompleted(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Alice's Maker/Buyer View (Done)", aliceClient.getTrade(tradeId));
            logTrade(log, testInfo, "Bob's Taker/Seller View (Done)", bobClient.getTrade(tradeId));
            logBalances(log, testInfo);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }
}
