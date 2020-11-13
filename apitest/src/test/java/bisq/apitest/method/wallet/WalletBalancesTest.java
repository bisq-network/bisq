package bisq.apitest.method.wallet;

import bisq.proto.grpc.BalancesInfo;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static org.junit.jupiter.api.Assertions.assertEquals;



import bisq.apitest.method.MethodTest;
import bisq.cli.TableFormat;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WalletBalancesTest extends MethodTest {

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
    public void testNewGetBalances(final TestInfo testInfo) {
        BalancesInfo alicesBalances = getBalances(alicedaemon);
        BalancesInfo bobsBalances = getBalances(bobdaemon);

        log.info("{} Alice's Balances:\n{}", testName(testInfo), TableFormat.formatBalancesTbls(alicesBalances));
        log.info("{} Bob's Balances:\n{}", testName(testInfo), TableFormat.formatBalancesTbls(bobsBalances));

        assertEquals(INITIAL_BTC_BALANCES.getAvailableBalance(), alicesBalances.getBtcBalanceInfo().getAvailableBalance());
        assertEquals(INITIAL_BTC_BALANCES.getAvailableBalance(), bobsBalances.getBtcBalanceInfo().getAvailableBalance());
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
