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

import bisq.core.api.model.PaymentAccountForm;
import bisq.core.api.model.TxFeeRateInfo;
import bisq.core.proto.CoreProtoResolver;

import bisq.common.util.Utilities;

import bisq.proto.grpc.AddressBalanceInfo;
import bisq.proto.grpc.BalancesInfo;
import bisq.proto.grpc.BsqBalanceInfo;
import bisq.proto.grpc.BtcBalanceInfo;
import bisq.proto.grpc.CancelOfferRequest;
import bisq.proto.grpc.ConfirmPaymentReceivedRequest;
import bisq.proto.grpc.ConfirmPaymentStartedRequest;
import bisq.proto.grpc.CreatePaymentAccountRequest;
import bisq.proto.grpc.GetAddressBalanceRequest;
import bisq.proto.grpc.GetBalancesRequest;
import bisq.proto.grpc.GetFundingAddressesRequest;
import bisq.proto.grpc.GetOfferRequest;
import bisq.proto.grpc.GetPaymentAccountFormRequest;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.GetPaymentMethodsRequest;
import bisq.proto.grpc.GetTradeRequest;
import bisq.proto.grpc.GetTxFeeRateRequest;
import bisq.proto.grpc.GetUnusedBsqAddressRequest;
import bisq.proto.grpc.KeepFundsRequest;
import bisq.proto.grpc.LockWalletRequest;
import bisq.proto.grpc.MarketPriceRequest;
import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.RegisterDisputeAgentRequest;
import bisq.proto.grpc.RemoveWalletPasswordRequest;
import bisq.proto.grpc.SendBsqRequest;
import bisq.proto.grpc.SendBtcRequest;
import bisq.proto.grpc.SetTxFeeRatePreferenceRequest;
import bisq.proto.grpc.SetWalletPasswordRequest;
import bisq.proto.grpc.TakeOfferRequest;
import bisq.proto.grpc.TradeInfo;
import bisq.proto.grpc.TxInfo;
import bisq.proto.grpc.UnlockWalletRequest;
import bisq.proto.grpc.UnsetTxFeeRatePreferenceRequest;
import bisq.proto.grpc.WithdrawFundsRequest;

