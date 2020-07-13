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

import bisq.proto.grpc.GetVersionRequest;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.app.Version.VERSION;



import bisq.apitest.GrpcStubs;

@Slf4j
public class GetVersionTest extends MethodTest {

    public GetVersionTest(GrpcStubs grpcStubs) {
        super(grpcStubs);
    }

    public void setUp() {
        log.info("{} ...", this.getClass().getSimpleName());
    }

    public void run() {
        setUp();
        testGetVersion();
        report();
        tearDown();
    }

    public void testGetVersion() {
        if (isSkipped("testGetVersion"))
            return;

        var version = grpcStubs.versionService.getVersion(GetVersionRequest.newBuilder().build()).getVersion();
        if (version.equals(VERSION)) {
            log.info("{} testGetVersion passed", CHECK);
            countPassedTestCases++;
        } else {
            log.info("{} testGetVersion failed, expected {} actual {}", CROSS_MARK, VERSION, version);
            countFailedTestCases++;
        }
    }

    public void report() {
        log.info(reportString());
    }

    public void tearDown() {
    }
}
