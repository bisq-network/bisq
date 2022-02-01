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
import bisq.core.api.model.CanceledTradeInfo;
import bisq.core.api.model.TradeInfo;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import bisq.proto.grpc.CloseTradeReply;
import bisq.proto.grpc.CloseTradeRequest;
import bisq.proto.grpc.ConfirmPaymentReceivedReply;
import bisq.proto.grpc.ConfirmPaymentReceivedRequest;
import bisq.proto.grpc.ConfirmPaymentStartedReply;
import bisq.proto.grpc.ConfirmPaymentStartedRequest;
import bisq.proto.grpc.FailTradeReply;
import bisq.proto.grpc.FailTradeRequest;
import bisq.proto.grpc.GetTradeReply;
import bisq.proto.grpc.GetTradeRequest;
import bisq.proto.grpc.GetTradesReply;
import bisq.proto.grpc.GetTradesRequest;
import bisq.proto.grpc.TakeOfferReply;
import bisq.proto.grpc.TakeOfferRequest;
import bisq.proto.grpc.UnFailTradeReply;
import bisq.proto.grpc.UnFailTradeRequest;
import bisq.proto.grpc.WithdrawFundsReply;
import bisq.proto.grpc.WithdrawFundsRequest;

import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.api.model.TradeInfo.toNewTradeInfo;
import static bisq.core.api.model.TradeInfo.toTradeInfo;
import static bisq.core.trade.model.bsq_swap.BsqSwapTrade.State.COMPLETED;
import static bisq.daemon.grpc.interceptor.GrpcServiceRateMeteringConfig.getCustomRateMeteringInterceptor;
import static bisq.proto.grpc.GetTradesRequest.Category.CLOSED;
import static bisq.proto.grpc.GetTradesRequest.Category.OPEN;
import static bisq.proto.grpc.TradesGrpc.*;
import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.daemon.grpc.interceptor.CallRateMeteringInterceptor;
import bisq.daemon.grpc.interceptor.GrpcCallRateMeter;

@Slf4j
class GrpcTradesService extends TradesImplBase {

    private final CoreApi coreApi;
    private final GrpcExceptionHandler exceptionHandler;

