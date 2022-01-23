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

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static bisq.core.trade.model.bisq_v1.Trade.Phase.PAYOUT_PUBLISHED;
import static bisq.core.trade.model.bisq_v1.Trade.State.BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG;
import static bisq.core.trade.model.bisq_v1.Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.Offer.State.OFFER_FEE_PAID;
import static protobuf.OfferDirection.SELL;



import bisq.apitest.method.offer.AbstractOfferTest;

// https://github.com/ghubstan/bisq/blob/master/cli/src/main/java/bisq/cli/TradeFormat.java

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TakeBuyBSQOfferTest extends AbstractTradeTest {

    // Alice is maker / bsq buyer (btc seller), Bob is taker / bsq seller (btc buyer).

    // Maker and Taker fees are in BSQ.
    private static final String TRADE_FEE_CURRENCY_CODE = BSQ;

    @BeforeAll
    public static void setUp() {
        AbstractOfferTest.setUp();
        EXPECTED_PROTOCOL_STATUS.init();
    }

    @Test
    @Order(1)
    public void testTakeAlicesSellBTCForBSQOffer(final TestInfo testInfo) {
        try {
            // Alice is going to BUY BSQ, but the Offer direction = SELL because it is a
            // BTC trade;  Alice will SELL BTC for BSQ.  Bob will send Alice BSQ.
            // Confused me, but just need to remember there are only BTC offers.
            var btcTradeDirection = SELL.name();
            var alicesOffer = aliceClient.createFixedPricedOffer(btcTradeDirection,
                    BSQ,
                    15_000_000L,
                    7_500_000L,
                    "0.000035",   // FIXED PRICE IN BTC (satoshis) FOR 1 BSQ
                    getDefaultBuyerSecurityDepositAsPercent(),
                    alicesLegacyBsqAcct.getId(),
                    TRADE_FEE_CURRENCY_CODE);
            log.debug("ALICE'S BUY BSQ (SELL BTC) OFFER:\n{}", toOfferTable.apply(alicesOffer));
            genBtcBlocksThenWait(1, 5000);
            var offerId = alicesOffer.getId();
            assertFalse(alicesOffer.getIsCurrencyForMakerFeeBtc());

            var alicesBsqOffers = aliceClient.getMyCryptoCurrencyOffers(btcTradeDirection, BSQ);
            assertEquals(1, alicesBsqOffers.size());

            var trade = takeAlicesOffer(offerId,
                    bobsLegacyBsqAcct.getId(),
                    TRADE_FEE_CURRENCY_CODE,
                    false);
            assertNotNull(trade);
            assertEquals(offerId, trade.getTradeId());
            assertFalse(trade.getIsCurrencyForTakerFeeBtc());
            // Cache the trade id for the other tests.
            tradeId = trade.getTradeId();

            genBtcBlocksThenWait(1, 2_500);
            alicesBsqOffers = aliceClient.getMyCryptoCurrencyOffersSortedByDate(BSQ);
            assertEquals(0, alicesBsqOffers.size());

            waitForDepositConfirmation(log, testInfo, bobClient, trade.getTradeId());
            genBtcBlocksThenWait(1, 2_500);

            trade = bobClient.getTrade(tradeId);
            verifyTakerDepositConfirmed(trade);
            logTrade(log, testInfo, "Alice's Maker/Buyer View (Payment Sent)", aliceClient.getTrade(tradeId));
            logTrade(log, testInfo, "Bob's Taker/Seller View (Payment Sent)", bobClient.getTrade(tradeId));
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(2)
    public void testBobsConfirmPaymentStarted(final TestInfo testInfo) {
        try {
            var trade = bobClient.getTrade(tradeId);
            verifyTakerDepositConfirmed(trade);
            sendBsqPayment(log, bobClient, trade);
            genBtcBlocksThenWait(1, 2_500);
            bobClient.confirmPaymentStarted(trade.getTradeId());
            sleep(6000);
            waitForBuyerSeesPaymentInitiatedMessage(log, testInfo, bobClient, tradeId);
            logTrade(log, testInfo, "Alice's Maker/Buyer View (Payment Sent)", aliceClient.getTrade(tradeId));
            logTrade(log, testInfo, "Bob's Taker/Seller View (Payment Sent)", bobClient.getTrade(tradeId));
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(3)
    public void testAlicesConfirmPaymentReceived(final TestInfo testInfo) {
        try {
            waitForSellerSeesPaymentInitiatedMessage(log, testInfo, aliceClient, tradeId);
            sleep(2_000);
            var trade = aliceClient.getTrade(tradeId);
            verifyBsqPaymentHasBeenReceived(log, aliceClient, trade);
            aliceClient.confirmPaymentReceived(trade.getTradeId());
            sleep(3_000);
            trade = aliceClient.getTrade(tradeId);
            assertEquals(OFFER_FEE_PAID.name(), trade.getOffer().getState());
            EXPECTED_PROTOCOL_STATUS.setState(SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG)
                    .setPhase(PAYOUT_PUBLISHED)
                    .setPayoutPublished(true)
                    .setFiatReceived(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Alice's Maker/Buyer View (Payment Received)", aliceClient.getTrade(tradeId));
            logTrade(log, testInfo, "Bob's Taker/Seller View (Payment Received)", bobClient.getTrade(tradeId));
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(4)
    public void testKeepFunds(final TestInfo testInfo) {
        try {
            genBtcBlocksThenWait(1, 1_000);

            var trade = bobClient.getTrade(tradeId);
            logTrade(log, testInfo, "Alice's view before closing trade", trade);

            aliceClient.closeTrade(tradeId);
            bobClient.closeTrade(tradeId);
            genBtcBlocksThenWait(1, 1_000);

            trade = bobClient.getTrade(tradeId);
            EXPECTED_PROTOCOL_STATUS.setState(BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG).setPhase(PAYOUT_PUBLISHED);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Alice's Maker/Buyer View (Done)", aliceClient.getTrade(tradeId));
            logTrade(log, testInfo, "Bob's Taker/Seller View (Done)", bobClient.getTrade(tradeId));
            logBalances(log, testInfo);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }
}
