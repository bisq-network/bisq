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

import bisq.proto.grpc.BsqSwapOfferInfo;

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
import static bisq.cli.TableFormat.formatBalancesTbls;
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
        createBsqSwapBsqPaymentAccounts();
    }

    @BeforeEach
    public void generateBtcBlock() {
        genBtcBlocksThenWait(1, 2000);
    }

    @Test
    @Order(1)
    public void testGetBalancesBeforeCreateOffers() {
        var alicesBalances = aliceClient.getBalances();
        log.info("Alice's Before Trade Balance:\n{}", formatBalancesTbls(alicesBalances));
        var bobsBalances = bobClient.getBalances();
        log.info("Bob's Before Trade Balance:\n{}", formatBalancesTbls(bobsBalances));
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

    private void createBsqSwapOffer() {
        var bsqSwapOffer = aliceClient.createBsqSwapOffer(BUY.name(),
                100_000_000L,
                100_000_000L,
                "0.00005",
                alicesBsqAcct.getId());
        log.info("BsqSwap Sell BSQ (Buy BTC) OFFER:\n{}", bsqSwapOffer);
        var newOfferId = bsqSwapOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(BUY.name(), bsqSwapOffer.getDirection());
        assertEquals(5_000, bsqSwapOffer.getPrice());
        assertEquals(100_000_000L, bsqSwapOffer.getAmount());
        assertEquals(100_000_000L, bsqSwapOffer.getMinAmount());
        // assertEquals(alicesBsqAcct.getId(), atomicOffer.getMakerPaymentAccountId());
        assertEquals(BSQ, bsqSwapOffer.getBaseCurrencyCode());
        assertEquals(BTC, bsqSwapOffer.getCounterCurrencyCode());

        testGetMyBsqSwapOffer(bsqSwapOffer);
        testGetBsqSwapOffer(bsqSwapOffer);
    }

    private void testGetMyBsqSwapOffer(BsqSwapOfferInfo bsqSwapOfferInfo) {
        int numFetchAttempts = 0;
        while (true) {
            try {
                numFetchAttempts++;
                var fetchedBsqSwapOffer = aliceClient.getMyBsqSwapOffer(bsqSwapOfferInfo.getId());
                assertEquals(bsqSwapOfferInfo.getId(), fetchedBsqSwapOffer.getId());
                log.info("Alice found her (my) new bsq swap offer on attempt # {}.", numFetchAttempts);
                break;
            } catch (Exception ex) {
                log.warn(ex.getMessage());

                if (numFetchAttempts >= 9)
                    fail(format("Alice giving up on fetching her (my) bsq swap offer after %d attempts.", numFetchAttempts), ex);

                sleep(1000);
            }
        }
    }

    private void testGetBsqSwapOffer(BsqSwapOfferInfo bsqSwapOfferInfo) {
        int numFetchAttempts = 0;
        while (true) {
            try {
                numFetchAttempts++;
                var fetchedBsqSwapOffer = bobClient.getBsqSwapOffer(bsqSwapOfferInfo.getId());
                assertEquals(bsqSwapOfferInfo.getId(), fetchedBsqSwapOffer.getId());
                log.info("Bob found new available bsq swap offer on attempt # {}.", numFetchAttempts);
                break;
            } catch (Exception ex) {
                log.warn(ex.getMessage());

                if (numFetchAttempts > 9)
                    fail(format("Bob gave up on fetching available bsq swap offer after %d attempts.", numFetchAttempts), ex);

                sleep(1000);
            }
        }
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
        log.info("Alice's After Trade Balance:\n{}", formatBalancesTbls(alicesBalances));
        var bobsBalances = bobClient.getBalances();
        log.info("Bob's After Trade Balance:\n{}", formatBalancesTbls(bobsBalances));
    }
}
