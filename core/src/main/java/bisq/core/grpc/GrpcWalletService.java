package bisq.core.grpc;

import bisq.proto.grpc.GetBalanceReply;
import bisq.proto.grpc.GetBalanceRequest;
import bisq.proto.grpc.LockWalletReply;
import bisq.proto.grpc.LockWalletRequest;
import bisq.proto.grpc.RemoveWalletPasswordReply;
import bisq.proto.grpc.RemoveWalletPasswordRequest;
import bisq.proto.grpc.SetWalletPasswordReply;
import bisq.proto.grpc.SetWalletPasswordRequest;
import bisq.proto.grpc.UnlockWalletReply;
import bisq.proto.grpc.UnlockWalletRequest;
import bisq.proto.grpc.WalletGrpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

class GrpcWalletService extends WalletGrpc.WalletImplBase {

    private final CoreWalletService walletService;

    @Inject
    public GrpcWalletService(CoreWalletService walletService) {
        this.walletService = walletService;
    }

    @Override
    public void getBalance(GetBalanceRequest req, StreamObserver<GetBalanceReply> responseObserver) {
        try {
            long result = walletService.getAvailableBalance();
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
    public void setWalletPassword(SetWalletPasswordRequest req,
                                  StreamObserver<SetWalletPasswordReply> responseObserver) {
        try {
            walletService.setWalletPassword(req.getPassword(), req.getNewPassword());
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
            walletService.removeWalletPassword(req.getPassword());
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
            walletService.lockWallet();
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
            walletService.unlockWallet(req.getPassword(), req.getTimeout());
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
