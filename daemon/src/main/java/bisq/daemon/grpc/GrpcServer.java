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
import io.grpc.ServerBuilder;

import javax.inject.Inject;
import javax.inject.Singleton;

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
                      GrpcDisputeAgentsService disputeAgentsService,
                      GrpcHelpService helpService,
                      GrpcOffersService offersService,
                      GrpcPaymentAccountsService paymentAccountsService,
                      GrpcPriceService priceService,
                      GrpcShutdownService shutdownService,
                      GrpcVersionService versionService,
                      GrpcGetTradeStatisticsService tradeStatisticsService,
                      GrpcTradesService tradesService,
                      GrpcWalletsService walletsService) {
        this.server = ServerBuilder.forPort(config.apiPort)
                .executor(UserThread.getExecutor())
                .addService(interceptForward(disputeAgentsService, disputeAgentsService.interceptors()))
                .addService(interceptForward(helpService, helpService.interceptors()))
                .addService(interceptForward(offersService, offersService.interceptors()))
                .addService(interceptForward(paymentAccountsService, paymentAccountsService.interceptors()))
                .addService(interceptForward(priceService, priceService.interceptors()))
                .addService(shutdownService)
                .addService(interceptForward(tradeStatisticsService, tradeStatisticsService.interceptors()))
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
            log.info("listening on port {}", server.getPort());
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
