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

package bisq.apitest.scenario;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;

import static java.lang.System.getenv;



import bisq.apitest.method.offer.AbstractOfferTest;
import bisq.apitest.method.trade.BsqSwapBuyBtcTradeTest;

@EnabledIf("envLongRunningTestEnabled")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LongRunningBsqSwapTest extends AbstractOfferTest {

    private static final int MAX_SWAPS = 250;

    @BeforeAll
    public static void setUp() {
        AbstractOfferTest.setUp();
    }

    @Test
    @Order(1)
    public void testBsqSwaps() {
        // TODO Fix wallet inconsistency bugs after N(?) trades.
        BsqSwapBuyBtcTradeTest test = new BsqSwapBuyBtcTradeTest();
        test.setCheckForLoggedExceptions(true);

        for (int swapCount = 1; swapCount <= MAX_SWAPS; swapCount++) {
            log.info("Beginning BSQ Swap # {}", swapCount);

            test.testGetBalancesBeforeTrade();

            test.testAliceCreateBsqSwapBuyBtcOffer();
            genBtcBlocksThenWait(1, 8_000);

            test.testBobTakesBsqSwapOffer();
            genBtcBlocksThenWait(1, 8_000);

            test.testGetBalancesAfterTrade();
            log.info("Finished  BSQ Swap # {}", swapCount);
        }
    }

    protected static boolean envLongRunningTestEnabled() {
        String envName = "LONG_RUNNING_BSQ_SWAP_TEST_ENABLED";
        String envX = getenv(envName);
        if (envX != null) {
            log.info("Enabled, found {}.", envName);
            return true;
        } else {
            log.info("Skipped, no environment variable {} defined.", envName);
            log.info("To enable on Mac OS or Linux:"
                    + "\tIf running in terminal, export LONG_RUNNING_BSQ_SWAP_TEST_ENABLED=true in bash shell."
                    + "\tIf running in Intellij, set LONG_RUNNING_BSQ_SWAP_TEST_ENABLED=true in launcher's Environment variables field.");
            return false;
        }
    }
}
