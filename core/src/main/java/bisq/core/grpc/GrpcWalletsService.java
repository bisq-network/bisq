package bisq.core.grpc;

import bisq.core.grpc.model.AddressBalanceInfo;

import bisq.proto.grpc.GetAddressBalanceReply;
import bisq.proto.grpc.GetAddressBalanceRequest;
import bisq.proto.grpc.GetBalanceReply;
import bisq.proto.grpc.GetBalanceRequest;
import bisq.proto.grpc.GetFundingAddressesReply;
import bisq.proto.grpc.GetFundingAddressesRequest;
import bisq.proto.grpc.LockWalletReply;
import bisq.proto.grpc.LockWalletRequest;
import bisq.proto.grpc.RemoveWalletPasswordReply;
import bisq.proto.grpc.RemoveWalletPasswordRequest;
import bisq.proto.grpc.SetWalletPasswordReply;
import bisq.proto.grpc.SetWalletPasswordRequest;
import bisq.proto.grpc.UnlockWalletReply;
import bisq.proto.grpc.UnlockWalletRequest;
import bisq.proto.grpc.WalletsGrpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

class GrpcWalletsService extends WalletsGrpc.WalletsImplBase {

    private final CoreApi coreApi;

    @Inject
    public GrpcWalletsService(CoreApi coreApi) {
        this.coreApi = coreApi;
    }

    @Override
    public void getBalance(GetBalanceRequest req, StreamObserver<GetBalanceReply> responseObserver) {
        try {
            long result = coreApi.getAvailableBalance();
            var reply = GetBalanceReply.newBuilder().setBalance(result).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalStateException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }

    @Override
    public void getAddressBalance(GetAddressBalanceRequest req,
                                  StreamObserver<GetAddressBalanceReply> responseObserver) {
        try {
            AddressBalanceInfo result = coreApi.getAddressBalanceInfo(req.getAddress());
            var reply = GetAddressBalanceReply.newBuilder().setAddressBalanceInfo(result.toProtoMessage()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalStateException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }

    @Override
    public void getFundingAddresses(GetFundingAddressesRequest req,
                                    StreamObserver<GetFundingAddressesReply> responseObserver) {
        try {
            List<AddressBalanceInfo> result = coreApi.getFundingAddresses();
            var reply = GetFundingAddressesReply.newBuilder()
                    .addAllAddressBalanceInfo(
                            result.stream()
                                    .map(AddressBalanceInfo::toProtoMessage)
                                    .collect(Collectors.toList()))
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalStateException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }

    @Override
    public void setWalletPassword(SetWalletPasswordRequest req,
                                  StreamObserver<SetWalletPasswordReply> responseObserver) {
        try {
            coreApi.setWalletPassword(req.getPassword(), req.getNewPassword());
            var reply = SetWalletPasswordReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalStateException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }

    @Override
    public void removeWalletPassword(RemoveWalletPasswordRequest req,
                                     StreamObserver<RemoveWalletPasswordReply> responseObserver) {
        try {
            coreApi.removeWalletPassword(req.getPassword());
            var reply = RemoveWalletPasswordReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalStateException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }

    @Override
    public void lockWallet(LockWalletRequest req,
                           StreamObserver<LockWalletReply> responseObserver) {
        try {
            coreApi.lockWallet();
            var reply = LockWalletReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalStateException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }

    @Override
    public void unlockWallet(UnlockWalletRequest req,
                             StreamObserver<UnlockWalletReply> responseObserver) {
        try {
            coreApi.unlockWallet(req.getPassword(), req.getTimeout());
            var reply = UnlockWalletReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (IllegalStateException cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }
}
