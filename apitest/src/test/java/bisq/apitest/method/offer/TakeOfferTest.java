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

package bisq.apitest.method.offer;

import bisq.proto.grpc.OfferInfo;

import io.grpc.StatusRuntimeException;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static bisq.core.trade.Trade.Phase.DEPOSIT_CONFIRMED;
import static bisq.core.trade.Trade.Phase.DEPOSIT_PUBLISHED;
import static bisq.core.trade.Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN;
import static bisq.core.trade.Trade.State.SELLER_PUBLISHED_DEPOSIT_TX;
import static bisq.core.trade.Trade.TradePeriodState.FIRST_HALF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TakeOfferTest extends AbstractOfferTest {

    @Test
    @Order(1)
    public void testTakeAlicesBuyOffer() {
        try {
            // Alice is buyer, Bob is seller.
            var aliceAccount = getDefaultPerfectDummyPaymentAccount(alicedaemon);
            var aliceOffer = createAliceOffer(aliceAccount, "buy", "usd", 12500000);
            var offerId = aliceOffer.getId();

            // Wait for Alice's AddToOfferBook task.
            // Wait times vary;  my logs show >= 2 second delay.
            sleep(2250);
            List<OfferInfo> alicesOpenOffers = getOffersSortedByDate("buy", "usd");
            assertEquals(1, alicesOpenOffers.size());

            var bobAccount = getDefaultPerfectDummyPaymentAccount(bobdaemon);
            var trade = takeAlicesOffer(offerId, bobAccount.getId());
            assertNotNull(trade);
            assertEquals(offerId, trade.getTradeId());

            bitcoinCli.generateBlocks(1);
            sleep(2250);

            alicesOpenOffers = getOffersSortedByDate("buy", "usd");
            assertEquals(0, alicesOpenOffers.size());

            trade = getTrade(bobdaemon, trade.getTradeId());
            assertEquals(SELLER_PUBLISHED_DEPOSIT_TX.name(), trade.getState());
            assertEquals(DEPOSIT_PUBLISHED.name(), trade.getPhase());
            assertEquals(FIRST_HALF.name(), trade.getTradePeriodState());

            bitcoinCli.generateBlocks(1);
            sleep(2250);

            trade = getTrade(bobdaemon, trade.getTradeId());
            assertEquals(DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN.name(), trade.getState());
            assertEquals(DEPOSIT_CONFIRMED.name(), trade.getPhase());
            assertEquals(FIRST_HALF.name(), trade.getTradePeriodState());

        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }
}
