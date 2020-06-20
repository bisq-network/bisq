/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.grpc;

import bisq.core.payment.PaymentAccount;

import bisq.proto.grpc.CreatePaymentAccountReply;
import bisq.proto.grpc.CreatePaymentAccountRequest;
import bisq.proto.grpc.GetPaymentAccountsReply;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.PaymentAccountsGrpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
        try {
            coreApi.createPaymentAccount(req.getAccountName(), req.getAccountNumber(), req.getFiatCurrencyCode());
            var reply = CreatePaymentAccountReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Exception cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }

    @Override
    public void getPaymentAccounts(GetPaymentAccountsRequest req,
                                   StreamObserver<GetPaymentAccountsReply> responseObserver) {
        try {
            var tradeStatistics = coreApi.getPaymentAccounts().stream()
                    .map(PaymentAccount::toProtoMessage)
                    .collect(Collectors.toList());
            var reply = GetPaymentAccountsReply.newBuilder().addAllPaymentAccounts(tradeStatistics).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Exception cause) {
            var ex = new StatusRuntimeException(Status.UNKNOWN.withDescription(cause.getMessage()));
            responseObserver.onError(ex);
            throw ex;
        }
    }
}
