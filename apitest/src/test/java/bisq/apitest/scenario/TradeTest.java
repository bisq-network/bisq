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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;



import bisq.apitest.method.trade.AbstractTradeTest;
import bisq.apitest.method.trade.BsqSwapBuyBtcTradeTest;
import bisq.apitest.method.trade.BsqSwapSellBtcTradeTest;
import bisq.apitest.method.trade.FailUnfailTradeTest;
import bisq.apitest.method.trade.TakeBuyBSQOfferTest;
import bisq.apitest.method.trade.TakeBuyBTCOfferTest;
import bisq.apitest.method.trade.TakeBuyBTCOfferWithNationalBankAcctTest;
import bisq.apitest.method.trade.TakeBuyXMROfferTest;
import bisq.apitest.method.trade.TakeSellBSQOfferTest;
import bisq.apitest.method.trade.TakeSellBTCOfferTest;
import bisq.apitest.method.trade.TakeSellXMROfferTest;


@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TradeTest extends AbstractTradeTest {

    @BeforeEach
    public void init() {
        EXPECTED_PROTOCOL_STATUS.init();
    }

    @Test
    @Order(1)
    public void testTakeBuyBTCOffer(final TestInfo testInfo) {
        TakeBuyBTCOfferTest test = new TakeBuyBTCOfferTest();
        test.testTakeAlicesBuyOffer(testInfo);
        test.testAlicesConfirmPaymentStarted(testInfo);
        test.testBobsConfirmPaymentReceived(testInfo);
        test.testCloseTrade(testInfo);
    }

    @Test
    @Order(2)
    public void testTakeSellBTCOffer(final TestInfo testInfo) {
        TakeSellBTCOfferTest test = new TakeSellBTCOfferTest();
        test.testTakeAlicesSellOffer(testInfo);
        test.testBobsConfirmPaymentStarted(testInfo);
        test.testAlicesConfirmPaymentReceived(testInfo);
        test.testBobsBtcWithdrawalToExternalAddress(testInfo);
    }

    @Test
    @Order(3)
    public void testTakeBuyBSQOffer(final TestInfo testInfo) {
        TakeBuyBSQOfferTest test = new TakeBuyBSQOfferTest();
        test.testTakeAlicesSellBTCForBSQOffer(testInfo);
        test.testBobsConfirmPaymentStarted(testInfo);
        test.testAlicesConfirmPaymentReceived(testInfo);
        test.testKeepFunds(testInfo);
    }

    @Test
    @Order(4)
    public void testTakeBuyBTCOfferWithNationalBankAcct(final TestInfo testInfo) {
        TakeBuyBTCOfferWithNationalBankAcctTest test = new TakeBuyBTCOfferWithNationalBankAcctTest();
        test.testTakeAlicesBuyOffer(testInfo);
        test.testBankAcctDetailsIncludedInContracts(testInfo);
        test.testAlicesConfirmPaymentStarted(testInfo);
        test.testBobsConfirmPaymentReceived(testInfo);
        test.testKeepFunds(testInfo);
    }

    @Test
    @Order(5)
    public void testTakeSellBSQOffer(final TestInfo testInfo) {
        TakeSellBSQOfferTest test = new TakeSellBSQOfferTest();
        test.testTakeAlicesBuyBTCForBSQOffer(testInfo);
        test.testAlicesConfirmPaymentStarted(testInfo);
        test.testBobsConfirmPaymentReceived(testInfo);
        test.testAlicesBtcWithdrawalToExternalAddress(testInfo);
    }

    @Test
    @Order(6)
    public void testTakeBuyXMROffer(final TestInfo testInfo) {
        TakeBuyXMROfferTest test = new TakeBuyXMROfferTest();
        TakeBuyXMROfferTest.createXmrPaymentAccounts();
        test.testTakeAlicesSellBTCForXMROffer(testInfo);
        test.testBobsConfirmPaymentStarted(testInfo);
        test.testAlicesConfirmPaymentReceived(testInfo);
        test.testCloseTrade(testInfo);
    }

    @Test
    @Order(7)
    public void testTakeSellXMROffer(final TestInfo testInfo) {
        TakeSellXMROfferTest test = new TakeSellXMROfferTest();
        TakeBuyXMROfferTest.createXmrPaymentAccounts();
        test.testTakeAlicesBuyBTCForXMROffer(testInfo);
        test.testAlicesConfirmPaymentStarted(testInfo);
        test.testBobsConfirmPaymentReceived(testInfo);
        test.testAlicesBtcWithdrawalToExternalAddress(testInfo);
    }

    @Test
    @Order(8)
    public void testBsqSwapBuyBtcTrade(final TestInfo testInfo) {
        BsqSwapBuyBtcTradeTest test = new BsqSwapBuyBtcTradeTest();
        test.testGetBalancesBeforeTrade();
        test.testAliceCreateBsqSwapBuyBtcOffer();
        test.testBobTakesBsqSwapOffer();
        test.testGetBalancesAfterTrade();
    }

    @Test
    @Order(9)
    public void testBsqSwapSellBtcTrade(final TestInfo testInfo) {
        BsqSwapSellBtcTradeTest test = new BsqSwapSellBtcTradeTest();
        test.testGetBalancesBeforeTrade();
        test.testAliceCreateBsqSwapSellBtcOffer();
        test.testBobTakesBsqSwapOffer();
        test.testGetBalancesAfterTrade();
    }

    @Test
    @Order(10)
    public void testFailUnfailTrade(final TestInfo testInfo) {
        FailUnfailTradeTest test = new FailUnfailTradeTest();
        test.testFailAndUnFailBuyBTCTrade(testInfo);
        test.testFailAndUnFailSellBTCTrade(testInfo);
        test.testFailAndUnFailBuyXmrTrade(testInfo);
        test.testFailAndUnFailTakeSellXMRTrade(testInfo);
    }
}
