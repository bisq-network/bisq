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

import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.Scaffold.EXIT_FAILURE;
import static bisq.apitest.Scaffold.EXIT_SUCCESS;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static java.lang.System.err;
import static java.lang.System.exit;



import bisq.apitest.config.ApiTestConfig;
import bisq.apitest.method.MethodTestSuite;

/**
 * ApiTest Application
 *
 * Runs all method tests, scenario tests, and end to end tests (e2e).
 *
 * Requires bitcoind v0.19.x
 */
@Slf4j
public class ApiTestMain {

    public static void main(String[] args) {
        new ApiTestMain().execute(args);
    }

    public void execute(@SuppressWarnings("unused") String[] args) {
        try {
            Scaffold scaffold = new Scaffold(args).setUp();
            ApiTestConfig config = scaffold.config;

            if (config.skipTests) {
                log.info("Skipping tests ...");
            } else {
                new SmokeTestBitcoind(config).run();
                GrpcStubs grpcStubs = new GrpcStubs(alicedaemon, config).init();
                MethodTestSuite methodTestSuite = new MethodTestSuite(grpcStubs);
                methodTestSuite.run();
            }

            if (config.shutdownAfterTests) {
                scaffold.tearDown();
                exit(EXIT_SUCCESS);
            } else {
                log.info("Not shutting down scaffolding background processes will run until ^C / kill -15 is rcvd ...");
            }

        } catch (Throwable ex) {
            err.println("Fault: An unexpected error occurred. " +
                    "Please file a report at https://bisq.network/issues");
            ex.printStackTrace(err);
            exit(EXIT_FAILURE);
        }
    }
}
