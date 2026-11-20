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

package bisq.daemon.grpc;

import bisq.core.api.CoreContext;

import bisq.common.UserThread;
import bisq.common.config.Config;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.net.InetSocketAddress;

import java.io.IOException;
import java.io.UncheckedIOException;

import lombok.extern.slf4j.Slf4j;

import static io.grpc.ServerInterceptors.interceptForward;



import bisq.daemon.grpc.interceptor.PasswordAuthInterceptor;

@Singleton
@Slf4j
public class GrpcServer {

    private final Server server;

    @Inject
    public GrpcServer(CoreContext coreContext,
                      Config config,
                      PasswordAuthInterceptor passwordAuthInterceptor,
                      GrpcDaoService daoService,
                      GrpcDisputeAgentsService disputeAgentsService,
                      GrpcHelpService helpService,
                      GrpcOffersService offersService,
                      GrpcPaymentAccountsService paymentAccountsService,
                      GrpcPriceService priceService,
                      GrpcShutdownService shutdownService,
                      GrpcVersionService versionService,
                      GrpcTradesService tradesService,
                      GrpcWalletsService walletsService) {
        InetSocketAddress listenAddress = new InetSocketAddress(config.apiHost, config.apiPort);
        if (!listenAddress.isUnresolved() && !listenAddress.getAddress().isLoopbackAddress()) {
            log.warn("The gRPC API listens on {} without TLS. Other hosts can reach it and can read the API " +
                    "password on the network.", listenAddress);
        }
        this.server = NettyServerBuilder.forAddress(listenAddress)
                .executor(UserThread.getExecutor())
                .addService(interceptForward(daoService, daoService.interceptors()))
                .addService(interceptForward(disputeAgentsService, disputeAgentsService.interceptors()))
                .addService(interceptForward(helpService, helpService.interceptors()))
                .addService(interceptForward(offersService, offersService.interceptors()))
                .addService(interceptForward(paymentAccountsService, paymentAccountsService.interceptors()))
                .addService(interceptForward(priceService, priceService.interceptors()))
                .addService(shutdownService)
                .addService(interceptForward(tradesService, tradesService.interceptors()))
                .addService(interceptForward(versionService, versionService.interceptors()))
                .addService(interceptForward(walletsService, walletsService.interceptors()))
                .intercept(passwordAuthInterceptor)
                .build();
        coreContext.setApiUser(true);
    }

    public void start() {
        try {
            server.start();
            log.info("listening on {}", server.getListenSockets());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public void shutdown() {
        log.info("Server shutdown started");
        server.shutdown();
        log.info("Server shutdown complete");
    }
}
