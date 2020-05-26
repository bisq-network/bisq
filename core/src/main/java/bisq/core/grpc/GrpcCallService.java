package bisq.core.grpc;

import bisq.proto.grpc.CallServiceGrpc;
import bisq.proto.grpc.Params;
import bisq.proto.grpc.Response;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

public class GrpcCallService extends CallServiceGrpc.CallServiceImplBase {

    private final GrpcCoreBridge bridge;

    @Inject
    public GrpcCallService(GrpcCoreBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void call(Params req, StreamObserver<Response> responseObserver) {
        try {
            // TODO Extract the gRPC Context Key HTTP1_REQUEST_CTX_KEY value and pass it
            //  to the GrpcCoreBridge, which will be responsible for wrapping the
            //  response in json if the HTTP1_REQUEST_CTX_KEY value == true.
            String result = bridge.call(req.getParams(), false /*todo*/);
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
