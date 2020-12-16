package bisq.daemon.grpc;

import bisq.core.api.CoreApi;

import bisq.proto.grpc.DisputeAgentsGrpc;
import bisq.proto.grpc.RegisterDisputeAgentReply;
import bisq.proto.grpc.RegisterDisputeAgentRequest;

import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class GrpcDisputeAgentsService extends DisputeAgentsGrpc.DisputeAgentsImplBase {

    private final CoreApi coreApi;
    private final CoreApiExceptionHandler exceptionHandler;

    @Inject
    public GrpcDisputeAgentsService(CoreApi coreApi, CoreApiExceptionHandler exceptionHandler) {
        this.coreApi = coreApi;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void registerDisputeAgent(RegisterDisputeAgentRequest req,
                                     StreamObserver<RegisterDisputeAgentReply> responseObserver) {
        try {
            coreApi.registerDisputeAgent(req.getDisputeAgentType(), req.getRegistrationKey());
            var reply = RegisterDisputeAgentReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(cause, responseObserver);
        }
    }
}
