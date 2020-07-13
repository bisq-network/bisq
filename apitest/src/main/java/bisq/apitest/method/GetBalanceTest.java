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

import bisq.proto.grpc.GetBalanceRequest;

import lombok.extern.slf4j.Slf4j;



import bisq.apitest.GrpcStubs;

@Slf4j
public class GetBalanceTest extends MethodTest {

    public GetBalanceTest(GrpcStubs grpcStubs) {
        super(grpcStubs);
    }

    public void setUp() {
        log.info("{} ...", this.getClass().getSimpleName());
    }

    public void run() {
        setUp();
        testGetBalance();
        report();
        tearDown();
    }

    public void testGetBalance() {
        if (isSkipped("testGetBalance"))
            return;

        var balance = grpcStubs.walletsService.getBalance(GetBalanceRequest.newBuilder().build()).getBalance();
        if (balance == 1000000000) {
            log.info("{} testGetBalance passed", CHECK);
            countPassedTestCases++;
        } else {
            log.info("{} testGetBalance failed, expected {} actual {}", CROSS_MARK, 1000000000, balance);
            countFailedTestCases++;
        }
    }

    public void report() {
        log.info(reportString());
    }

    public void tearDown() {
        // noop
    }
}
