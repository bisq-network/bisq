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

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;



import bisq.apitest.method.offer.AbstractOfferTest;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FailUnfailTradeTest extends AbstractTradeTest {

    @BeforeAll
    public static void setUp() {
        AbstractOfferTest.setUp();
    }

    @BeforeEach
    public void init() {
        EXPECTED_PROTOCOL_STATUS.init();
    }


    @Test
    @Order(1)
    public void testFailAndUnFailBuyBTCTrade(final TestInfo testInfo) {
        TakeBuyBTCOfferTest test = new TakeBuyBTCOfferTest();
        test.testTakeAlicesBuyOffer(testInfo);

        var tradeId = test.getTradeId();
        aliceClient.failTrade(tradeId);

        Throwable exception = assertThrows(StatusRuntimeException.class, () -> aliceClient.getTrade(tradeId));
        String expectedExceptionMessage = format("INVALID_ARGUMENT: trade with id '%s' not found", tradeId);
        assertEquals(expectedExceptionMessage, exception.getMessage());

        try {
            aliceClient.unFailTrade(tradeId);
            aliceClient.getTrade(tradeId); //Throws ex if trade is still failed.
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    @Order(2)
    public void testFailAndUnFailSellBTCTrade(final TestInfo testInfo) {
        TakeSellBTCOfferTest test = new TakeSellBTCOfferTest();
        test.testTakeAlicesSellOffer(testInfo);

        var tradeId = test.getTradeId();
        aliceClient.failTrade(tradeId);

        Throwable exception = assertThrows(StatusRuntimeException.class, () -> aliceClient.getTrade(tradeId));
        String expectedExceptionMessage = format("INVALID_ARGUMENT: trade with id '%s' not found", tradeId);
        assertEquals(expectedExceptionMessage, exception.getMessage());

        try {
            aliceClient.unFailTrade(tradeId);
            aliceClient.getTrade(tradeId);  //Throws ex if trade is still failed.
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    @Order(3)
    public void testFailAndUnFailBuyXmrTrade(final TestInfo testInfo) {
        TakeBuyXMROfferTest test = new TakeBuyXMROfferTest();
        test.createXmrPaymentAccounts();
        test.testTakeAlicesSellBTCForXMROffer(testInfo);

        var tradeId = test.getTradeId();
        aliceClient.failTrade(tradeId);

        Throwable exception = assertThrows(StatusRuntimeException.class, () -> aliceClient.getTrade(tradeId));
        String expectedExceptionMessage = format("INVALID_ARGUMENT: trade with id '%s' not found", tradeId);
        assertEquals(expectedExceptionMessage, exception.getMessage());

        try {
            aliceClient.unFailTrade(tradeId);
            aliceClient.getTrade(tradeId); //Throws ex if trade is still failed.
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    @Order(4)
    public void testFailAndUnFailTakeSellXMRTrade(final TestInfo testInfo) {
        TakeSellXMROfferTest test = new TakeSellXMROfferTest();
        test.createXmrPaymentAccounts();
        test.testTakeAlicesBuyBTCForXMROffer(testInfo);

        var tradeId = test.getTradeId();
        aliceClient.failTrade(tradeId);

        Throwable exception = assertThrows(StatusRuntimeException.class, () -> aliceClient.getTrade(tradeId));
        String expectedExceptionMessage = format("INVALID_ARGUMENT: trade with id '%s' not found", tradeId);
        assertEquals(expectedExceptionMessage, exception.getMessage());

        try {
            aliceClient.unFailTrade(tradeId);
            aliceClient.getTrade(tradeId); //Throws ex if trade is still failed.
        } catch (Exception ex) {
            fail(ex);
        }
    }
}
