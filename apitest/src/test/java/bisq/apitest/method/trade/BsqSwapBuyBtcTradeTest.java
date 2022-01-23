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

import bisq.proto.grpc.TradeInfo;

import protobuf.OfferDirection;

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
import static bisq.core.offer.OfferDirection.BUY;
import static bisq.proto.grpc.GetOfferCategoryReply.OfferCategory.BSQ_SWAP;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.BsqSwapTrade.State.COMPLETED;
import static protobuf.BsqSwapTrade.State.PREPARATION;



import bisq.apitest.method.offer.AbstractOfferTest;
import bisq.cli.GrpcClient;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BsqSwapBuyBtcTradeTest extends AbstractTradeTest {

    // Long-running swap trade tests might want to check node logs for exceptions.
    @Setter
    private boolean checkForLoggedExceptions;

    @BeforeAll
    public static void setUp() {
        AbstractOfferTest.setUp();
    }

    @BeforeEach
    public void generateBtcBlock() {
        genBtcBlocksThenWait(1, 2_000);
    }

    @Test
    @Order(1)
    public void testGetBalancesBeforeTrade() {
        var alicesBalances = aliceClient.getBalances();
        log.debug("Alice's Before Trade Balance:\n{}", formatBalancesTbls(alicesBalances));
        var bobsBalances = bobClient.getBalances();
        log.debug("Bob's Before Trade Balance:\n{}", formatBalancesTbls(bobsBalances));
    }

    @Test
    @Order(2)
    public void testAliceCreateBsqSwapBuyBtcOffer() {
        // Alice buys BTC, pays trade fee.  Bob (BTC seller) pays miner tx fee.
        var mySwapOffer = aliceClient.createBsqSwapOffer(OfferDirection.BUY.name(),
                1_000_000L,     // 0.01 BTC
                1_000_000L,
                "0.00005");
        log.debug("Pending BsqSwap Sell BSQ (Buy BTC) OFFER:\n{}", toOfferTable.apply(mySwapOffer));
        var newOfferId = mySwapOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(OfferDirection.BUY.name(), mySwapOffer.getDirection());
        assertEquals(5_000, mySwapOffer.getPrice());
        assertEquals(1_000_000L, mySwapOffer.getAmount());
        assertEquals(1_000_000L, mySwapOffer.getMinAmount());
        assertEquals(BSQ, mySwapOffer.getBaseCurrencyCode());
        assertEquals(BTC, mySwapOffer.getCounterCurrencyCode());

        genBtcBlocksThenWait(1, 2_500);

        mySwapOffer = aliceClient.getOffer(newOfferId);
        log.debug("My fetched BsqSwap Sell BSQ (Buy BTC) OFFER:\n{}", toOfferTable.apply(mySwapOffer));
        assertNotEquals(0, mySwapOffer.getMakerFee());

        runCliGetOffer(newOfferId);
    }

    @Test
    @Order(3)
    public void testBobTakesBsqSwapOffer() {
        var availableSwapOffer = getAvailableBsqSwapOffer(bobClient, BUY, true);

        // Before sending a TakeOfferRequest, the CLI needs to know what kind of Offer
        // it is taking (v1 or BsqSwap).  Only BSQ swap offers can be taken with a
        // single offerId parameter.  Taking v1 offers requires an additional
        // paymentAccountId param.  The test case knows what kind of offer is being taken,
        // but we test the gRPC GetOfferCategory service here.
        var availableOfferCategory = bobClient.getAvailableOfferCategory(availableSwapOffer.getId());
        assertTrue(availableOfferCategory.equals(BSQ_SWAP));

        sleep(3_000);

        var swapTrade = bobClient.takeBsqSwapOffer(availableSwapOffer.getId());
        tradeId = swapTrade.getTradeId(); // Cache the tradeId for following test case(s).
        log.debug("BsqSwap Trade at PREPARATION:\n{}", toTradeDetailTable.apply(swapTrade));
        assertEquals(PREPARATION.name(), swapTrade.getState());
        genBtcBlocksThenWait(1, 3_000);

        swapTrade = getBsqSwapTrade(bobClient, tradeId);
        log.debug("BsqSwap Trade at COMPLETION:\n{}", toTradeDetailTable.apply(swapTrade));
        assertEquals(COMPLETED.name(), swapTrade.getState());

        runCliGetClosedTrades();
    }

    @Test
    @Order(4)
    public void testCompletedSwapTxConfirmations() {
        sleep(2_000); // Wait for TX confirmation to happen on node.

        var alicesTrade = getBsqSwapTrade(aliceClient, tradeId);
        log.debug("Alice's BsqSwap Trade at COMPLETION:\n{}", toTradeDetailTable.apply(alicesTrade));
        assertEquals(1, alicesTrade.getBsqSwapTradeInfo().getNumConfirmations());

        var bobsTrade = getBsqSwapTrade(bobClient, tradeId);
        log.debug("Bob's BsqSwap Trade at COMPLETION:\n{}", toTradeDetailTable.apply(bobsTrade));
        assertEquals(1, bobsTrade.getBsqSwapTradeInfo().getNumConfirmations());

        genBtcBlocksThenWait(1, 2_000);

        bobsTrade = getBsqSwapTrade(bobClient, tradeId);
        log.debug("Bob's BsqSwap Trade at COMPLETION:\n{}", toTradeDetailTable.apply(bobsTrade));
        assertEquals(2, bobsTrade.getBsqSwapTradeInfo().getNumConfirmations());

        runCliGetClosedTrades();
    }

    @Test
    @Order(5)
    public void testGetBalancesAfterTrade() {
        var alicesBalances = aliceClient.getBalances();
        log.debug("Alice's After Trade Balance:\n{}", formatBalancesTbls(alicesBalances));
        var bobsBalances = bobClient.getBalances();
        log.debug("Bob's After Trade Balance:\n{}", formatBalancesTbls(bobsBalances));
    }

    private TradeInfo getBsqSwapTrade(GrpcClient client, String tradeId) {
        int numFetchAttempts = 0;
        while (true) {
            try {
                numFetchAttempts++;
                return client.getTrade(tradeId);
            } catch (Exception ex) {
                log.warn(ex.getMessage());
                if (numFetchAttempts > 9) {
                    if (checkForLoggedExceptions) {
                        printNodeExceptionMessages(log);
                    }
                    fail(format("Could not find new bsq swap trade after %d attempts.", numFetchAttempts));
                } else {
                    sleep(1_000);
                }
            }
        }
    }
}
