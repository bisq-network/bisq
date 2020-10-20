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

import protobuf.PaymentAccount;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static bisq.core.trade.Trade.Phase.DEPOSIT_CONFIRMED;
import static bisq.core.trade.Trade.Phase.DEPOSIT_PUBLISHED;
import static bisq.core.trade.Trade.Phase.FIAT_SENT;
import static bisq.core.trade.Trade.Phase.PAYOUT_PUBLISHED;
import static bisq.core.trade.Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG;
import static bisq.core.trade.Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN;
import static bisq.core.trade.Trade.State.SELLER_PUBLISHED_DEPOSIT_TX;
import static bisq.core.trade.Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.Offer.State.OFFER_FEE_PAID;
import static protobuf.OpenOffer.State.AVAILABLE;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TakeBuyBTCOfferTest extends AbstractTradeTest {

    // Alice is buyer, Bob is seller.

    private static String tradeId;

    private PaymentAccount alicesAccount;
    private PaymentAccount bobsAccount;

    @BeforeEach
    public void init() {
        alicesAccount = getDefaultPerfectDummyPaymentAccount(alicedaemon);
        bobsAccount = getDefaultPerfectDummyPaymentAccount(bobdaemon);
    }

    @Test
    @Order(1)
    public void testTakeAlicesBuyOffer() {
        try {
            var alicesOffer = createAliceOffer(alicesAccount, "buy", "usd", 12500000);
            var offerId = alicesOffer.getId();

            // Wait for Alice's AddToOfferBook task.
            // Wait times vary;  my logs show >= 2 second delay.
            sleep(3000);
            assertEquals(1, getOpenOffersCount(aliceStubs, "buy", "usd"));

            var trade = takeAlicesOffer(offerId, bobsAccount.getId());
            assertNotNull(trade);
            assertEquals(offerId, trade.getTradeId());
            // Cache the trade id for the other tests.
            tradeId = trade.getTradeId();

            genBtcBlocksThenWait(1, 2250);
            assertEquals(0, getOpenOffersCount(aliceStubs, "buy", "usd"));

            trade = getTrade(bobdaemon, trade.getTradeId());
            verifyExpectedTradeStateAndPhase(trade, SELLER_PUBLISHED_DEPOSIT_TX, DEPOSIT_PUBLISHED);

            genBtcBlocksThenWait(1, 2250);
            trade = getTrade(bobdaemon, trade.getTradeId());
            verifyExpectedTradeStateAndPhase(trade, DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN, DEPOSIT_CONFIRMED);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Disabled
    @Test
    @Order(2)
    public void testAlicesConfirmPaymentStarted() {
        try {
            var trade = getTrade(alicedaemon, tradeId);
            assertNotNull(trade);

            confirmPaymentStarted(alicedaemon, trade.getTradeId());
            sleep(3000);

            trade = getTrade(alicedaemon, tradeId);
            assertEquals(OFFER_FEE_PAID.name(), trade.getOffer().getState());
            verifyExpectedTradeStateAndPhase(trade, BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG, FIAT_SENT);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Disabled
    @Test
    @Order(3)
    public void testBobsConfirmPaymentReceived() {
        var trade = getTrade(bobdaemon, tradeId);
        assertNotNull(trade);

        confirmPaymentReceived(bobdaemon, trade.getTradeId());
        sleep(3000);

        trade = getTrade(bobdaemon, tradeId);
        // TODO is this a bug?  Why is offer.state == available?
        assertEquals(AVAILABLE.name(), trade.getOffer().getState());
        verifyExpectedTradeStateAndPhase(trade, SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG, PAYOUT_PUBLISHED);
    }
}
