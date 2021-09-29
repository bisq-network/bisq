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

import bisq.proto.grpc.AtomicOfferInfo;
import bisq.proto.grpc.AtomicTradeInfo;

import protobuf.AtomicTrade;

import java.util.ArrayList;
import java.util.List;

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



import bisq.apitest.method.offer.AbstractOfferTest;

// @Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BsqSwapTradeTest extends AbstractOfferTest {

    private static final String BISQ_FEE_CURRENCY_CODE = BSQ;

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
    public void testGetBalancesBeforeTrade() {
        var alicesBalances = aliceClient.getBalances();
        log.info("Alice's Before Trade Balance:\n{}", formatBalancesTbls(alicesBalances));
        var bobsBalances = bobClient.getBalances();
        log.info("Bob's Before Trade Balance:\n{}", formatBalancesTbls(bobsBalances));
    }

    @Test
    @Order(2)
    public void testAliceCreateAtomicBuyOffer() {
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
    }

    @Test
    @Order(3)
    public void testBobTakesAtomicOffer() {
        var atomicOffer = getAvailableAtomicOffer();

        var atomicTrade = bobClient.takeAtomicOffer(atomicOffer.getId(),
                bobsBsqAcct.getId(),
                BISQ_FEE_CURRENCY_CODE);
        log.info("Trade at t1: {}", atomicTrade);
        assertEquals(AtomicTrade.State.PREPARATION.name(), atomicTrade.getState());
        genBtcBlocksThenWait(1, 3000);

        atomicTrade = getAtomicTrade(atomicTrade.getTradeId());
        log.info("Trade at t2: {}", atomicTrade);
        assertEquals(AtomicTrade.State.TX_CONFIRMED.name(), atomicTrade.getState());
    }

    @Test
    @Order(4)
    public void testGetBalancesAfterTrade() {
        var alicesBalances = aliceClient.getBalances();
        log.info("Alice's After Trade Balance:\n{}", formatBalancesTbls(alicesBalances));
        var bobsBalances = bobClient.getBalances();
        log.info("Bob's After Trade Balance:\n{}", formatBalancesTbls(bobsBalances));
    }

    private AtomicOfferInfo getAvailableAtomicOffer() {
        List<AtomicOfferInfo> atomicOffers = new ArrayList<>();
        int numFetchAttempts = 0;
        while (atomicOffers.size() == 0) {
            atomicOffers.addAll(bobClient.getAtomicOffers(BUY.name(), "BSQ"));
            numFetchAttempts++;
            if (atomicOffers.size() == 0) {
                log.warn("No available atomic offer found after {} fetch attempts.", numFetchAttempts);
                if (numFetchAttempts > 9) {
                    fail(format("Bob gave up on fetching available atomic offer after %d attempts.", numFetchAttempts));
                }
                sleep(1000);
            } else {
                assertEquals(1, atomicOffers.size());
                log.info("Bob found new available atomic offer on attempt # {}.", numFetchAttempts);
                break;
            }
        }
        // Test api's getAtomicOffer(id).
        var atomicOffer = bobClient.getAtomicOffer(atomicOffers.get(0).getId());
        assertEquals(atomicOffers.get(0).getId(), atomicOffer.getId());
        return atomicOffer;
    }

    private AtomicTradeInfo getAtomicTrade(String tradeId) {
        int numFetchAttempts = 0;
        while (true) {
            try {
                numFetchAttempts++;
                return bobClient.getAtomicTrade(tradeId);
            } catch (Exception ex) {
                log.warn(ex.getMessage());
                if (numFetchAttempts > 9) {
                    fail(format("Could not find new atomic trade after %d attempts.", numFetchAttempts));
                } else {
                    sleep(1000);
                }
            }
        }
    }
}
