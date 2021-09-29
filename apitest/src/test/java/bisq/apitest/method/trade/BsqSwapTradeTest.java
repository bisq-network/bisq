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
    }

    @Test
    @Order(3)
    public void testBobTakesBsqSwapOffer() {
        var bsqSwapOffer = getAvailableBsqSwapOffer();

        var bsqSwapTradeInfo = bobClient.takeBsqSwapOffer(bsqSwapOffer.getId(),
                bobsBsqAcct.getId(),
                BISQ_FEE_CURRENCY_CODE);
        log.info("Trade at t1: {}", bsqSwapTradeInfo);
        assertEquals(BsqSwapTrade.State.PREPARATION.name(), bsqSwapTradeInfo.getState());
        genBtcBlocksThenWait(1, 3000);

        bsqSwapTradeInfo = getBsqSwapTrade(bsqSwapTradeInfo.getTradeId());
        log.info("Trade at t2: {}", bsqSwapTradeInfo);
        assertEquals(BsqSwapTrade.State.TX_CONFIRMED.name(), bsqSwapTradeInfo.getState());
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
        List<BsqSwapOfferInfo> bsqSwapOfferInfos = new ArrayList<>();
        int numFetchAttempts = 0;
        while (bsqSwapOfferInfos.size() == 0) {
            bsqSwapOfferInfos.addAll(bobClient.getBsqSwapOffers(BUY.name(), "BSQ"));
            numFetchAttempts++;
            if (bsqSwapOfferInfos.size() == 0) {
                log.warn("No available bsq swap offer found after {} fetch attempts.", numFetchAttempts);
                if (numFetchAttempts > 9) {
                    fail(format("Bob gave up on fetching available bsq swap offer after %d attempts.", numFetchAttempts));
                }
                sleep(1000);
            } else {
                assertEquals(1, bsqSwapOfferInfos.size());
                log.info("Bob found new available bsq swap offer on attempt # {}.", numFetchAttempts);
                break;
            }
        }
        // Test api's getBsqSwapOffer(id).
        var bsqSwapOffer = bobClient.getBsqSwapOffer(bsqSwapOfferInfos.get(0).getId());
        assertEquals(bsqSwapOfferInfos.get(0).getId(), bsqSwapOffer.getId());
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
                    fail(format("Could not find new bsq swap trade after %d attempts.", numFetchAttempts));
                } else {
                    sleep(1000);
                }
            }
        }
    }
}
