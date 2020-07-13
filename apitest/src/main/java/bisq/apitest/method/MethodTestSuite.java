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

package bisq.apitest.method;

import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.Scaffold.EXIT_FAILURE;
import static bisq.apitest.Scaffold.EXIT_SUCCESS;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static java.lang.String.format;
import static java.lang.System.err;
import static java.lang.System.exit;



import bisq.apitest.GrpcStubs;
import bisq.apitest.Scaffold;
import bisq.apitest.config.ApiTestConfig;

@Slf4j
public class MethodTestSuite {

    private int countTestCases;
    private int countFailedTestCases;
    private int countSkippedTestCases;
    private int countPassedTestCases;

    private final GrpcStubs grpcStubs;

    public MethodTestSuite(GrpcStubs grpcStubs) {
        this.grpcStubs = grpcStubs;
    }

    public void run() {
        log.info("{} ...", this.getClass().getSimpleName());

        MethodTest getVersionTest = new GetVersionTest(grpcStubs);
        getVersionTest.run();
        updateTally(getVersionTest);

        MethodTest getBalanceTest = new GetBalanceTest(grpcStubs);
        getBalanceTest.run();
        updateTally(getBalanceTest);

        MethodTest simpleWalletPasswordTest = new SimpleWalletPasswordTest(grpcStubs);
        simpleWalletPasswordTest.run();
        updateTally(simpleWalletPasswordTest);

        log.info(reportString());
    }

    private void updateTally(MethodTest methodTest) {
        countTestCases += methodTest.countTestCases;
        countPassedTestCases += methodTest.countPassedTestCases;
        countFailedTestCases += methodTest.countFailedTestCases;
        countSkippedTestCases += methodTest.countSkippedTestCases;
    }

    private String reportString() {
        return format("Total: %d  Passed: %d  Failed: %d  Skipped: %d",
                countTestCases,
                countPassedTestCases,
                countFailedTestCases,
                countSkippedTestCases);
    }

    public static void main(String[] args) {
        try {
            Scaffold scaffold = new Scaffold(args).setUp();
            ApiTestConfig config = scaffold.config;

            if (config.skipTests) {
                log.info("Skipping tests ...");
            } else {
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

