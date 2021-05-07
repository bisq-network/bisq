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

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;



import bisq.apitest.method.offer.AbstractOfferTest;

// @Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AtomicTradeTestLoop extends AbstractOfferTest {

    @BeforeAll
    public static void setUp() {
        AbstractOfferTest.setUp();
        createAtomicBsqPaymentAccounts();
    }

    @Test
    @Order(1)
    public void testGetBalancesBeforeTrade() {
        AtomicTradeTest test = new AtomicTradeTest();
        runTradeLoop(test);
    }

    private void runTradeLoop(AtomicTradeTest test) {
        // TODO Fix wallet inconsistency bugs after 2nd trades.
        for (int tradeCount = 1; tradeCount <= 2; tradeCount++) {
            log.warn("================================ Trade # {} ================================", tradeCount);
            test.testGetBalancesBeforeTrade();

            test.testAliceCreateAtomicBuyOffer();
            genBtcBlocksThenWait(1, 8000);

            test.testBobTakesAtomicOffer();
            genBtcBlocksThenWait(1, 8000);

            test.testGetBalancesAfterTrade();
        }
    }
}
