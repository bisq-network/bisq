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

import bisq.proto.grpc.CancelOfferRequest;
import bisq.proto.grpc.ConfirmPaymentReceivedRequest;
import bisq.proto.grpc.ConfirmPaymentStartedRequest;
import bisq.proto.grpc.CreatePaymentAccountRequest;
import bisq.proto.grpc.GetBalanceRequest;
import bisq.proto.grpc.GetFundingAddressesRequest;
import bisq.proto.grpc.GetOfferRequest;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.GetTradeRequest;
import bisq.proto.grpc.KeepFundsRequest;
import bisq.proto.grpc.LockWalletRequest;
import bisq.proto.grpc.MarketPriceRequest;
import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.RegisterDisputeAgentRequest;
import bisq.proto.grpc.RemoveWalletPasswordRequest;
import bisq.proto.grpc.SetWalletPasswordRequest;
import bisq.proto.grpc.TakeOfferRequest;
import bisq.proto.grpc.TradeInfo;
import bisq.proto.grpc.UnlockWalletRequest;
import bisq.proto.grpc.WithdrawFundsRequest;

import protobuf.PaymentAccount;

import java.util.stream.Collectors;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.arbdaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static bisq.common.app.DevEnv.DEV_PRIVILEGE_PRIV_KEY;
import static bisq.core.payment.payload.PaymentMethod.PERFECT_MONEY;
import static java.util.Arrays.stream;
import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;



import bisq.apitest.ApiTestCase;
import bisq.apitest.config.BisqAppConfig;
import bisq.cli.GrpcStubs;

public class MethodTest extends ApiTestCase {

    protected static final String ARBITRATOR = "arbitrator";
    protected static final String MEDIATOR = "mediator";
    protected static final String REFUND_AGENT = "refundagent";

    protected static GrpcStubs aliceStubs;
    protected static GrpcStubs bobStubs;

    protected static PaymentAccount alicesDummyAcct;
    protected static PaymentAccount bobsDummyAcct;

    public static void startSupportingApps(boolean registerDisputeAgents,
                                           boolean generateBtcBlock,
                                           Enum<?>... supportingApps) {
        try {
            // To run Bisq apps in debug mode, use the other setUpScaffold method:
            // setUpScaffold(new String[]{"--supportingApps", "bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon",
            //                            "--enableBisqDebugging", "true"});
            setUpScaffold(supportingApps);
            if (registerDisputeAgents) {
                registerDisputeAgents(arbdaemon);
            }

            if (stream(supportingApps).map(Enum::name).anyMatch(name -> name.equals(alicedaemon.name()))) {
                aliceStubs = grpcStubs(alicedaemon);
                alicesDummyAcct = getDefaultPerfectDummyPaymentAccount(alicedaemon);
            }

            if (stream(supportingApps).map(Enum::name).anyMatch(name -> name.equals(bobdaemon.name()))) {
                bobStubs = grpcStubs(bobdaemon);
                bobsDummyAcct = getDefaultPerfectDummyPaymentAccount(bobdaemon);
            }

            // Generate 1 regtest block for alice's and/or bob's wallet to
            // show 10 BTC balance, and allow time for daemons parse the new block.
            if (generateBtcBlock)
                genBtcBlocksThenWait(1, 1500);

        } catch (Exception ex) {
            fail(ex);
        }
    }

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

    protected final GetOfferRequest createGetOfferRequest(String offerId) {
        return GetOfferRequest.newBuilder().setId(offerId).build();
    }

    protected final CancelOfferRequest createCancelOfferRequest(String offerId) {
        return CancelOfferRequest.newBuilder().setId(offerId).build();
    }

    protected final TakeOfferRequest createTakeOfferRequest(String offerId, String paymentAccountId) {
        return TakeOfferRequest.newBuilder().setOfferId(offerId).setPaymentAccountId(paymentAccountId).build();
    }

    protected final GetTradeRequest createGetTradeRequest(String tradeId) {
        return GetTradeRequest.newBuilder().setTradeId(tradeId).build();
    }

    protected final ConfirmPaymentStartedRequest createConfirmPaymentStartedRequest(String tradeId) {
        return ConfirmPaymentStartedRequest.newBuilder().setTradeId(tradeId).build();
    }

    protected final ConfirmPaymentReceivedRequest createConfirmPaymentReceivedRequest(String tradeId) {
        return ConfirmPaymentReceivedRequest.newBuilder().setTradeId(tradeId).build();
    }

    protected final KeepFundsRequest createKeepFundsRequest(String tradeId) {
        return KeepFundsRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
    }

    protected final WithdrawFundsRequest createWithdrawFundsRequest(String tradeId, String address) {
        return WithdrawFundsRequest.newBuilder()
                .setTradeId(tradeId)
                .setAddress(address)
                .build();
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

    protected static PaymentAccount getDefaultPerfectDummyPaymentAccount(BisqAppConfig bisqAppConfig) {
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

    protected final OfferInfo getOffer(BisqAppConfig bisqAppConfig, String offerId) {
        var req = createGetOfferRequest(offerId);
        return grpcStubs(bisqAppConfig).offersService.getOffer(req).getOffer();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected final void cancelOffer(BisqAppConfig bisqAppConfig, String offerId) {
        var req = createCancelOfferRequest(offerId);
        grpcStubs(bisqAppConfig).offersService.cancelOffer(req);
    }

    protected final TradeInfo getTrade(BisqAppConfig bisqAppConfig, String tradeId) {
        var req = createGetTradeRequest(tradeId);
        return grpcStubs(bisqAppConfig).tradesService.getTrade(req).getTrade();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected final void confirmPaymentStarted(BisqAppConfig bisqAppConfig, String tradeId) {
        var req = createConfirmPaymentStartedRequest(tradeId);
        grpcStubs(bisqAppConfig).tradesService.confirmPaymentStarted(req);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected final void confirmPaymentReceived(BisqAppConfig bisqAppConfig, String tradeId) {
        var req = createConfirmPaymentReceivedRequest(tradeId);
        grpcStubs(bisqAppConfig).tradesService.confirmPaymentReceived(req);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected final void keepFunds(BisqAppConfig bisqAppConfig, String tradeId) {
        var req = createKeepFundsRequest(tradeId);
        grpcStubs(bisqAppConfig).tradesService.keepFunds(req);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected final void withdrawFunds(BisqAppConfig bisqAppConfig, String tradeId, String address) {
        var req = createWithdrawFundsRequest(tradeId, address);
        grpcStubs(bisqAppConfig).tradesService.withdrawFunds(req);
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
