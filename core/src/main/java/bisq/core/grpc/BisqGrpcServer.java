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

import bisq.proto.grpc.GetBalanceGrpc;
import bisq.proto.grpc.GetBalanceReply;
import bisq.proto.grpc.GetBalanceRequest;
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
import bisq.proto.grpc.StopServerGrpc;
import bisq.proto.grpc.StopServerReply;
import bisq.proto.grpc.StopServerRequest;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;


/**
 * gRPC server. Gets a instance of BisqFacade passed to access data from the running Bisq instance.
 */
@Slf4j
public class BisqGrpcServer {

    private Server server;

    private static BisqGrpcServer instance;
    private static CoreApi coreApi;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Services
    ///////////////////////////////////////////////////////////////////////////////////////////

    static class GetVersionImpl extends GetVersionGrpc.GetVersionImplBase {
        @Override
        public void getVersion(GetVersionRequest req, StreamObserver<GetVersionReply> responseObserver) {
            GetVersionReply reply = GetVersionReply.newBuilder().setVersion(coreApi.getVersion()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    static class GetBalanceImpl extends GetBalanceGrpc.GetBalanceImplBase {
        @Override
        public void getBalance(GetBalanceRequest req, StreamObserver<GetBalanceReply> responseObserver) {
            GetBalanceReply reply = GetBalanceReply.newBuilder().setBalance(coreApi.getAvailableBalance()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    static class GetTradeStatisticsImpl extends GetTradeStatisticsGrpc.GetTradeStatisticsImplBase {
        @Override
        public void getTradeStatistics(GetTradeStatisticsRequest req,
                                       StreamObserver<GetTradeStatisticsReply> responseObserver) {
            List<protobuf.TradeStatistics2> tradeStatistics = coreApi.getTradeStatistics().stream()
                    .map(TradeStatistics2::toProtoTradeStatistics2)
                    .collect(Collectors.toList());
            GetTradeStatisticsReply reply = GetTradeStatisticsReply.newBuilder().addAllTradeStatistics(tradeStatistics).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    static class GetOffersImpl extends GetOffersGrpc.GetOffersImplBase {
        @Override
        public void getOffers(GetOffersRequest req, StreamObserver<GetOffersReply> responseObserver) {

            List<protobuf.Offer> tradeStatistics = coreApi.getOffers().stream()
                    .map(Offer::toProtoMessage)
                    .collect(Collectors.toList());

            GetOffersReply reply = GetOffersReply.newBuilder().addAllOffers(tradeStatistics).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    static class GetPaymentAccountsImpl extends GetPaymentAccountsGrpc.GetPaymentAccountsImplBase {
        @Override
        public void getPaymentAccounts(GetPaymentAccountsRequest req,
                                       StreamObserver<GetPaymentAccountsReply> responseObserver) {

            List<protobuf.PaymentAccount> tradeStatistics = coreApi.getPaymentAccounts().stream()
                    .map(PaymentAccount::toProtoMessage)
                    .collect(Collectors.toList());

            GetPaymentAccountsReply reply = GetPaymentAccountsReply.newBuilder().addAllPaymentAccounts(tradeStatistics).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    static class PlaceOfferImpl extends PlaceOfferGrpc.PlaceOfferImplBase {
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

    static class StopServerImpl extends StopServerGrpc.StopServerImplBase {
        @Override
        public void stopServer(StopServerRequest req, StreamObserver<StopServerReply> responseObserver) {
            StopServerReply reply = StopServerReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();

            instance.stop();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BisqGrpcServer(CoreApi coreApi) {
        instance = this;

        BisqGrpcServer.coreApi = coreApi;

        try {
            start();

        } catch (IOException e) {
            log.error(e.toString(), e);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void start() throws IOException {
        // TODO add to options
        int port = 9998;

        // Config services
        server = ServerBuilder.forPort(port)
                .addService(new GetVersionImpl())
                .addService(new GetBalanceImpl())
                .addService(new GetTradeStatisticsImpl())
                .addService(new GetOffersImpl())
                .addService(new GetPaymentAccountsImpl())
                .addService(new PlaceOfferImpl())
                .addService(new StopServerImpl())
                .build()
                .start();

        log.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            log.error("*** shutting down gRPC server since JVM is shutting down");
            BisqGrpcServer.this.stop();
            log.error("*** server shut down");
        }));
    }
}
