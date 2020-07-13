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



import bisq.apitest.GrpcStubs;

@Slf4j
public class SimpleWalletPasswordTest extends MethodTest {

    public SimpleWalletPasswordTest(GrpcStubs grpcStubs) {
        super(grpcStubs);
    }

    public void setUp() {
        log.info("{} ...", this.getClass().getSimpleName());
    }

    public void run() {
        setUp();
        testSetWalletPassword();
        testRemoveWalletPassword();
        report();
        tearDown();
    }

    public void testSetWalletPassword() {
        if (isSkipped("testSetWalletPassword"))
            return;

        // Set a password on the wallet, give time for the wallet to be persisted to disk,
        // and attempt to get the balance of the locked wallet.
        // If the gRPC GetBalanceService throws an exception with a msg saying the wallet
        // is locked, the test passes.
        var setPasswordRequest = createSetWalletPasswordRequest("password");
        grpcStubs.walletsService.setWalletPassword(setPasswordRequest);
        sleep(1500);

        try {
            getBalance();
        } catch (Throwable t) {
            if (t.getMessage().contains("wallet is locked")) {
                log.info("{} testSetWalletPassword passed", CHECK);
                countPassedTestCases++;
            } else {
                log.info("{} testSetWalletPassword failed, expected '{}' exception, actual '{}'",
                        CROSS_MARK, "wallet is locked", t.getMessage());
                log.error("", t);
                countFailedTestCases++;
            }
        }
    }

    public void testRemoveWalletPassword() {
        if (isSkipped("testRemoveWalletPassword"))
            return;

        // Remove the password on the wallet, give time for the wallet to be persisted
        // to disk, and attempt to get the balance of the locked wallet.
        // If the gRPC GetBalanceService throws an exception with a msg saying the wallet
        // is locked, the test fails.
        var removePasswordRequest = createRemoveWalletPasswordRequest("password");
        grpcStubs.walletsService.removeWalletPassword(removePasswordRequest);
        sleep(1500);

        try {
            getBalance();
            log.info("{} testRemoveWalletPassword passed", CHECK);
            countPassedTestCases++;
        } catch (Throwable t) {
            log.info("{} testRemoveWalletPassword failed", CROSS_MARK);
            log.error("", t);
            countFailedTestCases++;
        }
    }

    public void report() {
        log.info(reportString());
    }

    public void tearDown() {
    }
}
