package bisq.core.grpc;

import bisq.proto.grpc.CallServiceGrpc;
import bisq.proto.grpc.Params;
import bisq.proto.grpc.Response;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import static bisq.core.grpc.PasswordAuthInterceptor.HTTP1_REQUEST_CTX_KEY;

public class GrpcCallService extends CallServiceGrpc.CallServiceImplBase {

    private final GrpcCoreBridge bridge;

    @Inject
    public GrpcCallService(GrpcCoreBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void call(Params req, StreamObserver<Response> responseObserver) {
        try {
            boolean isGatewayRequest = HTTP1_REQUEST_CTX_KEY.get(Context.current());
            String result = bridge.call(req.getParams(), isGatewayRequest);
            var reply = Response.newBuilder().setResult(result).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException ex) {
            responseObserver.onError(ex);
            throw ex;
        } catch (RuntimeException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }
}
