package bisq.core.grpc;

import bisq.core.payment.PaymentAccount;

import bisq.proto.grpc.CreatePaymentAccountReply;
import bisq.proto.grpc.CreatePaymentAccountRequest;
import bisq.proto.grpc.GetPaymentAccountsReply;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.PaymentAccountsGrpc;

import io.grpc.stub.StreamObserver;

import javax.inject.Inject;

import java.util.stream.Collectors;


public class GrpcPaymentAccountsService extends PaymentAccountsGrpc.PaymentAccountsImplBase {

    private final CoreApi coreApi;

    @Inject
    public GrpcPaymentAccountsService(CoreApi coreApi) {
        this.coreApi = coreApi;
    }

    @Override
    public void createPaymentAccount(CreatePaymentAccountRequest req,
                                     StreamObserver<CreatePaymentAccountReply> responseObserver) {
        coreApi.createPaymentAccount(req.getAccountName(), req.getAccountNumber(), req.getFiatCurrencyCode());
        var reply = CreatePaymentAccountReply.newBuilder().build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void getPaymentAccounts(GetPaymentAccountsRequest req,
                                   StreamObserver<GetPaymentAccountsReply> responseObserver) {
        var tradeStatistics = coreApi.getPaymentAccounts().stream()
                .map(PaymentAccount::toProtoMessage)
                .collect(Collectors.toList());
        var reply = GetPaymentAccountsReply.newBuilder().addAllPaymentAccounts(tradeStatistics).build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
