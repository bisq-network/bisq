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

import bisq.proto.grpc.AtomicOfferInfo;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import static protobuf.OfferPayload.Direction.BUY;

// @Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AtomicOfferTest extends AbstractOfferTest {

    @BeforeAll
    public static void setUp() {
        AbstractOfferTest.setUp();
        createAtomicBsqPaymentAccounts();
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
    public void testAliceCreateAtomicBuyOffer1() {
        createAtomicOffer();
    }

    @Test
    @Order(3)
    public void testAliceCreateAtomicBuyOffer2() {
        createAtomicOffer();
    }

    @Test
    @Order(4)
    public void testAliceCreateAtomicBuyOffer3() {
        createAtomicOffer();
    }

    @Test
    @Order(5)
    public void testAliceCreateAtomicBuyOffer4() {
        createAtomicOffer();
    }

    private void createAtomicOffer() {
        var atomicOffer = aliceClient.createAtomicOffer(BUY.name(),
                100_000_000L,
                100_000_000L,
                "0.00005",
                alicesBsqAcct.getId());
        log.info("Atomic Sell BSQ (Buy BTC) OFFER:\n{}", atomicOffer);
        var newOfferId = atomicOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(BUY.name(), atomicOffer.getDirection());
        assertEquals(5_000, atomicOffer.getPrice());
        assertEquals(100_000_000L, atomicOffer.getAmount());
        assertEquals(100_000_000L, atomicOffer.getMinAmount());
        // assertEquals(alicesBsqAcct.getId(), atomicOffer.getMakerPaymentAccountId());
        assertEquals(BSQ, atomicOffer.getBaseCurrencyCode());
        assertEquals(BTC, atomicOffer.getCounterCurrencyCode());

        testGetMyAtomicOffer(atomicOffer);
        testGetAtomicOffer(atomicOffer);
    }

    private void testGetMyAtomicOffer(AtomicOfferInfo atomicOffer) {
        int numFetchAttempts = 0;
        while (true) {
            try {
                numFetchAttempts++;
                var fetchedAtomicOffer = aliceClient.getMyAtomicOffer(atomicOffer.getId());
                assertEquals(atomicOffer.getId(), fetchedAtomicOffer.getId());
                log.info("Alice found her (my) new atomic offer on attempt # {}.", numFetchAttempts);
                break;
            } catch (Exception ex) {
                log.warn(ex.getMessage());

                if (numFetchAttempts >= 9)
                    fail(format("Alice giving up on fetching her (my) atomic offer after %d attempts.", numFetchAttempts), ex);

                sleep(1000);
            }
        }
    }

    private void testGetAtomicOffer(AtomicOfferInfo atomicOffer) {
        int numFetchAttempts = 0;
        while (true) {
            try {
                numFetchAttempts++;
                var fetchedAtomicOffer = bobClient.getAtomicOffer(atomicOffer.getId());
                assertEquals(atomicOffer.getId(), fetchedAtomicOffer.getId());
                log.info("Bob found new available atomic offer on attempt # {}.", numFetchAttempts);
                break;
            } catch (Exception ex) {
                log.warn(ex.getMessage());

                if (numFetchAttempts > 9)
                    fail(format("Bob gave up on fetching available atomic offer after %d attempts.", numFetchAttempts), ex);

                sleep(1000);
            }
        }
    }

    @Test
    @Order(6)
    public void testGetMyAtomicOffers() {
        var offers = aliceClient.getMyAtomicBsqOffersSortedByDate();
        assertEquals(4, offers.size());
    }

    @Test
    @Order(7)
    public void testGetAvailableAtomicOffers() {
        var offers = bobClient.getAtomicOffersSortedByDate();
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
