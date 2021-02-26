package bisq.apitest.method.wallet;

import bisq.proto.grpc.BtcBalanceInfo;
import bisq.proto.grpc.TxInfo;

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
import static bisq.apitest.method.wallet.WalletTestUtil.INITIAL_BTC_BALANCES;
import static bisq.apitest.method.wallet.WalletTestUtil.verifyBtcBalances;
import static bisq.cli.TableFormat.formatAddressBalanceTbl;
import static bisq.cli.TableFormat.formatBtcBalanceInfoTbl;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;



import bisq.apitest.method.MethodTest;

@Disabled
@Slf4j
@TestMethodOrder(OrderAnnotation.class)
public class BtcWalletTest extends MethodTest {

    private static final String TX_MEMO = "tx memo";

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

        BtcBalanceInfo alicesBalances = aliceClient.getBtcBalances();
        log.debug("{} Alice's BTC Balances:\n{}", testName(testInfo), formatBtcBalanceInfoTbl(alicesBalances));

        BtcBalanceInfo bobsBalances = bobClient.getBtcBalances();
        log.debug("{} Bob's BTC Balances:\n{}", testName(testInfo), formatBtcBalanceInfoTbl(bobsBalances));

        assertEquals(INITIAL_BTC_BALANCES.getAvailableBalance(), alicesBalances.getAvailableBalance());
        assertEquals(INITIAL_BTC_BALANCES.getAvailableBalance(), bobsBalances.getAvailableBalance());
    }

    @Test
    @Order(2)
    public void testFundAlicesBtcWallet(final TestInfo testInfo) {
        String newAddress = aliceClient.getUnusedBtcAddress();
        bitcoinCli.sendToAddress(newAddress, "2.5");
        genBtcBlocksThenWait(1, 1000);

        BtcBalanceInfo btcBalanceInfo = aliceClient.getBtcBalances();
        // New balance is 12.5 BTC
        assertEquals(1250000000, btcBalanceInfo.getAvailableBalance());

        log.debug("{} -> Alice's Funded Address Balance -> \n{}",
                testName(testInfo),
                formatAddressBalanceTbl(singletonList(aliceClient.getAddressBalance(newAddress))));

        // New balance is 12.5 BTC
        btcBalanceInfo = aliceClient.getBtcBalances();
        bisq.core.api.model.BtcBalanceInfo alicesExpectedBalances =
                bisq.core.api.model.BtcBalanceInfo.valueOf(1250000000,
                        0,
                        1250000000,
                        0);
        verifyBtcBalances(alicesExpectedBalances, btcBalanceInfo);
        log.debug("{} -> Alice's BTC Balances After Sending 2.5 BTC -> \n{}",
                testName(testInfo),
                formatBtcBalanceInfoTbl(btcBalanceInfo));
    }

    @Test
    @Order(3)
    public void testAliceSendBTCToBob(TestInfo testInfo) {
        String bobsBtcAddress = bobClient.getUnusedBtcAddress();
        log.debug("Sending 5.5 BTC From Alice to Bob @ {}", bobsBtcAddress);

        TxInfo txInfo = aliceClient.sendBtc(bobsBtcAddress,
                "5.50",
                "100",
                TX_MEMO);
        assertTrue(txInfo.getIsPending());

        // Note that the memo is not set on the tx yet.
        assertTrue(txInfo.getMemo().isEmpty());
        genBtcBlocksThenWait(1, 1000);

        // Fetch the tx and check for confirmation and memo.
        txInfo = aliceClient.getTransaction(txInfo.getTxId());
        assertFalse(txInfo.getIsPending());
        assertEquals(TX_MEMO, txInfo.getMemo());

        BtcBalanceInfo alicesBalances = aliceClient.getBtcBalances();
        log.debug("{} Alice's BTC Balances:\n{}",
                testName(testInfo),
                formatBtcBalanceInfoTbl(alicesBalances));
        bisq.core.api.model.BtcBalanceInfo alicesExpectedBalances =
                bisq.core.api.model.BtcBalanceInfo.valueOf(700000000,
                        0,
                        700000000,
                        0);
        verifyBtcBalances(alicesExpectedBalances, alicesBalances);

        BtcBalanceInfo bobsBalances = bobClient.getBtcBalances();
        log.debug("{} Bob's BTC Balances:\n{}",
                testName(testInfo),
                formatBtcBalanceInfoTbl(bobsBalances));
        // The sendbtc tx weight and size randomly varies between two distinct values
        // (876 wu, 219 bytes, OR 880 wu, 220 bytes) from test run to test run, hence
        // the assertion of an available balance range [1549978000, 1549978100].
        assertTrue(bobsBalances.getAvailableBalance() >= 1549978000);
        assertTrue(bobsBalances.getAvailableBalance() <= 1549978100);
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