    @Inject
    public GrpcTradesService(CoreApi coreApi, GrpcExceptionHandler exceptionHandler) {
        this.coreApi = coreApi;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void takeOffer(TakeOfferRequest req,
                          StreamObserver<TakeOfferReply> responseObserver) {
        GrpcErrorMessageHandler errorMessageHandler =
                new GrpcErrorMessageHandler(getTakeOfferMethod().getFullMethodName(),
                        responseObserver,
                        exceptionHandler,
                        log);

        if (coreApi.isBsqSwapOffer(req.getOfferId(), false)) {
            coreApi.takeBsqSwapOffer(req.getOfferId(),
                    bsqSwapTrade -> {
                        var reply = buildTakeOfferReply(bsqSwapTrade);
                        responseObserver.onNext(reply);
                        responseObserver.onCompleted();
                    },
                    errorMessage -> {
                        if (!errorMessageHandler.isErrorHandled())
                            errorMessageHandler.handleErrorMessage(errorMessage);
                    });
        } else {
            coreApi.takeOffer(req.getOfferId(),
                    req.getPaymentAccountId(),
                    req.getTakerFeeCurrencyCode(),
                    trade -> {
                        var reply = buildTakeOfferReply(trade);
                        responseObserver.onNext(reply);
                        responseObserver.onCompleted();
                    },
                    errorMessage -> {
                        if (!errorMessageHandler.isErrorHandled())
                            errorMessageHandler.handleErrorMessage(errorMessage);
                    });
        }
    }

    @Override
    public void getTrade(GetTradeRequest req,
                         StreamObserver<GetTradeReply> responseObserver) {
        try {
            var tradeModel = coreApi.getTradeModel(req.getTradeId());
            var reply = tradeModel.getOffer().isBsqSwapOffer()
                    ? buildGetTradeReply((BsqSwapTrade) tradeModel)
                    : buildGetTradeReply((Trade) tradeModel);
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException cause) {
            // Offer makers may call 'gettrade' many times before a trade exists.
            // Log a 'trade not found' warning instead of a full stack trace.
            exceptionHandler.handleExceptionAsWarning(log, "getTrade", cause, responseObserver);
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void getTrades(GetTradesRequest req,
                          StreamObserver<GetTradesReply> responseObserver) {
        try {
            var category = req.getCategory();
            var trades = category.equals(OPEN)
                    ? coreApi.getOpenTrades()
                    : coreApi.getTradeHistory(category);
            var reply = buildGetTradesReply(trades, category);
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException cause) {
            exceptionHandler.handleExceptionAsWarning(log, "getTrades", cause, responseObserver);
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void confirmPaymentStarted(ConfirmPaymentStartedRequest req,
                                      StreamObserver<ConfirmPaymentStartedReply> responseObserver) {
        try {
            coreApi.confirmPaymentStarted(req.getTradeId());
            var reply = ConfirmPaymentStartedReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void confirmPaymentReceived(ConfirmPaymentReceivedRequest req,
                                       StreamObserver<ConfirmPaymentReceivedReply> responseObserver) {
        try {
            coreApi.confirmPaymentReceived(req.getTradeId());
            var reply = ConfirmPaymentReceivedReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void closeTrade(CloseTradeRequest req,
                           StreamObserver<CloseTradeReply> responseObserver) {
        try {
            coreApi.closeTrade(req.getTradeId());
            var reply = CloseTradeReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void withdrawFunds(WithdrawFundsRequest req,
                              StreamObserver<WithdrawFundsReply> responseObserver) {
        try {
            coreApi.withdrawFunds(req.getTradeId(), req.getAddress(), req.getMemo());
            var reply = WithdrawFundsReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void failTrade(FailTradeRequest req,
                          StreamObserver<FailTradeReply> responseObserver) {
        try {
            coreApi.failTrade(req.getTradeId());
            var reply = FailTradeReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void unFailTrade(UnFailTradeRequest req,
                            StreamObserver<UnFailTradeReply> responseObserver) {
        try {
            coreApi.unFailTrade(req.getTradeId());
            var reply = UnFailTradeReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    final ServerInterceptor[] interceptors() {
        Optional<ServerInterceptor> rateMeteringInterceptor = rateMeteringInterceptor();
        return rateMeteringInterceptor.map(serverInterceptor ->
                new ServerInterceptor[]{serverInterceptor}).orElseGet(() -> new ServerInterceptor[0]);
    }

    final Optional<ServerInterceptor> rateMeteringInterceptor() {
        return getCustomRateMeteringInterceptor(coreApi.getConfig().appDataDir, this.getClass())
                .or(() -> Optional.of(CallRateMeteringInterceptor.valueOf(
                        new HashMap<>() {{
                            put(getGetTradeMethod().getFullMethodName(), new GrpcCallRateMeter(1, SECONDS));
                            put(getGetTradesMethod().getFullMethodName(), new GrpcCallRateMeter(1, SECONDS));
                            put(getTakeOfferMethod().getFullMethodName(), new GrpcCallRateMeter(1, MINUTES));
                            put(getConfirmPaymentStartedMethod().getFullMethodName(), new GrpcCallRateMeter(1, MINUTES));
                            put(getConfirmPaymentReceivedMethod().getFullMethodName(), new GrpcCallRateMeter(1, MINUTES));
                            put(getCloseTradeMethod().getFullMethodName(), new GrpcCallRateMeter(1, MINUTES));
                            put(getWithdrawFundsMethod().getFullMethodName(), new GrpcCallRateMeter(1, MINUTES));
                        }}
                )));
    }

    private TakeOfferReply buildTakeOfferReply(TradeModel tradeModel) {
        TradeInfo tradeInfo;
        if (tradeModel.getOffer().isBsqSwapOffer()) {
            BsqSwapTrade bsqSwapTrade = (BsqSwapTrade) tradeModel;
            String role = getMyRole(bsqSwapTrade);
            tradeInfo = toNewTradeInfo(bsqSwapTrade, role);
        } else {
            tradeInfo = toNewTradeInfo((Trade) tradeModel);
        }
        return TakeOfferReply.newBuilder()
                .setTrade(tradeInfo.toProtoMessage())
                .build();
    }

    private GetTradeReply buildGetTradeReply(BsqSwapTrade bsqSwapTrade) {
        boolean wasMyOffer = wasMyOffer(bsqSwapTrade);
        String role = getMyRole(bsqSwapTrade);
        var numConfirmations = coreApi.getTransactionConfirmations(bsqSwapTrade.getTxId());
        var closingStatus = bsqSwapTrade.getTradeState().equals(COMPLETED)
                ? coreApi.getClosedTradeStateAsString(bsqSwapTrade)
                : "Pending";
        var tradeInfo = toTradeInfo(bsqSwapTrade,
                role,
                wasMyOffer,
                numConfirmations,
                closingStatus);
        return GetTradeReply.newBuilder()
                .setTrade(tradeInfo.toProtoMessage())
                .build();
    }

    private GetTradeReply buildGetTradeReply(Trade trade) {
        boolean wasMyOffer = wasMyOffer(trade);
        String role = getMyRole(trade);
        var closingStatus = trade.isCompleted()
                ? coreApi.getClosedTradeStateAsString(trade)
                : "Pending";
        return GetTradeReply.newBuilder()
                .setTrade(toTradeInfo(trade,
                        role,
                        wasMyOffer,
                        closingStatus).toProtoMessage())
                .build();
    }


    private GetTradesReply buildGetTradesReply(List<TradeModel> trades, GetTradesRequest.Category category) {
        // Build an unsorted List<TradeInfo>, starting with
        // all pending, or all completed BsqSwap and v1 trades.
        List<TradeInfo> unsortedTrades = trades.stream()
                .map(tradeModel -> {
                    var role = coreApi.getTradeRole(tradeModel);
                    var isMyOffer = coreApi.isMyOffer(tradeModel.getOffer());
                    var isBsqSwapTrade = tradeModel instanceof BsqSwapTrade;
                    var numConfirmations = isBsqSwapTrade
                            ? coreApi.getTransactionConfirmations(((BsqSwapTrade) tradeModel).getTxId())
                            : 0;
                    var closingStatus = category.equals(OPEN)
                            ? "Pending"
                            : coreApi.getClosedTradeStateAsString(tradeModel);
                    return isBsqSwapTrade
                            ? toTradeInfo((BsqSwapTrade) tradeModel, role, isMyOffer, numConfirmations, closingStatus)
                            : toTradeInfo(tradeModel, role, isMyOffer, closingStatus);
                })
                .collect(Collectors.toList());

        // If closed trades were requested, add any canceled
        // OpenOffers (canceled trades) to the unsorted List<TradeInfo>.
        Optional<List<OpenOffer>> canceledOpenOffers = category.equals(CLOSED)
                ? Optional.of(coreApi.getCanceledOpenOffers())
                : Optional.empty();
        List<TradeInfo> canceledTrades = new ArrayList<>();
        canceledOpenOffers.ifPresent(openOffers -> canceledTrades.addAll(
                openOffers.stream()
                        .map(CanceledTradeInfo::toCanceledTradeInfo)
                        .collect(Collectors.toList())
        ));
        unsortedTrades.addAll(canceledTrades);

        // Sort the cumulative List<TradeInfo> by date before sending it to the client.
        List<TradeInfo> sortedTrades = unsortedTrades.stream()
                .sorted(comparing(TradeInfo::getDate))
                .collect(Collectors.toList());

        return GetTradesReply.newBuilder()
                .addAllTrades(sortedTrades.stream()
                        .map(TradeInfo::toProtoMessage)
                        .collect(Collectors.toList()))
                .build();
    }

    private boolean wasMyOffer(TradeModel tradeModel) {
        return coreApi.isMyOffer(tradeModel.getOffer());
    }

    private String getMyRole(TradeModel tradeModel) {
        return coreApi.getTradeRole(tradeModel);
    }
}
