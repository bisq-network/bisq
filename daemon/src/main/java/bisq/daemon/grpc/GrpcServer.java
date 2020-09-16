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

import bisq.core.api.CoreApi;
import bisq.core.trade.statistics.TradeStatistics2;

import bisq.common.config.Config;

import bisq.proto.grpc.GetTradeStatisticsGrpc;
import bisq.proto.grpc.GetTradeStatisticsReply;
import bisq.proto.grpc.GetTradeStatisticsRequest;
import bisq.proto.grpc.GetVersionGrpc;
import bisq.proto.grpc.GetVersionReply;
import bisq.proto.grpc.GetVersionRequest;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GrpcServer {

    private final CoreApi coreApi;
    private final Server server;

    @Inject
    public GrpcServer(Config config,
                      CoreApi coreApi,
                      GrpcDisputeAgentsService disputeAgentsService,
                      GrpcOffersService offersService,
                      GrpcPaymentAccountsService paymentAccountsService,
                      GrpcWalletsService walletsService) {
        this.coreApi = coreApi;
        this.server = ServerBuilder.forPort(config.apiPort)
                .addService(disputeAgentsService)
                .addService(new GetVersionService())
                .addService(new GetTradeStatisticsService())
                .addService(offersService)
                .addService(paymentAccountsService)
                .addService(walletsService)
                .intercept(new PasswordAuthInterceptor(config.apiPassword))
                .build();
    }

    public void start() {
        try {
            server.start();
            log.info("listening on port {}", server.getPort());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.shutdown();
                log.info("shutdown complete");
            }));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    class GetVersionService extends GetVersionGrpc.GetVersionImplBase {
        @Override
        public void getVersion(GetVersionRequest req, StreamObserver<GetVersionReply> responseObserver) {
            var reply = GetVersionReply.newBuilder().setVersion(coreApi.getVersion()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    class GetTradeStatisticsService extends GetTradeStatisticsGrpc.GetTradeStatisticsImplBase {
        @Override
        public void getTradeStatistics(GetTradeStatisticsRequest req,
                                       StreamObserver<GetTradeStatisticsReply> responseObserver) {

            var tradeStatistics = coreApi.getTradeStatistics().stream()
                    .map(TradeStatistics2::toProtoTradeStatistics2)
                    .collect(Collectors.toList());

            var reply = GetTradeStatisticsReply.newBuilder().addAllTradeStatistics(tradeStatistics).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }
}
