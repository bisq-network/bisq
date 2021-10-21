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

package bisq.apitest;

import java.time.Duration;

import java.io.IOException;

import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import org.junit.jupiter.api.TestInfo;

import static bisq.apitest.config.ApiTestRateMeterInterceptorConfig.getTestRateMeterInterceptorConfig;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.arbdaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.net.InetAddress.getLoopbackAddress;
import static java.util.Arrays.stream;



import bisq.apitest.config.ApiTestConfig;
import bisq.apitest.method.BitcoinCliHelper;
import bisq.cli.GrpcClient;

/**
 * Base class for all test types:  'method', 'scenario' and 'e2e'.
 * <p>
 * During scaffold setup, various combinations of bitcoind and bisq instances
 * can be started in the background before test cases are run.  Currently, this test
 * harness supports only the "Bisq DAO development environment running against a
 * local Bitcoin regtest network" as described in
 * <a href="https://github.com/bisq-network/bisq/blob/master/docs/dev-setup.md">dev-setup.md</a>
 * and <a href="https://github.com/bisq-network/bisq/blob/master/docs/dao-setup.md">dao-setup.md</a>.
 * <p>
 * Those documents contain information about the configurations used by this test harness:
 * bitcoin-core's bitcoin.conf and blocknotify values, bisq instance options, the DAO genesis
 * transaction id, initial BSQ and BTC balances for Bob & Alice accounts, and Bob and
 * Alice's default payment accounts.
 * <p>
 * During a build, the
 * <a href="https://github.com/bisq-network/bisq/blob/master/docs/dao-setup.zip">dao-setup.zip</a>
 * file is downloaded and extracted if necessary.  In each test case's @BeforeClass
 * method, the DAO setup files are re-installed into the run time's data directories
 * (each test case runs on a refreshed DAO/regtest environment setup).
 * <p>
 * Initial Alice balances & accounts:  10.0 BTC, 1000000.00 BSQ, USD PerfectMoney dummy
 * <p>
 * Initial Bob balances & accounts:    10.0 BTC, 1500000.00 BSQ, USD PerfectMoney dummy
 */
@Slf4j
public class ApiTestCase {

    protected static Scaffold scaffold;
    protected static ApiTestConfig config;
    protected static BitcoinCliHelper bitcoinCli;

    @Nullable
    protected static GrpcClient arbClient;
    @Nullable
    protected static GrpcClient aliceClient;
    @Nullable
    protected static GrpcClient bobClient;

    public static void setUpScaffold(Enum<?>... supportingApps)
            throws InterruptedException, ExecutionException, IOException {
        String[] params = new String[]{
                "--supportingApps", stream(supportingApps).map(Enum::name).collect(Collectors.joining(",")),
                "--callRateMeteringConfigPath", getTestRateMeterInterceptorConfig().getAbsolutePath(),
                "--enableBisqDebugging", "false"
        };
        setUpScaffold(params);
    }

    public static void setUpScaffold(String[] params)
            throws InterruptedException, ExecutionException, IOException {
        // Test cases needing to pass more than just an ApiTestConfig
        // --supportingApps option will use this setup method, but the
        // --supportingApps option will need to be passed too, with its comma
        // delimited app list value, e.g., "bitcoind,seednode,arbdaemon".
        scaffold = new Scaffold(params).setUp();
        config = scaffold.config;
        bitcoinCli = new BitcoinCliHelper((config));
        createGrpcClients();
    }

    public static void tearDownScaffold() {
        scaffold.tearDown();
    }

    protected static void createGrpcClients() {
        if (config.supportingApps.contains(alicedaemon.name())) {
            aliceClient = new GrpcClient(getLoopbackAddress().getHostAddress(),
                    alicedaemon.apiPort,
                    config.apiPassword);
        }
        if (config.supportingApps.contains(bobdaemon.name())) {
            bobClient = new GrpcClient(getLoopbackAddress().getHostAddress(),
                    bobdaemon.apiPort,
                    config.apiPassword);
        }
        if (config.supportingApps.contains(arbdaemon.name())) {
            arbClient = new GrpcClient(getLoopbackAddress().getHostAddress(),
                    arbdaemon.apiPort,
                    config.apiPassword);
        }
    }

    protected static void genBtcBlocksThenWait(int numBlocks, long wait) {
        bitcoinCli.generateBlocks(numBlocks);
        sleep(wait);
    }

    protected static void sleep(long ms) {
        sleepUninterruptibly(Duration.ofMillis(ms));
    }

    protected final String testName(TestInfo testInfo) {
        return testInfo.getTestMethod().isPresent()
                ? testInfo.getTestMethod().get().getName()
                : "unknown test name";
    }
}
