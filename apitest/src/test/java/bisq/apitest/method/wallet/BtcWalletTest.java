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
    public void testDeprecatedAvailableBtcBalance() {
        // Alice's regtest Bisq wallet was initialized with 10 BTC.
        long balance = getBalance(alicedaemon); // @Deprecated method
        assertEquals(INITIAL_BTC_BALANCES.getAvailableBalance(), balance);

        // Bob's regtest Bisq wallet was initialized with 10 BTC.
        balance = getBalance(bobdaemon); // @Deprecated method
        assertEquals(INITIAL_BTC_BALANCES.getAvailableBalance(), balance);
    }

    @Test
    @Order(2)
    public void testFundAlicesBtcWallet(final TestInfo testInfo) {
        String newAddress = getUnusedBtcAddress(alicedaemon);
        bitcoinCli.sendToAddress(newAddress, "2.5");
        genBtcBlocksThenWait(1, 1500);

        long balance = getBalance(alicedaemon);  // @Deprecated method
        assertEquals(1250000000, balance); // new balance is 12.5 btc

        log.info("{} -> Alice's Funded Address Balance -> \n{}",
                testName(testInfo),
                formatAddressBalanceTbl(singletonList(getAddressBalance(alicedaemon, newAddress))));

        BtcBalanceInfo btcBalanceInfo = getBtcBalances(alicedaemon);  // new balance is 12.5 btc
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

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }

    private void verifyBtcBalances(bisq.core.api.model.BtcBalanceInfo expected,
                                   bisq.proto.grpc.BtcBalanceInfo actual) {
        assertEquals(expected.getAvailableBalance(), actual.getAvailableBalance());
        assertEquals(expected.getReservedBalance(), actual.getReservedBalance());
        assertEquals(expected.getTotalAvailableBalance(), actual.getTotalAvailableBalance());
        assertEquals(expected.getLockedBalance(), actual.getLockedBalance());
    }
}
