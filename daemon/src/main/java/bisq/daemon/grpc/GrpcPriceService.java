package bisq.daemon.grpc;

import bisq.core.api.CoreApi;

import bisq.proto.grpc.MarketPriceReply;
import bisq.proto.grpc.MarketPriceRequest;
import bisq.proto.grpc.PriceGrpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class GrpcPriceService extends PriceGrpc.PriceImplBase {

    private final CoreApi coreApi;

    @Inject
    public GrpcPriceService(CoreApi coreApi) {
        this.coreApi = coreApi;
    }

    @Override
    public void getMarketPrice(MarketPriceRequest req,
                               StreamObserver<MarketPriceReply> responseObserver) {
        try {
            double price = coreApi.getMarketPrice(req.getCurrencyCode());
            var reply = MarketPriceReply.newBuilder().setPrice(price).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalStateException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }
}
