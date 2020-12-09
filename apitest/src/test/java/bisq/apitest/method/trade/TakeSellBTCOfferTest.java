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

import bisq.proto.grpc.BtcBalanceInfo;

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
import static bisq.core.trade.Trade.Phase.*;
import static bisq.core.trade.Trade.State.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.Offer.State.OFFER_FEE_PAID;
import static protobuf.OpenOffer.State.AVAILABLE;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TakeSellBTCOfferTest extends AbstractTradeTest {

    // Alice is seller, Bob is buyer.

    // Maker and Taker fees are in BTC.
    private static final String TRADE_FEE_CURRENCY_CODE = "btc";

    @Test
    @Order(1)
    public void testTakeAlicesSellOffer(final TestInfo testInfo) {
        try {
            var alicesOffer = createAliceOffer(alicesDummyAcct,
                    "sell",
                    "usd",
                    12500000,
                    TRADE_FEE_CURRENCY_CODE);
            var offerId = alicesOffer.getId();
            assertTrue(alicesOffer.getIsCurrencyForMakerFeeBtc());

            // Wait for Alice's AddToOfferBook task.
            // Wait times vary;  my logs show >= 2 second delay, but taking sell offers
            // seems to require more time to prepare.
            sleep(3000); // TODO loop instead of hard code wait time
            assertEquals(1, getOpenOffersCount(bobStubs, "sell", "usd"));

            var trade = takeAlicesOffer(offerId, bobsDummyAcct.getId(), TRADE_FEE_CURRENCY_CODE);
            assertNotNull(trade);
            assertEquals(offerId, trade.getTradeId());
            assertTrue(trade.getIsCurrencyForTakerFeeBtc());
            // Cache the trade id for the other tests.
            tradeId = trade.getTradeId();

            genBtcBlocksThenWait(1, 4000);
            assertEquals(0, getOpenOffersCount(bobStubs, "sell", "usd"));

            trade = getTrade(bobdaemon, trade.getTradeId());
            EXPECTED_PROTOCOL_STATUS.setState(BUYER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG)
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
    public void testBobsConfirmPaymentStarted(final TestInfo testInfo) {
        try {
            var trade = getTrade(bobdaemon, tradeId);
            confirmPaymentStarted(bobdaemon, trade.getTradeId());
            sleep(3000);

            trade = getTrade(bobdaemon, tradeId);
            // Note: offer.state == available
            assertEquals(AVAILABLE.name(), trade.getOffer().getState());
            EXPECTED_PROTOCOL_STATUS.setState(BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG)
                    .setPhase(FIAT_SENT)
                    .setFiatSent(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Bob's view after confirming fiat payment sent", trade);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(3)
    public void testAlicesConfirmPaymentReceived(final TestInfo testInfo) {
        var trade = getTrade(alicedaemon, tradeId);
        confirmPaymentReceived(alicedaemon, trade.getTradeId());
        sleep(3000);

        trade = getTrade(alicedaemon, tradeId);
        assertEquals(OFFER_FEE_PAID.name(), trade.getOffer().getState());
        EXPECTED_PROTOCOL_STATUS.setState(SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG)
                .setPhase(PAYOUT_PUBLISHED)
                .setPayoutPublished(true)
                .setFiatReceived(true);
        verifyExpectedProtocolStatus(trade);
        logTrade(log, testInfo, "Alice's view after confirming fiat payment received", trade);
    }

    @Test
    @Order(4)
    public void testBobsBtcWithdrawalToExternalAddress(final TestInfo testInfo) {
        genBtcBlocksThenWait(1, 2250);

        var trade = getTrade(bobdaemon, tradeId);
        logTrade(log, testInfo, "Bob's view before withdrawing funds to external wallet", trade);

        String toAddress = bitcoinCli.getNewBtcAddress();
        withdrawFunds(bobdaemon, tradeId, toAddress, "to whom it may concern");

        genBtcBlocksThenWait(1, 2250);

        trade = getTrade(bobdaemon, tradeId);
        EXPECTED_PROTOCOL_STATUS.setState(WITHDRAW_COMPLETED)
                .setPhase(WITHDRAWN)
                .setWithdrawn(true);
        verifyExpectedProtocolStatus(trade);
        logTrade(log, testInfo, "Bob's view after withdrawing funds to external wallet", trade);
        BtcBalanceInfo currentBalance = getBtcBalances(bobdaemon);
        log.debug("{} Bob's current available balance: {} BTC",
                testName(testInfo),
                formatSatoshis(currentBalance.getAvailableBalance()));
    }
}
