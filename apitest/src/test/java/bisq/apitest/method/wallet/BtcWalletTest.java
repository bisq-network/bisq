package bisq.apitest.method.wallet;

import bisq.proto.grpc.BtcBalanceInfo;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static bisq.cli.TableFormat.formatAddressBalanceTbl;
import static bisq.cli.TableFormat.formatBtcBalanceInfoTbl;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;



import bisq.apitest.method.MethodTest;

@Disabled
@Slf4j
@TestMethodOrder(OrderAnnotation.class)
public class BtcWalletTest extends MethodTest {

    // All api tests depend on the DAO / regtest environment, and Bob & Alice's wallets
    // are initialized with 10 BTC during the scaffolding setup.
    private static final bisq.core.api.model.BtcBalanceInfo INITIAL_BTC_BALANCES =
            bisq.core.api.model.BtcBalanceInfo.valueOf(1000000000,
                    0,
                    1000000000,
                    0);

    @BeforeAll
    public static void setUp() {
        startSupportingApps(false,
                true,
                bitcoind,
                seednode,
                alicedaemon,
                bobdaemon);
    }

    @Test
    @Order(1)
    public void testInitialBtcBalances(final TestInfo testInfo) {
        // Bob & Alice's regtest Bisq wallets were initialized with 10 BTC.

        BtcBalanceInfo alicesBalances = getBtcBalances(alicedaemon);
        log.info("{} Alice's BTC Balances:\n{}", testName(testInfo), formatBtcBalanceInfoTbl(alicesBalances));

        BtcBalanceInfo bobsBalances = getBtcBalances(bobdaemon);
        log.info("{} Bob's BTC Balances:\n{}", testName(testInfo), formatBtcBalanceInfoTbl(bobsBalances));

        assertEquals(INITIAL_BTC_BALANCES.getAvailableBalance(), alicesBalances.getAvailableBalance());
        assertEquals(INITIAL_BTC_BALANCES.getAvailableBalance(), bobsBalances.getAvailableBalance());
    }

    @Test
    @Order(2)
    public void testFundAlicesBtcWallet(final TestInfo testInfo) {
        String newAddress = getUnusedBtcAddress(alicedaemon);
        bitcoinCli.sendToAddress(newAddress, "2.5");
        genBtcBlocksThenWait(1, 1500);

        BtcBalanceInfo btcBalanceInfo = getBtcBalances(alicedaemon);
        // New balance is 12.5 BTC
        assertEquals(1250000000, btcBalanceInfo.getAvailableBalance());

        log.info("{} -> Alice's Funded Address Balance -> \n{}",
                testName(testInfo),
                formatAddressBalanceTbl(singletonList(getAddressBalance(alicedaemon, newAddress))));

        // New balance is 12.5 BTC
        btcBalanceInfo = getBtcBalances(alicedaemon);
        bisq.core.api.model.BtcBalanceInfo alicesExpectedBalances =
                bisq.core.api.model.BtcBalanceInfo.valueOf(1250000000,
                        0,
                        1250000000,
                        0);
        verifyBtcBalances(alicesExpectedBalances, btcBalanceInfo);
        log.info("{} -> Alice's BTC Balances After Sending 2.5 BTC -> \n{}",
                testName(testInfo),
                formatBtcBalanceInfoTbl(btcBalanceInfo));
    }

    @Test
    @Order(3)
    public void testAliceSendBTCToBob(TestInfo testInfo) {
        String bobsBtcAddress = getUnusedBtcAddress(bobdaemon);
        log.debug("Sending 5.5 BTC From Alice to Bob @ {}", bobsBtcAddress);

        sendBtc(alicedaemon,
                bobsBtcAddress,
                "5.50",
                "100",
                "to whom it may concern");
        genBtcBlocksThenWait(1, 3000);

        BtcBalanceInfo alicesBalances = getBtcBalances(alicedaemon);

        log.debug("{} Alice's BTC Balances:\n{}",
                testName(testInfo),
                formatBtcBalanceInfoTbl(alicesBalances));
        bisq.core.api.model.BtcBalanceInfo alicesExpectedBalances =
                bisq.core.api.model.BtcBalanceInfo.valueOf(700000000,
                        0,
                        700000000,
                        0);
        verifyBtcBalances(alicesExpectedBalances, alicesBalances);

        BtcBalanceInfo bobsBalances = getBtcBalances(bobdaemon);
        log.debug("{} Bob's BTC Balances:\n{}",
                testName(testInfo),
                formatBtcBalanceInfoTbl(bobsBalances));
        // We cannot (?) predict the exact tx size and calculate how much in tx fees were
        // deducted from the 5.5 BTC sent to Bob, but we do know Bob should have something
        // between 15.49978000 and 15.49978100 BTC.
        assertTrue(bobsBalances.getAvailableBalance() >= 1549978000);
        assertTrue(bobsBalances.getAvailableBalance() <= 1549978100);
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }

    private void verifyBtcBalances(bisq.core.api.model.BtcBalanceInfo expected,
                                   BtcBalanceInfo actual) {
        assertEquals(expected.getAvailableBalance(), actual.getAvailableBalance());
        assertEquals(expected.getReservedBalance(), actual.getReservedBalance());
        assertEquals(expected.getTotalAvailableBalance(), actual.getTotalAvailableBalance());
        assertEquals(expected.getLockedBalance(), actual.getLockedBalance());
    }
}
