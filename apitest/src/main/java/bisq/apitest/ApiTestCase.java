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

import org.junit.Rule;
import org.junit.rules.ExpectedException;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static java.util.concurrent.TimeUnit.MILLISECONDS;



import bisq.apitest.config.ApiTestConfig;
import bisq.apitest.method.BitcoinCliHelper;

public class ApiTestCase {

    // The gRPC service stubs are used by method & scenario tests, but not e2e tests.
    protected static GrpcStubs grpcStubs;

    protected static Scaffold scaffold;
    protected static ApiTestConfig config;
    protected static BitcoinCliHelper bitcoinCli;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    public static void setUpScaffold(String supportingApps) {
        // The supportingApps argument is a comma delimited string of supporting app
        // names, e.g. "bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon"
        scaffold = new Scaffold(supportingApps).setUp();
        config = scaffold.config;
        bitcoinCli = new BitcoinCliHelper((config));
        grpcStubs = new GrpcStubs(alicedaemon, config).init();
    }

    public static void setUpScaffold() {
        scaffold = new Scaffold(new String[]{}).setUp();
        config = scaffold.config;
        grpcStubs = new GrpcStubs(alicedaemon, config).init();
    }

    public static void tearDownScaffold() {
        scaffold.tearDown();
    }

    protected void sleep(long ms) {
        try {
            MILLISECONDS.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
