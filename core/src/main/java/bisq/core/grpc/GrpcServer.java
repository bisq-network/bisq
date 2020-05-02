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

package bisq.core.grpc;

import bisq.core.offer.Offer;
import bisq.core.payment.PaymentAccount;
import bisq.core.trade.handlers.TransactionResultHandler;
import bisq.core.trade.statistics.TradeStatistics2;

import bisq.common.config.Config;

import bisq.proto.grpc.GetOffersGrpc;
import bisq.proto.grpc.GetOffersReply;
import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.GetPaymentAccountsGrpc;
import bisq.proto.grpc.GetPaymentAccountsReply;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.GetTradeStatisticsGrpc;
import bisq.proto.grpc.GetTradeStatisticsReply;
import bisq.proto.grpc.GetTradeStatisticsRequest;
import bisq.proto.grpc.GetVersionGrpc;
import bisq.proto.grpc.GetVersionReply;
import bisq.proto.grpc.GetVersionRequest;
import bisq.proto.grpc.PlaceOfferGrpc;
import bisq.proto.grpc.PlaceOfferReply;
import bisq.proto.grpc.PlaceOfferRequest;

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
    public GrpcServer(Config config, CoreApi coreApi, GrpcWalletService walletService) {
        this.coreApi = coreApi;
        this.server = ServerBuilder.forPort(config.apiPort)
                .addService(new GetVersionService())
                .addService(new GetTradeStatisticsService())
                .addService(new GetOffersService())
                .addService(new GetPaymentAccountsService())
                .addService(new PlaceOfferService())
                .addService(walletService)
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

    class GetOffersService extends GetOffersGrpc.GetOffersImplBase {
        @Override
        public void getOffers(GetOffersRequest req, StreamObserver<GetOffersReply> responseObserver) {

            var tradeStatistics = coreApi.getOffers().stream()
                    .map(Offer::toProtoMessage)
                    .collect(Collectors.toList());

            var reply = GetOffersReply.newBuilder().addAllOffers(tradeStatistics).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    class GetPaymentAccountsService extends GetPaymentAccountsGrpc.GetPaymentAccountsImplBase {
        @Override
        public void getPaymentAccounts(GetPaymentAccountsRequest req,
                                       StreamObserver<GetPaymentAccountsReply> responseObserver) {

            var tradeStatistics = coreApi.getPaymentAccounts().stream()
                    .map(PaymentAccount::toProtoMessage)
                    .collect(Collectors.toList());

            var reply = GetPaymentAccountsReply.newBuilder().addAllPaymentAccounts(tradeStatistics).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    class PlaceOfferService extends PlaceOfferGrpc.PlaceOfferImplBase {
        @Override
        public void placeOffer(PlaceOfferRequest req, StreamObserver<PlaceOfferReply> responseObserver) {
            TransactionResultHandler resultHandler = transaction -> {
                PlaceOfferReply reply = PlaceOfferReply.newBuilder().setResult(true).build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
            };
            coreApi.placeOffer(
                    req.getCurrencyCode(),
                    req.getDirection(),
                    req.getPrice(),
                    req.getUseMarketBasedPrice(),
                    req.getMarketPriceMargin(),
                    req.getAmount(),
                    req.getMinAmount(),
                    req.getBuyerSecurityDeposit(),
                    req.getPaymentAccountId(),
                    resultHandler);
        }
    }
}
