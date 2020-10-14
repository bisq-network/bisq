package bisq.daemon.grpc;

import bisq.core.api.CoreApi;

import bisq.proto.grpc.DisputeAgentsGrpc;
import bisq.proto.grpc.RegisterDisputeAgentReply;
import bisq.proto.grpc.RegisterDisputeAgentRequest;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class GrpcDisputeAgentsService extends DisputeAgentsGrpc.DisputeAgentsImplBase {

    private final CoreApi coreApi;

    @Inject
    public GrpcDisputeAgentsService(CoreApi coreApi) {
        this.coreApi = coreApi;
    }

    @Override
    public void registerDisputeAgent(RegisterDisputeAgentRequest req,
                                     StreamObserver<RegisterDisputeAgentReply> responseObserver) {
        try {
            coreApi.registerDisputeAgent(req.getDisputeAgentType(), req.getRegistrationKey());
            var reply = RegisterDisputeAgentReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException cause) {
            var ex = new StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        } catch (IllegalStateException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }
}
