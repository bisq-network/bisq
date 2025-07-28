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

package bisq.bridge;

import bisq.core.app.BisqHeadlessApp;

import com.google.inject.Injector;

import lombok.extern.slf4j.Slf4j;



import bisq.bridge.grpc.BridgeGrpcServer;
import bisq.bridge.grpc.services.BsqBlockGrpcService;
import bisq.bridge.grpc.services.BurningmanGrpcService;

@Slf4j
public class Bridge extends BisqHeadlessApp {
    private BridgeGrpcServer bridgeGrpcServer;

    public Bridge(Injector injector) {
        this.injector = injector;
    }

    public void startApplication() {
        super.startApplication();

        bridgeGrpcServer = injector.getInstance(BridgeGrpcServer.class);
        bridgeGrpcServer.startServer();
    }

    public void shutDown() {
        injector.getInstance(BsqBlockGrpcService.class).shutDown();
        injector.getInstance(BurningmanGrpcService.class).shutDown();

        if (bridgeGrpcServer != null) {
            bridgeGrpcServer.shutDown();
            bridgeGrpcServer = null;
        }
    }
}
