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

package bisq.apitest.method;

import bisq.proto.grpc.CreatePaymentAccountRequest;
import bisq.proto.grpc.GetBalanceRequest;
import bisq.proto.grpc.GetFundingAddressesRequest;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.LockWalletRequest;
import bisq.proto.grpc.MarketPriceRequest;
import bisq.proto.grpc.RegisterDisputeAgentRequest;
import bisq.proto.grpc.RemoveWalletPasswordRequest;
import bisq.proto.grpc.SetWalletPasswordRequest;
import bisq.proto.grpc.UnlockWalletRequest;

import protobuf.PaymentAccount;

import java.util.stream.Collectors;

import static bisq.common.app.DevEnv.DEV_PRIVILEGE_PRIV_KEY;
import static bisq.core.payment.payload.PaymentMethod.PERFECT_MONEY;
import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.assertEquals;



import bisq.apitest.ApiTestCase;
import bisq.apitest.config.BisqAppConfig;

public class MethodTest extends ApiTestCase {

    protected static final String ARBITRATOR = "arbitrator";
    protected static final String MEDIATOR = "mediator";
    protected static final String REFUND_AGENT = "refundagent";

    // Convenience methods for building gRPC request objects

    protected final GetBalanceRequest createBalanceRequest() {
        return GetBalanceRequest.newBuilder().build();
    }

    protected final SetWalletPasswordRequest createSetWalletPasswordRequest(String password) {
        return SetWalletPasswordRequest.newBuilder().setPassword(password).build();
    }

    protected final SetWalletPasswordRequest createSetWalletPasswordRequest(String oldPassword, String newPassword) {
        return SetWalletPasswordRequest.newBuilder().setPassword(oldPassword).setNewPassword(newPassword).build();
    }

    protected final RemoveWalletPasswordRequest createRemoveWalletPasswordRequest(String password) {
        return RemoveWalletPasswordRequest.newBuilder().setPassword(password).build();
    }

    protected final UnlockWalletRequest createUnlockWalletRequest(String password, long timeout) {
        return UnlockWalletRequest.newBuilder().setPassword(password).setTimeout(timeout).build();
    }

    protected final LockWalletRequest createLockWalletRequest() {
        return LockWalletRequest.newBuilder().build();
    }

    protected final GetFundingAddressesRequest createGetFundingAddressesRequest() {
        return GetFundingAddressesRequest.newBuilder().build();
    }

    protected final MarketPriceRequest createMarketPriceRequest(String currencyCode) {
        return MarketPriceRequest.newBuilder().setCurrencyCode(currencyCode).build();
    }

    // Convenience methods for calling frequently used & thoroughly tested gRPC services.

    protected final long getBalance(BisqAppConfig bisqAppConfig) {
        return grpcStubs(bisqAppConfig).walletsService.getBalance(createBalanceRequest()).getBalance();
    }

    protected final void unlockWallet(BisqAppConfig bisqAppConfig, String password, long timeout) {
        //noinspection ResultOfMethodCallIgnored
        grpcStubs(bisqAppConfig).walletsService.unlockWallet(createUnlockWalletRequest(password, timeout));
    }

    protected final void lockWallet(BisqAppConfig bisqAppConfig) {
        //noinspection ResultOfMethodCallIgnored
        grpcStubs(bisqAppConfig).walletsService.lockWallet(createLockWalletRequest());
    }

    protected final String getUnusedBtcAddress(BisqAppConfig bisqAppConfig) {
        //noinspection OptionalGetWithoutIsPresent
        return grpcStubs(bisqAppConfig).walletsService.getFundingAddresses(createGetFundingAddressesRequest())
                .getAddressBalanceInfoList()
                .stream()
                .filter(a -> a.getBalance() == 0 && a.getNumConfirmations() == 0)
                .findFirst()
                .get()
                .getAddress();
    }

    protected final CreatePaymentAccountRequest createCreatePerfectMoneyPaymentAccountRequest(
            String accountName,
            String accountNumber,
            String currencyCode) {
        return CreatePaymentAccountRequest.newBuilder()
                .setPaymentMethodId(PERFECT_MONEY.getId())
                .setAccountName(accountName)
                .setAccountNumber(accountNumber)
                .setCurrencyCode(currencyCode)
                .build();
    }

    protected final PaymentAccount getDefaultPerfectDummyPaymentAccount(BisqAppConfig bisqAppConfig) {
        var req = GetPaymentAccountsRequest.newBuilder().build();
        var paymentAccountsService = grpcStubs(bisqAppConfig).paymentAccountsService;
        PaymentAccount paymentAccount = paymentAccountsService.getPaymentAccounts(req)
                .getPaymentAccountsList()
                .stream()
                .sorted(comparing(PaymentAccount::getCreationDate))
                .collect(Collectors.toList()).get(0);
        assertEquals("PerfectMoney dummy", paymentAccount.getAccountName());
        return paymentAccount;
    }

    protected final double getMarketPrice(BisqAppConfig bisqAppConfig, String currencyCode) {
        var req = createMarketPriceRequest(currencyCode);
        return grpcStubs(bisqAppConfig).priceService.getMarketPrice(req).getPrice();
    }

    // Static conveniences for test methods and test case fixture setups.

    protected static RegisterDisputeAgentRequest createRegisterDisputeAgentRequest(String disputeAgentType) {
        return RegisterDisputeAgentRequest.newBuilder()
                .setDisputeAgentType(disputeAgentType.toLowerCase())
                .setRegistrationKey(DEV_PRIVILEGE_PRIV_KEY).build();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected static void registerDisputeAgents(BisqAppConfig bisqAppConfig) {
        var disputeAgentsService = grpcStubs(bisqAppConfig).disputeAgentsService;
        disputeAgentsService.registerDisputeAgent(createRegisterDisputeAgentRequest(MEDIATOR));
        disputeAgentsService.registerDisputeAgent(createRegisterDisputeAgentRequest(REFUND_AGENT));
    }
}
