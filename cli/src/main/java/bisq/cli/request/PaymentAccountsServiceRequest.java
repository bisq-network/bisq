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

package bisq.cli.request;

import bisq.proto.grpc.CreateCryptoCurrencyPaymentAccountRequest;
import bisq.proto.grpc.CreatePaymentAccountRequest;
import bisq.proto.grpc.GetCryptoCurrencyPaymentMethodsRequest;
import bisq.proto.grpc.GetPaymentAccountFormRequest;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.GetPaymentMethodsRequest;

import protobuf.PaymentAccount;
import protobuf.PaymentMethod;

import java.util.List;

import static java.lang.String.format;



import bisq.cli.GrpcStubs;

public class PaymentAccountsServiceRequest {

    private final GrpcStubs grpcStubs;

    public PaymentAccountsServiceRequest(GrpcStubs grpcStubs) {
        this.grpcStubs = grpcStubs;
    }

    public List<PaymentMethod> getPaymentMethods() {
        var request = GetPaymentMethodsRequest.newBuilder().build();
        return grpcStubs.paymentAccountsService.getPaymentMethods(request).getPaymentMethodsList();
    }

    public String getPaymentAcctFormAsJson(String paymentMethodId) {
        var request = GetPaymentAccountFormRequest.newBuilder()
                .setPaymentMethodId(paymentMethodId)
                .build();
        return grpcStubs.paymentAccountsService.getPaymentAccountForm(request).getPaymentAccountFormJson();
    }

    public PaymentAccount createPaymentAccount(String json) {
        var request = CreatePaymentAccountRequest.newBuilder()
                .setPaymentAccountForm(json)
                .build();
        return grpcStubs.paymentAccountsService.createPaymentAccount(request).getPaymentAccount();
    }

    public List<PaymentAccount> getPaymentAccounts() {
        var request = GetPaymentAccountsRequest.newBuilder().build();
        return grpcStubs.paymentAccountsService.getPaymentAccounts(request).getPaymentAccountsList();
    }

    /**
     * Returns the first PaymentAccount found with the given name, or throws an
     * IllegalArgumentException if not found.  This method should be used with care;
     * it will only return one PaymentAccount, and the account name must be an exact
     * match on the name argument.
     * @param accountName the name of the stored PaymentAccount to retrieve
     * @return PaymentAccount with given name
     */
    public PaymentAccount getPaymentAccount(String accountName) {
        return getPaymentAccounts().stream()
                .filter(a -> a.getAccountName().equals(accountName)).findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(format("payment account with name '%s' not found",
                                accountName)));
    }

    public PaymentAccount createCryptoCurrencyPaymentAccount(String accountName,
                                                             String currencyCode,
                                                             String address,
                                                             boolean tradeInstant) {
        var request = CreateCryptoCurrencyPaymentAccountRequest.newBuilder()
                .setAccountName(accountName)
                .setCurrencyCode(currencyCode)
                .setAddress(address)
                .setTradeInstant(tradeInstant)
                .build();
        return grpcStubs.paymentAccountsService.createCryptoCurrencyPaymentAccount(request).getPaymentAccount();
    }

    public List<PaymentMethod> getCryptoPaymentMethods() {
        var request = GetCryptoCurrencyPaymentMethodsRequest.newBuilder().build();
        return grpcStubs.paymentAccountsService.getCryptoCurrencyPaymentMethods(request).getPaymentMethodsList();
    }
}
