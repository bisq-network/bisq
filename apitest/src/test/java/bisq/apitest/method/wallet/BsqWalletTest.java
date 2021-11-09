package bisq.apitest.method.wallet;

import bisq.proto.grpc.BsqBalanceInfo;

import org.bitcoinj.core.Address;
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
import static bisq.cli.table.builder.TableType.BSQ_BALANCE_TBL;
import static org.bitcoinj.core.NetworkParameters.ID_REGTEST;
import static org.bitcoinj.core.NetworkParameters.PAYMENT_PROTOCOL_ID_REGTEST;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;



import bisq.apitest.config.BisqAppConfig;
import bisq.apitest.method.MethodTest;
import bisq.cli.GrpcClient;
import bisq.cli.table.builder.TableBuilder;

@Disabled
@Slf4j
@TestMethodOrder(OrderAnnotation.class)
public class BsqWalletTest extends MethodTest {

    private static final String SEND_BSQ_AMOUNT = "25000.50";

    @BeforeAll
    public static void setUp() {
        startSupportingApps(true,
                false,
                bitcoind,
                seednode,
                arbdaemon,
                alicedaemon,
                bobdaemon);
    }


    @Test
    @Order(1)
    public void testGetUnusedBsqAddress() {
        String addressString = aliceClient.getUnusedBsqAddress();
        assertFalse(addressString.isEmpty());
        assertTrue(addressString.startsWith("B"));
        Address address = Address.fromString(NetworkParameters.fromID(ID_REGTEST), addressString.substring(1));
        NetworkParameters networkParameters = address.getParameters();
        String addressNetwork = networkParameters.getPaymentProtocolId();
        assertTrue(addressNetwork.equals(PAYMENT_PROTOCOL_ID_REGTEST));
    }

    @Test
    @Order(2)
    public void testInitialBsqBalances(final TestInfo testInfo) {
        BsqBalanceInfo alicesBsqBalances = aliceClient.getBsqBalances();
        log.debug("{} -> Alice's BSQ Initial Balances -> \n{}",
                testName(testInfo),
                new TableBuilder(BSQ_BALANCE_TBL, alicesBsqBalances).build());
        verifyBsqBalances(ALICES_INITIAL_BSQ_BALANCES, alicesBsqBalances);

        BsqBalanceInfo bobsBsqBalances = bobClient.getBsqBalances();
        log.debug("{} -> Bob's BSQ Initial Balances -> \n{}",
                testName(testInfo),
                new TableBuilder(BSQ_BALANCE_TBL, bobsBsqBalances).build());
        verifyBsqBalances(BOBS_INITIAL_BSQ_BALANCES, bobsBsqBalances);
    }

    @Test
    @Order(3)
    public void testSendBsqAndCheckBalancesBeforeGeneratingBtcBlock(final TestInfo testInfo) {
        String bobsBsqAddress = bobClient.getUnusedBsqAddress();
        aliceClient.sendBsq(bobsBsqAddress, SEND_BSQ_AMOUNT, "100");
        sleep(2000);

        BsqBalanceInfo alicesBsqBalances = aliceClient.getBsqBalances();
        BsqBalanceInfo bobsBsqBalances = waitForNonZeroBsqUnverifiedBalance(bobClient);

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

        BsqBalanceInfo alicesBsqBalances = aliceClient.getBalances().getBsq();
        BsqBalanceInfo bobsBsqBalances = waitForBsqNewAvailableConfirmedBalance(bobClient, 150000000);

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

    private BsqBalanceInfo waitForNonZeroBsqUnverifiedBalance(GrpcClient grpcClient) {
        // A BSQ recipient needs to wait for her daemon to detect a new tx.
        // Loop here until her unverifiedBalance != 0, or give up after 15 seconds.
        // A slow test is preferred over a flaky test.
        BsqBalanceInfo bsqBalance = grpcClient.getBsqBalances();
        for (int numRequests = 1; numRequests <= 15 && bsqBalance.getUnverifiedBalance() == 0; numRequests++) {
            sleep(1000);
            bsqBalance = grpcClient.getBsqBalances();
        }
        return bsqBalance;
    }

    private BsqBalanceInfo waitForBsqNewAvailableConfirmedBalance(GrpcClient grpcClient,
                                                                  long staleBalance) {
        BsqBalanceInfo bsqBalance = grpcClient.getBsqBalances();
        for (int numRequests = 1;
             numRequests <= 15 && bsqBalance.getAvailableConfirmedBalance() == staleBalance;
             numRequests++) {
            sleep(1000);
            bsqBalance = grpcClient.getBsqBalances();
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
                new TableBuilder(BSQ_BALANCE_TBL, bobsBsqBalances).build());

        log.debug("{} -> Alice's Balances After {} {} BSQ-> \n{}",
                testName(testInfo),
                senderApp.equals(alicedaemon) ? "Sending" : "Receiving",
                SEND_BSQ_AMOUNT,
                new TableBuilder(BSQ_BALANCE_TBL, alicesBsqBalances).build());
    }
}
