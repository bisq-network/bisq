package bisq.apitest.method.wallet;

import bisq.proto.grpc.BsqBalanceInfo;

import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;

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
import static bisq.apitest.config.BisqAppConfig.arbdaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static bisq.apitest.method.wallet.WalletTestUtil.ALICES_INITIAL_BSQ_BALANCES;
import static bisq.apitest.method.wallet.WalletTestUtil.BOBS_INITIAL_BSQ_BALANCES;
import static bisq.apitest.method.wallet.WalletTestUtil.bsqBalanceModel;
import static bisq.apitest.method.wallet.WalletTestUtil.verifyBsqBalances;
import static bisq.cli.TableFormat.formatBsqBalanceInfoTbl;
import static org.bitcoinj.core.NetworkParameters.PAYMENT_PROTOCOL_ID_MAINNET;
import static org.bitcoinj.core.NetworkParameters.PAYMENT_PROTOCOL_ID_REGTEST;
import static org.bitcoinj.core.NetworkParameters.PAYMENT_PROTOCOL_ID_TESTNET;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;



import bisq.apitest.config.BisqAppConfig;
import bisq.apitest.method.MethodTest;

@Disabled
@Slf4j
@TestMethodOrder(OrderAnnotation.class)
public class BsqWalletTest extends MethodTest {

    private static final String SEND_BSQ_AMOUNT = "25000.50";

    @BeforeAll
    public static void setUp() {
        startSupportingApps(false,
                true,
                bitcoind,
                seednode,
                arbdaemon,
                alicedaemon,
                bobdaemon);
    }

    @Test
    @Order(1)
    public void testGetUnusedBsqAddress() {
        var request = createGetUnusedBsqAddressRequest();

        String address = grpcStubs(alicedaemon).walletsService.getUnusedBsqAddress(request).getAddress();
        assertFalse(address.isEmpty());
        assertTrue(address.startsWith("B"));

        NetworkParameters networkParameters = LegacyAddress.getParametersFromAddress(address.substring(1));
        String addressNetwork = networkParameters.getPaymentProtocolId();
        assertNotEquals(PAYMENT_PROTOCOL_ID_MAINNET, addressNetwork);
        // TODO Fix bug causing the regtest bsq address network to be evaluated as 'testnet' here.
        assertTrue(addressNetwork.equals(PAYMENT_PROTOCOL_ID_TESTNET)
                || addressNetwork.equals(PAYMENT_PROTOCOL_ID_REGTEST));
    }

    @Test
    @Order(2)
    public void testInitialBsqBalances(final TestInfo testInfo) {
        BsqBalanceInfo alicesBsqBalances = getBsqBalances(alicedaemon);
        log.debug("{} -> Alice's BSQ Initial Balances -> \n{}",
                testName(testInfo),
                formatBsqBalanceInfoTbl(alicesBsqBalances));
        verifyBsqBalances(ALICES_INITIAL_BSQ_BALANCES, alicesBsqBalances);

        BsqBalanceInfo bobsBsqBalances = getBsqBalances(bobdaemon);
        log.debug("{} -> Bob's BSQ Initial Balances -> \n{}",
                testName(testInfo),
                formatBsqBalanceInfoTbl(bobsBsqBalances));
        verifyBsqBalances(BOBS_INITIAL_BSQ_BALANCES, bobsBsqBalances);
    }

    @Test
    @Order(3)
    public void testSendBsqAndCheckBalancesBeforeGeneratingBtcBlock(final TestInfo testInfo) {
        String bobsBsqAddress = getUnusedBsqAddress(bobdaemon);
        sendBsq(alicedaemon, bobsBsqAddress, SEND_BSQ_AMOUNT, "100");
        sleep(2000);

        BsqBalanceInfo alicesBsqBalances = getBsqBalances(alicedaemon);
        BsqBalanceInfo bobsBsqBalances = waitForNonZeroBsqUnverifiedBalance(bobdaemon);

        log.debug("BSQ Balances Before BTC Block Gen...");
        printBobAndAliceBsqBalances(testInfo,
                bobsBsqBalances,
                alicesBsqBalances,
                alicedaemon);

        verifyBsqBalances(bsqBalanceModel(150000000,
                2500050,
                0,
                0,
                0,
                0),
                bobsBsqBalances);

        verifyBsqBalances(bsqBalanceModel(97499950,
                97499950,
                97499950,
                0,
                0,
                0),
                alicesBsqBalances);
    }

    @Test
    @Order(4)
    public void testBalancesAfterSendingBsqAndGeneratingBtcBlock(final TestInfo testInfo) {
        // There is a wallet persist delay;  we have to
        // wait for both wallets to be saved to disk.
        genBtcBlocksThenWait(1, 4000);

        BsqBalanceInfo alicesBsqBalances = getBsqBalances(alicedaemon);
        BsqBalanceInfo bobsBsqBalances = waitForBsqNewAvailableConfirmedBalance(bobdaemon, 150000000);

        log.debug("See Available Confirmed BSQ Balances...");
        printBobAndAliceBsqBalances(testInfo,
                bobsBsqBalances,
                alicesBsqBalances,
                alicedaemon);

        verifyBsqBalances(bsqBalanceModel(152500050,
                0,
                0,
                0,
                0,
                0),
                bobsBsqBalances);

        verifyBsqBalances(bsqBalanceModel(97499950,
                0,
                0,
                0,
                0,
                0),
                alicesBsqBalances);
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }

    private BsqBalanceInfo waitForNonZeroBsqUnverifiedBalance(BisqAppConfig daemon) {
        // A BSQ recipient needs to wait for her daemon to detect a new tx.
        // Loop here until her unverifiedBalance != 0, or give up after 15 seconds.
        // A slow test is preferred over a flaky test.
        BsqBalanceInfo bsqBalance = getBsqBalances(daemon);
        for (int numRequests = 1; numRequests <= 15 && bsqBalance.getUnverifiedBalance() == 0; numRequests++) {
            sleep(1000);
            bsqBalance = getBsqBalances(daemon);
        }
        return bsqBalance;
    }

    private BsqBalanceInfo waitForBsqNewAvailableConfirmedBalance(BisqAppConfig daemon,
                                                                  long staleBalance) {
        BsqBalanceInfo bsqBalance = getBsqBalances(daemon);
        for (int numRequests = 1;
             numRequests <= 15 && bsqBalance.getAvailableConfirmedBalance() == staleBalance;
             numRequests++) {
            sleep(1000);
            bsqBalance = getBsqBalances(daemon);
        }
        return bsqBalance;
    }

    @SuppressWarnings("SameParameterValue")
    private void printBobAndAliceBsqBalances(final TestInfo testInfo,
                                             BsqBalanceInfo bobsBsqBalances,
                                             BsqBalanceInfo alicesBsqBalances,
                                             BisqAppConfig senderApp) {
        log.debug("{} -> Bob's BSQ Balances After {} {} BSQ-> \n{}",
                testName(testInfo),
                senderApp.equals(bobdaemon) ? "Sending" : "Receiving",
                SEND_BSQ_AMOUNT,
                formatBsqBalanceInfoTbl(bobsBsqBalances));

        log.debug("{} -> Alice's Balances After {} {} BSQ-> \n{}",
                testName(testInfo),
                senderApp.equals(alicedaemon) ? "Sending" : "Receiving",
                SEND_BSQ_AMOUNT,
                formatBsqBalanceInfoTbl(alicesBsqBalances));
    }
}
