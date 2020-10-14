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

import java.net.InetAddress;

import java.io.IOException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static java.util.concurrent.TimeUnit.MILLISECONDS;



import bisq.apitest.config.ApiTestConfig;
import bisq.apitest.config.BisqAppConfig;
import bisq.apitest.method.BitcoinCliHelper;
import bisq.cli.GrpcStubs;

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
 * transaction id, initial BSQ and BTC balances for Bob & Alice accounts, and default
 * PerfectMoney dummy payment accounts (USD) for Bob and Alice.
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
public class ApiTestCase {

    protected static Scaffold scaffold;
    protected static ApiTestConfig config;
    protected static BitcoinCliHelper bitcoinCli;

    // gRPC service stubs are used by method & scenario tests, but not e2e tests.
    private static final Map<BisqAppConfig, GrpcStubs> grpcStubsCache = new HashMap<>();

    public static void setUpScaffold(Enum<?>... supportingApps)
            throws InterruptedException, ExecutionException, IOException {
        scaffold = new Scaffold(stream(supportingApps).map(Enum::name)
                .collect(Collectors.joining(",")))
                .setUp();
        config = scaffold.config;
        bitcoinCli = new BitcoinCliHelper((config));
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
    }

    public static void tearDownScaffold() {
        scaffold.tearDown();
    }

    protected static GrpcStubs grpcStubs(BisqAppConfig bisqAppConfig) {
        if (grpcStubsCache.containsKey(bisqAppConfig)) {
            return grpcStubsCache.get(bisqAppConfig);
        } else {
            GrpcStubs stubs = new GrpcStubs(InetAddress.getLoopbackAddress().getHostAddress(),
                    bisqAppConfig.apiPort, config.apiPassword);
            grpcStubsCache.put(bisqAppConfig, stubs);
            return stubs;
        }
    }

    protected void sleep(long ms) {
        try {
            MILLISECONDS.sleep(ms);
        } catch (InterruptedException ignored) {
            // empty
        }
    }
}
