package bisq.daemon.grpc;

import bisq.core.api.CoreApi;
import bisq.core.trade.statistics.TradeStatistics3;

import bisq.proto.grpc.GetTradeStatisticsGrpc;
import bisq.proto.grpc.GetTradeStatisticsReply;
import bisq.proto.grpc.GetTradeStatisticsRequest;

import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.stream.Collectors;

class GrpcGetTradeStatisticsService extends GetTradeStatisticsGrpc.GetTradeStatisticsImplBase {

    private final CoreApi coreApi;
    private final GrpcExceptionHandler exceptionHandler;

    @Inject
    public GrpcGetTradeStatisticsService(CoreApi coreApi, GrpcExceptionHandler exceptionHandler) {
        this.coreApi = coreApi;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void getTradeStatistics(GetTradeStatisticsRequest req,
                                   StreamObserver<GetTradeStatisticsReply> responseObserver) {
        try {
            var tradeStatistics = coreApi.getTradeStatistics().stream()
                    .map(TradeStatistics3::toProtoTradeStatistics3)
                    .collect(Collectors.toList());

            var reply = GetTradeStatisticsReply.newBuilder().addAllTradeStatistics(tradeStatistics).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(cause, responseObserver);
        }
    }
}
