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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static bisq.cli.CurrencyFormat.formatSatoshis;
import static bisq.core.trade.Trade.Phase.DEPOSIT_CONFIRMED;
import static bisq.core.trade.Trade.Phase.DEPOSIT_PUBLISHED;
import static bisq.core.trade.Trade.Phase.FIAT_SENT;
import static bisq.core.trade.Trade.Phase.PAYOUT_PUBLISHED;
import static bisq.core.trade.Trade.State.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.Offer.State.OFFER_FEE_PAID;
import static protobuf.OpenOffer.State.AVAILABLE;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TakeBuyBTCOfferTest extends AbstractTradeTest {

    // Alice is buyer, Bob is seller.

    @Test
    @Order(1)
    public void testTakeAlicesBuyOffer(final TestInfo testInfo) {
        try {
            var alicesOffer = createAliceOffer(alicesDummyAcct,
                    "buy",
                    "usd",
                    12500000);
            var offerId = alicesOffer.getId();

            // Wait for Alice's AddToOfferBook task.
            // Wait times vary;  my logs show >= 2 second delay.
            sleep(3000);
            assertEquals(1, getOpenOffersCount(aliceStubs, "buy", "usd"));

            var trade = takeAlicesOffer(offerId, bobsDummyAcct.getId());
            assertNotNull(trade);
            assertEquals(offerId, trade.getTradeId());
            // Cache the trade id for the other tests.
            tradeId = trade.getTradeId();

            genBtcBlocksThenWait(1, 2250);
            assertEquals(0, getOpenOffersCount(aliceStubs, "buy", "usd"));

            trade = getTrade(bobdaemon, trade.getTradeId());
            EXPECTED_PROTOCOL_STATUS.setState(SELLER_PUBLISHED_DEPOSIT_TX)
                    .setPhase(DEPOSIT_PUBLISHED)
                    .setDepositPublished(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Bob's view after taking offer and sending deposit", trade);

            genBtcBlocksThenWait(1, 2250);
            trade = getTrade(bobdaemon, trade.getTradeId());
            EXPECTED_PROTOCOL_STATUS.setState(DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN)
                    .setPhase(DEPOSIT_CONFIRMED)
                    .setDepositConfirmed(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Bob's view after deposit is confirmed", trade);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(2)
    public void testAlicesConfirmPaymentStarted(final TestInfo testInfo) {
        try {
            var trade = getTrade(alicedaemon, tradeId);
            confirmPaymentStarted(alicedaemon, trade.getTradeId());
            sleep(3000);

            trade = getTrade(alicedaemon, tradeId);
            assertEquals(OFFER_FEE_PAID.name(), trade.getOffer().getState());
            EXPECTED_PROTOCOL_STATUS.setState(BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG)
                    .setPhase(FIAT_SENT)
                    .setFiatSent(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Alice's view after confirming fiat payment sent", trade);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(3)
    public void testBobsConfirmPaymentReceived(final TestInfo testInfo) {
        var trade = getTrade(bobdaemon, tradeId);
        confirmPaymentReceived(bobdaemon, trade.getTradeId());
        sleep(3000);

        trade = getTrade(bobdaemon, tradeId);
        // Note: offer.state == available
        assertEquals(AVAILABLE.name(), trade.getOffer().getState());
        EXPECTED_PROTOCOL_STATUS.setState(SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG)
                .setPhase(PAYOUT_PUBLISHED)
                .setPayoutPublished(true)
                .setFiatReceived(true);
        verifyExpectedProtocolStatus(trade);
        logTrade(log, testInfo, "Bob's view after confirming fiat payment received", trade);
    }

    @Test
    @Order(4)
    public void testAlicesKeepFunds(final TestInfo testInfo) {
        genBtcBlocksThenWait(1, 2250);

        var trade = getTrade(alicedaemon, tradeId);
        logTrade(log, testInfo, "Alice's view before keeping funds", trade);

        keepFunds(alicedaemon, tradeId);

        genBtcBlocksThenWait(1, 2250);

        trade = getTrade(alicedaemon, tradeId);
        EXPECTED_PROTOCOL_STATUS.setState(BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG)
                .setPhase(PAYOUT_PUBLISHED);
        verifyExpectedProtocolStatus(trade);
        logTrade(log, testInfo, "Alice's view after keeping funds", trade);
        log.info("{} Alice's current available balance: {} BTC",
                testName(testInfo),
                formatSatoshis(getBalance(alicedaemon)));
    }
}
