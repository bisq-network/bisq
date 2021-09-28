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

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;

import static java.lang.System.getenv;



import bisq.apitest.method.trade.AbstractTradeTest;
import bisq.apitest.method.trade.TakeBuyBSQOfferTest;
import bisq.apitest.method.trade.TakeBuyBTCOfferTest;
import bisq.apitest.method.trade.TakeBuyXMROfferTest;
import bisq.apitest.method.trade.TakeSellBSQOfferTest;
import bisq.apitest.method.trade.TakeSellBTCOfferTest;
import bisq.apitest.method.trade.TakeSellXMROfferTest;

@EnabledIf("envLongRunningTestEnabled")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LongRunningTradesTest extends AbstractTradeTest {

    // A cycle of trades is six trades:  Buy & Sell BTC, Buy & Sell BSQ, Buy & Sell XMR.
    private static final int NUM_TRADES_PER_CYCLE = 6;
    private static final int MAX_TRADES = NUM_TRADES_PER_CYCLE * 1;

    private static final long DELAY_BETWEEN_TRADES = 15_000;

    @Test
    @Order(1)
    public void TradeCycle(final TestInfo testInfo) {
        int numTrades = 0;
        while (numTrades < MAX_TRADES) {

            initTestFixture(++numTrades);
            testTakeBuyBTCOffer(testInfo);
            generateBtcBlock();

            initTestFixture(++numTrades);
            testTakeSellBTCOffer(testInfo);
            generateBtcBlock();

            initTestFixture(++numTrades);
            testTakeBuyBSQOffer(testInfo);
            generateBtcBlock();

            initTestFixture(++numTrades);
            testTakeSellBSQOffer(testInfo);
            generateBtcBlock();

            initTestFixture(++numTrades);
            testTakeBuyXMROffer(testInfo);
            generateBtcBlock();

            initTestFixture(++numTrades);
            testTakeSellXMROffer(testInfo);
            generateBtcBlock();

            // printClosedTrades();
        }
    }

    public void testTakeBuyBTCOffer(final TestInfo testInfo) {
        TakeBuyBTCOfferTest test = new TakeBuyBTCOfferTest();
        setLongRunningTest(true);
        test.testTakeAlicesBuyOffer(testInfo);
        test.testAlicesConfirmPaymentStarted(testInfo);
        test.testBobsConfirmPaymentReceived(testInfo);
        test.testKeepFunds(testInfo);
    }

    public void testTakeSellBTCOffer(final TestInfo testInfo) {
        TakeSellBTCOfferTest test = new TakeSellBTCOfferTest();
        setLongRunningTest(true);
        test.testTakeAlicesSellOffer(testInfo);
        test.testBobsConfirmPaymentStarted(testInfo);
        test.testAlicesConfirmPaymentReceived(testInfo);
        test.testBobsBtcWithdrawalToExternalAddress(testInfo);
    }

    public void testTakeBuyBSQOffer(final TestInfo testInfo) {
        TakeBuyBSQOfferTest test = new TakeBuyBSQOfferTest();
        setLongRunningTest(true);
        test.createBsqPaymentAccounts();
        test.testTakeAlicesSellBTCForBSQOffer(testInfo);
        test.testBobsConfirmPaymentStarted(testInfo);
        test.testAlicesConfirmPaymentReceived(testInfo);
        test.testKeepFunds(testInfo);

    }

    public void testTakeSellBSQOffer(final TestInfo testInfo) {
        TakeSellBSQOfferTest test = new TakeSellBSQOfferTest();
        setLongRunningTest(true);
        test.createBsqPaymentAccounts();
        test.testTakeAlicesBuyBTCForBSQOffer(testInfo);
        test.testAlicesConfirmPaymentStarted(testInfo);
        test.testBobsConfirmPaymentReceived(testInfo);
        test.testAlicesBtcWithdrawalToExternalAddress(testInfo);
    }

    public void testTakeBuyXMROffer(final TestInfo testInfo) {
        TakeBuyXMROfferTest test = new TakeBuyXMROfferTest();
        setLongRunningTest(true);
        test.createXmrPaymentAccounts();
        test.testTakeAlicesSellBTCForXMROffer(testInfo);
        test.testBobsConfirmPaymentStarted(testInfo);
        test.testAlicesConfirmPaymentReceived(testInfo);
        test.testKeepFunds(testInfo);
    }

    public void testTakeSellXMROffer(final TestInfo testInfo) {
        TakeSellXMROfferTest test = new TakeSellXMROfferTest();
        setLongRunningTest(true);
        test.createXmrPaymentAccounts();
        test.testTakeAlicesBuyBTCForXMROffer(testInfo);
        test.testAlicesConfirmPaymentStarted(testInfo);
        test.testBobsConfirmPaymentReceived(testInfo);
        test.testAlicesBtcWithdrawalToExternalAddress(testInfo);
    }

    private void initTestFixture(int numTrades) {
        EXPECTED_PROTOCOL_STATUS.init();
        log.info("*******************************************************************");
        log.info("Trade # {}", numTrades);
        log.info("*******************************************************************");
    }

    private void generateBtcBlock() {
        genBtcBlocksThenWait(1, DELAY_BETWEEN_TRADES);
    }

    // TODO Uncomment when API 'gettrades' method is implemented.
    /*
    private void printClosedTrades() {
        log.info("Alice's Closed Trades");
        new TableBuilder(TableType.TRADE_HISTORY_TBL, aliceClient.getTradeHistory(CLOSED)).build().print(out);
        log.info("Bob's Closed Trades");
        new TableBuilder(TableType.TRADE_HISTORY_TBL, bobClient.getTradeHistory(CLOSED)).build().print(out);
    }
     */

    protected static boolean envLongRunningTestEnabled() {
        String envName = "LONG_RUNNING_TRADES_TEST_ENABLED";
        String envX = getenv(envName);
        if (envX != null) {
            log.info("Enabled, found {}.", envName);
            return true;
        } else {
            log.info("Skipped, no environment variable {} defined.", envName);
            log.info("To enable on Mac OS or Linux:"
                    + "\tIf running in terminal, export LONG_RUNNING_TRADES_TEST_ENABLED=true in bash shell."
                    + "\tIf running in Intellij, set LONG_RUNNING_TRADES_TEST_ENABLED=true in launcher's Environment variables field.");
            return false;
        }
    }
}
