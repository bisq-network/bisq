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

import bisq.proto.grpc.BsqSwapOfferInfo;
import bisq.proto.grpc.BsqSwapTradeInfo;

import protobuf.BsqSwapTrade;

import java.util.ArrayList;
import java.util.List;

import lombok.Setter;
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



import bisq.apitest.method.offer.AbstractOfferTest;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BsqSwapTradeTest extends AbstractOfferTest {

    private static final String BISQ_FEE_CURRENCY_CODE = BSQ;

    // Long-running swap trade tests might want to check node logs for exceptions.
    @Setter
    private boolean checkForLoggedExceptions;

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
    public void testGetBalancesBeforeTrade() {
        var alicesBalances = aliceClient.getBalances();
        log.info("Alice's Before Trade Balance:\n{}", formatBalancesTbls(alicesBalances));
        var bobsBalances = bobClient.getBalances();
        log.info("Bob's Before Trade Balance:\n{}", formatBalancesTbls(bobsBalances));
    }

    @Test
    @Order(2)
    public void testAliceCreateBsqSwapBuyOffer() {
        var bsqSwapOffer = aliceClient.createBsqSwapOffer(BUY.name(),
                1_000_000L,
                1_000_000L,
                "0.00005",
                alicesBsqAcct.getId());
        log.debug("BsqSwap Sell BSQ (Buy BTC) OFFER:\n{}", bsqSwapOffer);
        var newOfferId = bsqSwapOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(BUY.name(), bsqSwapOffer.getDirection());
        assertEquals(5_000, bsqSwapOffer.getPrice());
        assertEquals(1_000_000L, bsqSwapOffer.getAmount());
        assertEquals(1_000_000L, bsqSwapOffer.getMinAmount());
        // assertEquals(alicesBsqAcct.getId(), atomicOffer.getMakerPaymentAccountId());
        assertEquals(BSQ, bsqSwapOffer.getBaseCurrencyCode());
        assertEquals(BTC, bsqSwapOffer.getCounterCurrencyCode());
    }

    @Test
    @Order(3)
    public void testBobTakesBsqSwapOffer() {
        var bsqSwapOffer = getAvailableBsqSwapOffer();
        var bsqSwapTradeInfo = bobClient.takeBsqSwapOffer(bsqSwapOffer.getId(),
                bobsBsqAcct.getId(),
                BISQ_FEE_CURRENCY_CODE);
        log.debug("Trade at t1: {}", bsqSwapTradeInfo);
        assertEquals(BsqSwapTrade.State.PREPARATION.name(), bsqSwapTradeInfo.getState());
        genBtcBlocksThenWait(1, 3_000);

        bsqSwapTradeInfo = getBsqSwapTrade(bsqSwapTradeInfo.getTradeId());
        log.debug("Trade at t2: {}", bsqSwapTradeInfo);
        assertEquals(BsqSwapTrade.State.COMPLETED.name(), bsqSwapTradeInfo.getState());
    }

    @Test
    @Order(4)
    public void testGetBalancesAfterTrade() {
        var alicesBalances = aliceClient.getBalances();
        log.info("Alice's After Trade Balance:\n{}", formatBalancesTbls(alicesBalances));
        var bobsBalances = bobClient.getBalances();
        log.info("Bob's After Trade Balance:\n{}", formatBalancesTbls(bobsBalances));
    }

    private BsqSwapOfferInfo getAvailableBsqSwapOffer() {
        List<BsqSwapOfferInfo> bsqSwapOffers = new ArrayList<>();
        int numFetchAttempts = 0;
        while (bsqSwapOffers.size() == 0) {
            bsqSwapOffers.addAll(bobClient.getBsqSwapOffers(BUY.name(), BSQ));
            numFetchAttempts++;
            if (bsqSwapOffers.size() == 0) {
                log.warn("No available bsq swap offers found after {} fetch attempts.", numFetchAttempts);
                if (numFetchAttempts > 9) {
                    if (checkForLoggedExceptions) {
                        printNodeExceptionMessages(log);
                    }
                    fail(format("Bob gave up on fetching available bsq swap offers after %d attempts.", numFetchAttempts));
                }
                sleep(1000);
            } else {
                assertEquals(1, bsqSwapOffers.size());
                log.debug("Bob found new available bsq swap offer on attempt # {}.", numFetchAttempts);
                break;
            }
        }
        var bsqSwapOffer = bobClient.getBsqSwapOffer(bsqSwapOffers.get(0).getId());
        assertEquals(bsqSwapOffers.get(0).getId(), bsqSwapOffer.getId());
        return bsqSwapOffer;
    }

    private BsqSwapTradeInfo getBsqSwapTrade(String tradeId) {
        int numFetchAttempts = 0;
        while (true) {
            try {
                numFetchAttempts++;
                return bobClient.getBsqSwapTrade(tradeId);
            } catch (Exception ex) {
                log.warn(ex.getMessage());
                if (numFetchAttempts > 9) {
                    if (checkForLoggedExceptions) {
                        printNodeExceptionMessages(log);
                    }
                    fail(format("Could not find new bsq swap trade after %d attempts.", numFetchAttempts));
                } else {
                    sleep(1000);
                }
            }
        }
    }
}