import protobuf.PaymentAccount;
import protobuf.PaymentMethod;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.List;
import java.util.stream.Collectors;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.arbdaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static bisq.common.app.DevEnv.DEV_PRIVILEGE_PRIV_KEY;
import static java.nio.charset.StandardCharsets.UTF_8;
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

    private static final CoreProtoResolver CORE_PROTO_RESOLVER = new CoreProtoResolver();

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
    protected final GetBalancesRequest createGetBalancesRequest(String currencyCode) {
        return GetBalancesRequest.newBuilder().setCurrencyCode(currencyCode).build();
    }

    protected final GetAddressBalanceRequest createGetAddressBalanceRequest(String address) {
        return GetAddressBalanceRequest.newBuilder().setAddress(address).build();
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

    protected final GetUnusedBsqAddressRequest createGetUnusedBsqAddressRequest() {
        return GetUnusedBsqAddressRequest.newBuilder().build();
    }

    protected final SendBsqRequest createSendBsqRequest(String address,
                                                        String amount,
                                                        String txFeeRate) {
        return SendBsqRequest.newBuilder()
                .setAddress(address)
                .setAmount(amount)
                .setTxFeeRate(txFeeRate)
                .build();
    }

    protected final SendBtcRequest createSendBtcRequest(String address,
                                                        String amount,
                                                        String txFeeRate,
                                                        String memo) {
        return SendBtcRequest.newBuilder()
                .setAddress(address)
                .setAmount(amount)
                .setTxFeeRate(txFeeRate)
                .setMemo(memo)
                .build();
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

    protected final TakeOfferRequest createTakeOfferRequest(String offerId,
                                                            String paymentAccountId,
                                                            String takerFeeCurrencyCode) {
        return TakeOfferRequest.newBuilder()
                .setOfferId(offerId)
                .setPaymentAccountId(paymentAccountId)
                .setTakerFeeCurrencyCode(takerFeeCurrencyCode)
                .build();
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

    protected final WithdrawFundsRequest createWithdrawFundsRequest(String tradeId,
                                                                    String address,
                                                                    String memo) {
        return WithdrawFundsRequest.newBuilder()
                .setTradeId(tradeId)
                .setAddress(address)
                .setMemo(memo)
                .build();
    }

    // Convenience methods for calling frequently used & thoroughly tested gRPC services.
    protected final BalancesInfo getBalances(BisqAppConfig bisqAppConfig, String currencyCode) {
        return grpcStubs(bisqAppConfig).walletsService.getBalances(
                createGetBalancesRequest(currencyCode)).getBalances();
    }

    protected final BsqBalanceInfo getBsqBalances(BisqAppConfig bisqAppConfig) {
        return getBalances(bisqAppConfig, "bsq").getBsq();
    }

    protected final BtcBalanceInfo getBtcBalances(BisqAppConfig bisqAppConfig) {
        return getBalances(bisqAppConfig, "btc").getBtc();
    }

    protected final AddressBalanceInfo getAddressBalance(BisqAppConfig bisqAppConfig, String address) {
        return grpcStubs(bisqAppConfig).walletsService.getAddressBalance(createGetAddressBalanceRequest(address)).getAddressBalanceInfo();
    }

    protected final void unlockWallet(BisqAppConfig bisqAppConfig, String password, long timeout) {
        //noinspection ResultOfMethodCallIgnored
        grpcStubs(bisqAppConfig).walletsService.unlockWallet(createUnlockWalletRequest(password, timeout));
    }

    protected final void lockWallet(BisqAppConfig bisqAppConfig) {
        //noinspection ResultOfMethodCallIgnored
        grpcStubs(bisqAppConfig).walletsService.lockWallet(createLockWalletRequest());
    }

    protected final String getUnusedBsqAddress(BisqAppConfig bisqAppConfig) {
        return grpcStubs(bisqAppConfig).walletsService.getUnusedBsqAddress(createGetUnusedBsqAddressRequest()).getAddress();
    }

    protected final TxInfo sendBsq(BisqAppConfig bisqAppConfig,
                                   String address,
                                   String amount) {
        return sendBsq(bisqAppConfig, address, amount, "");
    }

    protected final TxInfo sendBsq(BisqAppConfig bisqAppConfig,
                                   String address,
                                   String amount,
                                   String txFeeRate) {
        //noinspection ResultOfMethodCallIgnored
        return grpcStubs(bisqAppConfig).walletsService.sendBsq(createSendBsqRequest(address,
                amount,
                txFeeRate))
                .getTxInfo();
    }

    protected final TxInfo sendBtc(BisqAppConfig bisqAppConfig, String address, String amount) {
        return sendBtc(bisqAppConfig, address, amount, "", "");
    }

    protected final TxInfo sendBtc(BisqAppConfig bisqAppConfig,
                                   String address,
                                   String amount,
                                   String txFeeRate,
                                   String memo) {
        //noinspection ResultOfMethodCallIgnored
        return grpcStubs(bisqAppConfig).walletsService.sendBtc(
                createSendBtcRequest(address, amount, txFeeRate, memo))
                .getTxInfo();
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

    protected final List<PaymentMethod> getPaymentMethods(BisqAppConfig bisqAppConfig) {
        var req = GetPaymentMethodsRequest.newBuilder().build();
        return grpcStubs(bisqAppConfig).paymentAccountsService.getPaymentMethods(req).getPaymentMethodsList();
    }

    protected final File getPaymentAccountForm(BisqAppConfig bisqAppConfig, String paymentMethodId) {
        // We take seemingly unnecessary steps to get a File object, but the point is to
        // test the API, and we do not directly ask bisq.core.api.model.PaymentAccountForm
        // for an empty json form (file).
        var req = GetPaymentAccountFormRequest.newBuilder()
                .setPaymentMethodId(paymentMethodId)
                .build();
        String jsonString = grpcStubs(bisqAppConfig).paymentAccountsService.getPaymentAccountForm(req)
                .getPaymentAccountFormJson();
        // Write the json string to a file here in the test case.
        File jsonFile = PaymentAccountForm.getTmpJsonFile(paymentMethodId);
        try (PrintWriter out = new PrintWriter(jsonFile, UTF_8)) {
            out.println(jsonString);
        } catch (IOException ex) {
            fail("Could not create tmp payment account form.", ex);
        }
        return jsonFile;
    }

    protected final bisq.core.payment.PaymentAccount createPaymentAccount(BisqAppConfig bisqAppConfig,
                                                                          String jsonString) {
        var req = CreatePaymentAccountRequest.newBuilder()
                .setPaymentAccountForm(jsonString)
                .build();
        var paymentAccountsService = grpcStubs(bisqAppConfig).paymentAccountsService;
        // Normally, we can do asserts on the protos from the gRPC service, but in this
        // case we need to return a bisq.core.payment.PaymentAccount so it can be cast
        // to its sub type.
        return fromProto(paymentAccountsService.createPaymentAccount(req).getPaymentAccount());
    }

    protected static List<PaymentAccount> getPaymentAccounts(BisqAppConfig bisqAppConfig) {
        var req = GetPaymentAccountsRequest.newBuilder().build();
        return grpcStubs(bisqAppConfig).paymentAccountsService.getPaymentAccounts(req)
                .getPaymentAccountsList()
                .stream()
                .sorted(comparing(PaymentAccount::getCreationDate))
                .collect(Collectors.toList());
    }

    protected static PaymentAccount getDefaultPerfectDummyPaymentAccount(BisqAppConfig bisqAppConfig) {
        PaymentAccount paymentAccount = getPaymentAccounts(bisqAppConfig).get(0);
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
    protected final void withdrawFunds(BisqAppConfig bisqAppConfig,
                                       String tradeId,
                                       String address,
                                       String memo) {
        var req = createWithdrawFundsRequest(tradeId, address, memo);
        grpcStubs(bisqAppConfig).tradesService.withdrawFunds(req);
    }

    protected final TxFeeRateInfo getTxFeeRate(BisqAppConfig bisqAppConfig) {
        var req = GetTxFeeRateRequest.newBuilder().build();
        return TxFeeRateInfo.fromProto(
                grpcStubs(bisqAppConfig).walletsService.getTxFeeRate(req).getTxFeeRateInfo());
    }

    protected final TxFeeRateInfo setTxFeeRate(BisqAppConfig bisqAppConfig, long feeRate) {
        var req = SetTxFeeRatePreferenceRequest.newBuilder()
                .setTxFeeRatePreference(feeRate)
                .build();
        return TxFeeRateInfo.fromProto(
                grpcStubs(bisqAppConfig).walletsService.setTxFeeRatePreference(req).getTxFeeRateInfo());
    }

    protected final TxFeeRateInfo unsetTxFeeRate(BisqAppConfig bisqAppConfig) {
        var req = UnsetTxFeeRatePreferenceRequest.newBuilder().build();
        return TxFeeRateInfo.fromProto(
                grpcStubs(bisqAppConfig).walletsService.unsetTxFeeRatePreference(req).getTxFeeRateInfo());
    }

    // Static conveniences for test methods and test case fixture setups.

    protected static RegisterDisputeAgentRequest createRegisterDisputeAgentRequest(String disputeAgentType) {
        return RegisterDisputeAgentRequest.newBuilder()
                .setDisputeAgentType(disputeAgentType.toLowerCase())
                .setRegistrationKey(DEV_PRIVILEGE_PRIV_KEY).build();
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "SameParameterValue"})
    protected static void registerDisputeAgents(BisqAppConfig bisqAppConfig) {
        var disputeAgentsService = grpcStubs(bisqAppConfig).disputeAgentsService;
        disputeAgentsService.registerDisputeAgent(createRegisterDisputeAgentRequest(MEDIATOR));
        disputeAgentsService.registerDisputeAgent(createRegisterDisputeAgentRequest(REFUND_AGENT));
    }

    protected static String encodeToHex(String s) {
        return Utilities.bytesAsHexString(s.getBytes(UTF_8));
    }

    private bisq.core.payment.PaymentAccount fromProto(PaymentAccount proto) {
        return bisq.core.payment.PaymentAccount.fromProto(proto, CORE_PROTO_RESOLVER);
    }
}
