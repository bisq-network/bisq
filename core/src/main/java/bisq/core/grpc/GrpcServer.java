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
import bisq.proto.grpc.LockWalletGrpc;
import bisq.proto.grpc.LockWalletReply;
import bisq.proto.grpc.LockWalletRequest;
import bisq.proto.grpc.PlaceOfferGrpc;
import bisq.proto.grpc.PlaceOfferReply;
import bisq.proto.grpc.PlaceOfferRequest;
import bisq.proto.grpc.RemoveWalletPasswordGrpc;
import bisq.proto.grpc.RemoveWalletPasswordReply;
import bisq.proto.grpc.RemoveWalletPasswordRequest;
import bisq.proto.grpc.SetWalletPasswordGrpc;
import bisq.proto.grpc.SetWalletPasswordReply;
import bisq.proto.grpc.SetWalletPasswordRequest;
import bisq.proto.grpc.UnlockWalletGrpc;
import bisq.proto.grpc.UnlockWalletReply;
import bisq.proto.grpc.UnlockWalletRequest;

import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;

import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GrpcServer {

    private final CoreApi coreApi;
    private final int port;

    public GrpcServer(Config config, CoreApi coreApi) {
        this.coreApi = coreApi;
        this.port = config.apiPort;

        try {
            var server = ServerBuilder.forPort(port)
                    .addService(new GetVersionService())
                    .addService(new GetBalanceService())
                    .addService(new GetTradeStatisticsService())
                    .addService(new GetOffersService())
                    .addService(new GetPaymentAccountsService())
                    .addService(new LockWalletService())
                    .addService(new PlaceOfferService())
                    .addService(new RemoveWalletPasswordService())
                    .addService(new SetWalletPasswordService())
                    .addService(new UnlockWalletService())
                    .intercept(new PasswordAuthInterceptor(config.apiPassword))
                    .build()
                    .start();

            log.info("listening on port {}", port);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.shutdown();
                log.info("shutdown complete");
            }));

        } catch (IOException e) {
            log.error(e.toString(), e);
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

    class GetBalanceService extends GetBalanceGrpc.GetBalanceImplBase {
        @Override
        public void getBalance(GetBalanceRequest req, StreamObserver<GetBalanceReply> responseObserver) {
            var result = coreApi.getAvailableBalance();
            var reply = GetBalanceReply.newBuilder().setBalance(result.first).setErrorMessage(result.second).build();
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

    class RemoveWalletPasswordService extends RemoveWalletPasswordGrpc.RemoveWalletPasswordImplBase {
        @Override
        public void removeWalletPassword(RemoveWalletPasswordRequest req,
                                         StreamObserver<RemoveWalletPasswordReply> responseObserver) {
            var result = coreApi.removeWalletPassword(req.getPassword());
            var reply = RemoveWalletPasswordReply.newBuilder()
                    .setSuccess(result.first).setErrorMessage(result.second).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    class SetWalletPasswordService extends SetWalletPasswordGrpc.SetWalletPasswordImplBase {
        @Override
        public void setWalletPassword(SetWalletPasswordRequest req,
                                      StreamObserver<SetWalletPasswordReply> responseObserver) {
            var result = coreApi.setWalletPassword(req.getPassword(), req.getNewPassword());
            var reply = SetWalletPasswordReply.newBuilder()
                    .setSuccess(result.first).setErrorMessage(result.second).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    class LockWalletService extends LockWalletGrpc.LockWalletImplBase {
        @Override
        public void lockWallet(LockWalletRequest req,
                               StreamObserver<LockWalletReply> responseObserver) {
            var result = coreApi.lockWallet();
            var reply = LockWalletReply.newBuilder()
                    .setSuccess(result.first).setErrorMessage(result.second).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    class UnlockWalletService extends UnlockWalletGrpc.UnlockWalletImplBase {
        @Override
        public void unlockWallet(UnlockWalletRequest req,
                                 StreamObserver<UnlockWalletReply> responseObserver) {
            var result = coreApi.unlockWallet(req.getPassword(), req.getTimeout());
            var reply = UnlockWalletReply.newBuilder()
                    .setSuccess(result.first).setErrorMessage(result.second).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }
}
