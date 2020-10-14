package bisq.daemon.grpc;

import bisq.core.api.CoreApi;
import bisq.core.trade.statistics.TradeStatistics2;

import bisq.proto.grpc.GetTradeStatisticsGrpc;
import bisq.proto.grpc.GetTradeStatisticsReply;
import bisq.proto.grpc.GetTradeStatisticsRequest;

import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.stream.Collectors;

class GrpcGetTradeStatisticsService extends GetTradeStatisticsGrpc.GetTradeStatisticsImplBase {

    private final CoreApi coreApi;

    @Inject
    public GrpcGetTradeStatisticsService(CoreApi coreApi) {
        this.coreApi = coreApi;
    }

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
