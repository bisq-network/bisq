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
import bisq.proto.grpc.RemoveWalletPasswordRequest;
import bisq.proto.grpc.SetWalletPasswordRequest;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;



import bisq.apitest.ApiTestCase;
import bisq.apitest.GrpcStubs;
import bisq.apitest.Scaffold;
import bisq.apitest.config.ApiTestConfig;

public class MethodTest extends ApiTestCase {

    static Scaffold scaffold;
    static ApiTestConfig config;
    static GrpcStubs grpcStubs;

    public static void setUpScaffold() {
        scaffold = new Scaffold(new String[]{}).setUp();
        config = scaffold.config;
        grpcStubs = new GrpcStubs(alicedaemon, config).init();
    }

    public static void tearDownScaffold() {
        scaffold.tearDown();
    }

    // Convenience methods for building gRPC request objects

    protected final GetBalanceRequest createBalanceRequest() {
        return GetBalanceRequest.newBuilder().build();
    }

    protected final SetWalletPasswordRequest createSetWalletPasswordRequest(String password) {
        return SetWalletPasswordRequest.newBuilder().setPassword(password).build();
    }

    protected final RemoveWalletPasswordRequest createRemoveWalletPasswordRequest(String password) {
        return RemoveWalletPasswordRequest.newBuilder().setPassword("password").build();
    }

    // Convenience methods for calling frequently used & thoroughly tested gRPC services.

    protected final long getBalance() {
        return grpcStubs.walletsService.getBalance(createBalanceRequest()).getBalance();
    }
}
