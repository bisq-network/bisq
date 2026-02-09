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

package bisq.bridge.grpc;

import bisq.common.config.Config;
import bisq.common.util.ExecutorFactory;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import com.google.inject.Inject;

import java.io.IOException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;



import bisq.bridge.grpc.services.AccountAgeWitnessGrpcService;
import bisq.bridge.grpc.services.AccountTimestampGrpcService;
import bisq.bridge.grpc.services.BondedRoleGrpcService;
import bisq.bridge.grpc.services.BsqBlockGrpcService;
import bisq.bridge.grpc.services.BurningmanGrpcService;
import bisq.bridge.grpc.services.SignedWitnessGrpcService;

@Slf4j
public class BridgeGrpcServer {
    private final Server server;
    private final ExecutorService startServerExecutor;
    private final ExecutorService serverExecutor;

    @Inject
    public BridgeGrpcServer(Config config,
                            BsqBlockGrpcService bsqBlockGrpcService,
                            BurningmanGrpcService burningManGrpcService,
                            AccountTimestampGrpcService accountTimestampGrpcService,
                            AccountAgeWitnessGrpcService accountAgeWitnessGrpcService,
                            SignedWitnessGrpcService signedWitnessGrpcService,
                            BondedRoleGrpcService bondedRoleGrpcService) {
        int port = config.bridgePort;

        startServerExecutor = ExecutorFactory.newSingleThreadExecutor("BridgeGrpcServer.startServerExecutor");
        serverExecutor = ExecutorFactory.newSingleThreadExecutor("BridgeGrpcServer.server");

        server = ServerBuilder
                .forPort(port)
                .executor(serverExecutor)
                .addService(bsqBlockGrpcService)
                .addService(burningManGrpcService)
                .addService(accountTimestampGrpcService)
                .addService(accountAgeWitnessGrpcService)
                .addService(signedWitnessGrpcService)
                .addService(bondedRoleGrpcService)
                .build();
        log.info("Create gRPC server listening on port {}", port);
    }

    public void startServer() {
        CompletableFuture.runAsync(() -> {
            Thread.currentThread().setName("BridgeGrpcServer");
            try {
                server.start();
                log.info("Server started.");
                server.awaitTermination();
            } catch (IOException e) {
                log.error("IOException at starting server", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Server interrupted", e);
            } catch (Exception e) {
                log.error("Failed to start server", e);
            }
        }, startServerExecutor);
    }

    public void shutDown() {
        server.shutdown();
        try {
            if (!server.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                log.warn("Server did not terminate in time, forcing shutdown...");
                server.shutdownNow();
            }
        } catch (InterruptedException ignore) {
            log.warn("Shutdown interrupted, forcing shutdown now.");
            server.shutdownNow();
            Thread.currentThread().interrupt();
        }

        ExecutorFactory.shutdownAndAwaitTermination(serverExecutor, 1000);
        ExecutorFactory.shutdownAndAwaitTermination(startServerExecutor, 1000);
    }
}
