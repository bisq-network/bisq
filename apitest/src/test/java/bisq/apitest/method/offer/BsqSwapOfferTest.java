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

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.BTC;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.OfferDirection.BUY;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BsqSwapOfferTest extends AbstractOfferTest {

    @BeforeAll
    public static void setUp() {
        AbstractOfferTest.setUp();
    }

    @BeforeEach
    public void generateBtcBlock() {
        genBtcBlocksThenWait(1, 2000);
    }

    @Test
    @Order(1)
    public void testGetBalancesBeforeCreateOffers() {
        var alicesBalances = aliceClient.getBalances();
        log.debug("Alice's Before Trade Balance:\n{}", formatBalancesTbls(alicesBalances));
        var bobsBalances = bobClient.getBalances();
        log.debug("Bob's Before Trade Balance:\n{}", formatBalancesTbls(bobsBalances));
    }

    @Test
    @Order(2)
    public void testAliceCreateBsqSwapBuyOffer1() {
        createBsqSwapOffer();
    }

    @Test
    @Order(3)
    public void testAliceCreateBsqSwapBuyOffer2() {
        createBsqSwapOffer();
    }

    @Test
    @Order(4)
    public void testAliceCreateBsqSwapBuyOffer3() {
        createBsqSwapOffer();
    }

    @Test
    @Order(5)
    public void testAliceCreateBsqSwapBuyOffer4() {
        createBsqSwapOffer();
    }

    @Test
    @Order(6)
    public void testGetMyBsqSwapOffers() {
        var offers = aliceClient.getMyBsqSwapBsqOffersSortedByDate();
        assertEquals(4, offers.size());
    }

    @Test
    @Order(7)
    public void testGetAvailableBsqSwapOffers() {
        var offers = bobClient.getBsqSwapOffersSortedByDate();
        assertEquals(4, offers.size());
    }

    @Test
    @Order(8)
    public void testGetBalancesAfterCreateOffers() {
        var alicesBalances = aliceClient.getBalances();
        log.debug("Alice's After Trade Balance:\n{}", formatBalancesTbls(alicesBalances));
        var bobsBalances = bobClient.getBalances();
        log.debug("Bob's After Trade Balance:\n{}", formatBalancesTbls(bobsBalances));
    }

    private void createBsqSwapOffer() {
        var bsqSwapOffer = aliceClient.createBsqSwapOffer(BUY.name(),
                1_000_000L,
                1_000_000L,
                "0.00005");
        log.debug("BsqSwap Sell BSQ (Buy BTC) OFFER:\n{}", bsqSwapOffer);
        var newOfferId = bsqSwapOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(BUY.name(), bsqSwapOffer.getDirection());
        assertEquals(5_000, bsqSwapOffer.getPrice());
        assertEquals(1_000_000L, bsqSwapOffer.getAmount());
        assertEquals(1_000_000L, bsqSwapOffer.getMinAmount());
        assertEquals(BSQ, bsqSwapOffer.getBaseCurrencyCode());
        assertEquals(BTC, bsqSwapOffer.getCounterCurrencyCode());

        testGetMyBsqSwapOffer(bsqSwapOffer);
        testGetBsqSwapOffer(bsqSwapOffer);
    }

    private void testGetMyBsqSwapOffer(OfferInfo bsqSwapOffer) {
        int numFetchAttempts = 0;
        while (true) {
            try {
                numFetchAttempts++;
                var fetchedBsqSwapOffer = aliceClient.getOffer(bsqSwapOffer.getId());
                assertEquals(bsqSwapOffer.getId(), fetchedBsqSwapOffer.getId());
                log.debug("Alice found her (my) new bsq swap offer on attempt # {}.", numFetchAttempts);
                break;
            } catch (Exception ex) {
                log.warn(ex.getMessage());

                if (numFetchAttempts >= 9)
                    fail(format("Alice giving up on fetching her (my) bsq swap offer after %d attempts.", numFetchAttempts), ex);

                sleep(1000);
            }
        }
    }

    private void testGetBsqSwapOffer(OfferInfo bsqSwapOffer) {
        int numFetchAttempts = 0;
        while (true) {
            try {
                numFetchAttempts++;
                var fetchedBsqSwapOffer = bobClient.getOffer(bsqSwapOffer.getId());
                assertEquals(bsqSwapOffer.getId(), fetchedBsqSwapOffer.getId());
                log.debug("Bob found new available bsq swap offer on attempt # {}.", numFetchAttempts);
                break;
            } catch (Exception ex) {
                log.warn(ex.getMessage());

                if (numFetchAttempts > 9)
                    fail(format("Bob gave up on fetching available bsq swap offer after %d attempts.", numFetchAttempts), ex);

                sleep(1000);
            }
        }
    }
}
