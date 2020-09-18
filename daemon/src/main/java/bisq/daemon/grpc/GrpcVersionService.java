package bisq.daemon.grpc;

import bisq.core.api.CoreApi;

import bisq.proto.grpc.GetVersionGrpc;
import bisq.proto.grpc.GetVersionReply;
import bisq.proto.grpc.GetVersionRequest;

import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

class GrpcVersionService extends GetVersionGrpc.GetVersionImplBase {

    private final CoreApi coreApi;

    @Inject
    public GrpcVersionService(CoreApi coreApi) {
        this.coreApi = coreApi;
    }

    @Override
    public void getVersion(GetVersionRequest req, StreamObserver<GetVersionReply> responseObserver) {
        var reply = GetVersionReply.newBuilder().setVersion(coreApi.getVersion()).build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
