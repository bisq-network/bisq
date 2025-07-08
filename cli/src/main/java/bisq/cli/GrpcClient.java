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

package bisq.cli;

import bisq.proto.grpc.AddressBalanceInfo;
import bisq.proto.grpc.AverageBsqTradePrice;
import bisq.proto.grpc.BalancesInfo;
import bisq.proto.grpc.BsqBalanceInfo;
import bisq.proto.grpc.BtcBalanceInfo;
import bisq.proto.grpc.GetAverageBsqTradePriceRequest;
import bisq.proto.grpc.GetMethodHelpRequest;
import bisq.proto.grpc.GetTradesRequest;
import bisq.proto.grpc.GetVersionRequest;
import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.RegisterDisputeAgentRequest;
import bisq.proto.grpc.StopRequest;
import bisq.proto.grpc.TradeInfo;
import bisq.proto.grpc.TxFeeRateInfo;
import bisq.proto.grpc.TxInfo;

import protobuf.PaymentAccount;
import protobuf.PaymentMethod;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.proto.grpc.EditOfferRequest.EditType;
import static bisq.proto.grpc.GetOfferCategoryReply.OfferCategory;



import bisq.cli.request.OffersServiceRequest;
import bisq.cli.request.PaymentAccountsServiceRequest;
import bisq.cli.request.TradesServiceRequest;
import bisq.cli.request.WalletsServiceRequest;


@SuppressWarnings("ResultOfMethodCallIgnored")
@Slf4j
public final class GrpcClient {

    private final GrpcStubs grpcStubs;
    private final OffersServiceRequest offersServiceRequest;
    private final TradesServiceRequest tradesServiceRequest;
    private final WalletsServiceRequest walletsServiceRequest;
    private final PaymentAccountsServiceRequest paymentAccountsServiceRequest;

    public GrpcClient(String apiHost,
                      int apiPort,
                      String apiPassword) {
        this.grpcStubs = new GrpcStubs(apiHost, apiPort, apiPassword);
        this.offersServiceRequest = new OffersServiceRequest(grpcStubs);
        this.tradesServiceRequest = new TradesServiceRequest(grpcStubs);
        this.walletsServiceRequest = new WalletsServiceRequest(grpcStubs);
        this.paymentAccountsServiceRequest = new PaymentAccountsServiceRequest(grpcStubs);
    }

    public String getVersion() {
        var request = GetVersionRequest.newBuilder().build();
        return grpcStubs.versionService.getVersion(request).getVersion();
    }

    public String getNetwork() {
        return walletsServiceRequest.getNetwork();
    }

    public boolean getDaoStatus() {
        return walletsServiceRequest.getDaoStatus();
    }

    public BalancesInfo getBalances() {
        return walletsServiceRequest.getBalances();
    }

    public BsqBalanceInfo getBsqBalances() {
        return walletsServiceRequest.getBsqBalances();
    }

    public BtcBalanceInfo getBtcBalances() {
        return walletsServiceRequest.getBtcBalances();
    }

    public BalancesInfo getBalances(String currencyCode) {
        return walletsServiceRequest.getBalances(currencyCode);
    }

    public AddressBalanceInfo getAddressBalance(String address) {
        return walletsServiceRequest.getAddressBalance(address);
    }

    public AverageBsqTradePrice getAverageBsqTradePrice(int days) {
        var request = GetAverageBsqTradePriceRequest.newBuilder()
                .setDays(days)
                .build();
        return grpcStubs.priceService.getAverageBsqTradePrice(request).getPrice();
    }

    public double getBtcPrice(String currencyCode) {
        return walletsServiceRequest.getBtcPrice(currencyCode);
    }

    public List<AddressBalanceInfo> getFundingAddresses(boolean onlyFunded) {
        return walletsServiceRequest.getFundingAddresses(onlyFunded);
    }

    public String getUnusedBsqAddress() {
        return walletsServiceRequest.getUnusedBsqAddress();
    }

    public String getUnusedBtcAddress() {
        return walletsServiceRequest.getUnusedBtcAddress();
    }

    public TxInfo sendBsq(String address, String amount, String txFeeRate) {
        return walletsServiceRequest.sendBsq(address, amount, txFeeRate);
    }

    public TxInfo sendBtc(String address, String amount, String txFeeRate, String memo) {
        return walletsServiceRequest.sendBtc(address, amount, txFeeRate, memo);
    }

    public boolean verifyBsqSentToAddress(String address, String amount) {
        return walletsServiceRequest.verifyBsqSentToAddress(address, amount);
    }

    public TxFeeRateInfo getTxFeeRate() {
        return walletsServiceRequest.getTxFeeRate();
    }

    public TxFeeRateInfo setTxFeeRate(long txFeeRate) {
        return walletsServiceRequest.setTxFeeRate(txFeeRate);
    }

    public TxFeeRateInfo unsetTxFeeRate() {
        return walletsServiceRequest.unsetTxFeeRate();
    }

    public List<TxInfo> getTransactions() {
        return walletsServiceRequest.getTransactions();
    }

    public TxInfo getTransaction(String txId) {
        return walletsServiceRequest.getTransaction(txId);
    }

    public OfferCategory getAvailableOfferCategory(String offerId) {
        return offersServiceRequest.getAvailableOfferCategory(offerId);
    }

    public OfferCategory getMyOfferCategory(String offerId) {
        return offersServiceRequest.getMyOfferCategory(offerId);
    }

    public OfferInfo createBsqSwapOffer(String direction,
                                        long amount,
                                        long minAmount,
                                        String fixedPrice) {
        return offersServiceRequest.createBsqSwapOffer(direction,
                amount,
                minAmount,
                fixedPrice);
    }

    public OfferInfo createFixedPricedOffer(String direction,
                                            String currencyCode,
                                            long amount,
                                            long minAmount,
                                            String fixedPrice,
                                            double securityDepositPct,
                                            String paymentAcctId,
                                            String makerFeeCurrencyCode) {
        return offersServiceRequest.createOffer(direction,
                currencyCode,
                amount,
                minAmount,
                false,
                fixedPrice,
                0.00,
                securityDepositPct,
                paymentAcctId,
                makerFeeCurrencyCode,
                "0" /* no trigger price */);
    }

    public OfferInfo createMarketBasedPricedOffer(String direction,
                                                  String currencyCode,
                                                  long amount,
                                                  long minAmount,
                                                  double marketPriceMarginPct,
                                                  double securityDepositPct,
                                                  String paymentAcctId,
                                                  String makerFeeCurrencyCode,
                                                  String triggerPrice) {
        return offersServiceRequest.createOffer(direction,
                currencyCode,
                amount,
                minAmount,
                true,
                "0",
                marketPriceMarginPct,
                securityDepositPct,
                paymentAcctId,
                makerFeeCurrencyCode,
                triggerPrice);
    }

    public OfferInfo createOffer(String direction,
                                 String currencyCode,
                                 long amount,
                                 long minAmount,
                                 boolean useMarketBasedPrice,
                                 String fixedPrice,
                                 double marketPriceMarginPct,
                                 double securityDepositPct,
                                 String paymentAcctId,
                                 String makerFeeCurrencyCode,
                                 String triggerPrice) {
        return offersServiceRequest.createOffer(direction,
                currencyCode,
                amount,
                minAmount,
                useMarketBasedPrice,
                fixedPrice,
                marketPriceMarginPct,
                securityDepositPct,
                paymentAcctId,
                makerFeeCurrencyCode,
                triggerPrice);
    }

    public void editOfferActivationState(String offerId, int enable) {
        offersServiceRequest.editOfferActivationState(offerId, enable);
    }

    public void editOfferFixedPrice(String offerId, String priceAsString) {
        offersServiceRequest.editOfferFixedPrice(offerId, priceAsString);
    }

    public void editOfferPriceMargin(String offerId, double marketPriceMargin) {
        offersServiceRequest.editOfferPriceMargin(offerId, marketPriceMargin);
    }

    public void editOfferTriggerPrice(String offerId, String triggerPrice) {
        offersServiceRequest.editOfferTriggerPrice(offerId, triggerPrice);
    }

    public void editOffer(String offerId,
                          String price,
                          boolean useMarketBasedPrice,
                          double marketPriceMarginPct,
                          String triggerPrice,
                          int enable,
                          EditType editType) {
        // Take care when using this method directly:
        //  useMarketBasedPrice = true if margin based offer, false for fixed priced offer
        //  scaledPriceString fmt = ######.####
        offersServiceRequest.editOffer(offerId,
                price,
                useMarketBasedPrice,
                marketPriceMarginPct,
                triggerPrice,
                enable,
                editType);
    }

    public void cancelOffer(String offerId) {
        offersServiceRequest.cancelOffer(offerId);
    }

    public OfferInfo getBsqSwapOffer(String offerId) {
        return offersServiceRequest.getBsqSwapOffer(offerId);
    }

    public OfferInfo getOffer(String offerId) {
        return offersServiceRequest.getOffer(offerId);
    }

    @Deprecated // Since 5-Dec-2021.
    // Endpoint to be removed from future version.  Use getOffer service method instead.
    public OfferInfo getMyOffer(String offerId) {
        return offersServiceRequest.getMyOffer(offerId);
    }

    public List<OfferInfo> getBsqSwapOffers(String direction) {
        return offersServiceRequest.getBsqSwapOffers(direction);
    }

    public List<OfferInfo> getOffers(String direction, String currencyCode) {
        return offersServiceRequest.getOffers(direction, currencyCode);
    }

    public List<OfferInfo> getOffersSortedByDate(String currencyCode) {
        return offersServiceRequest.getOffersSortedByDate(currencyCode);
    }

    public List<OfferInfo> getOffersSortedByDate(String direction, String currencyCode) {
        return offersServiceRequest.getOffersSortedByDate(direction, currencyCode);
    }

    public List<OfferInfo> getBsqSwapOffersSortedByDate() {
        return offersServiceRequest.getBsqSwapOffersSortedByDate();
    }

    public List<OfferInfo> getMyOffers(String direction, String currencyCode) {
        return offersServiceRequest.getMyOffers(direction, currencyCode);
    }

    public List<OfferInfo> getMyOffersSortedByDate(String currencyCode) {
        return offersServiceRequest.getMyOffersSortedByDate(currencyCode);
    }

    public List<OfferInfo> getMyOffersSortedByDate(String direction, String currencyCode) {
        return offersServiceRequest.getMyOffersSortedByDate(direction, currencyCode);
    }

    public List<OfferInfo> getMyBsqSwapBsqOffersSortedByDate() {
        return offersServiceRequest.getMyBsqSwapOffersSortedByDate();
    }

    public TradeInfo takeBsqSwapOffer(String offerId, long amount) {
        return tradesServiceRequest.takeBsqSwapOffer(offerId, amount);
    }

    public TradeInfo takeOffer(String offerId,
                               String paymentAccountId,
                               String takerFeeCurrencyCode,
                               long amount) {
        return tradesServiceRequest.takeOffer(offerId,
                paymentAccountId,
                takerFeeCurrencyCode,
                amount);
    }

    public TradeInfo getTrade(String tradeId) {
        return tradesServiceRequest.getTrade(tradeId);
    }

    public List<TradeInfo> getOpenTrades() {
        return tradesServiceRequest.getOpenTrades();
    }

    public List<TradeInfo> getTradeHistory(GetTradesRequest.Category category) {
        return tradesServiceRequest.getTradeHistory(category);
    }

    public void confirmPaymentStarted(String tradeId) {
        tradesServiceRequest.confirmPaymentStarted(tradeId);
    }
    public void confirmPaymentStartedXmr(String tradeId, String txId, String txKey) {
        tradesServiceRequest.confirmPaymentStartedXmr(tradeId, txId, txKey);
    }

    public void confirmPaymentReceived(String tradeId) {
        tradesServiceRequest.confirmPaymentReceived(tradeId);
    }

    public void closeTrade(String tradeId) {
        tradesServiceRequest.closeTrade(tradeId);
    }

    public void withdrawFunds(String tradeId, String address, String memo) {
        tradesServiceRequest.withdrawFunds(tradeId, address, memo);
    }

    public void failTrade(String tradeId) {
        tradesServiceRequest.failTrade(tradeId);
    }

    public void unFailTrade(String tradeId) {
        tradesServiceRequest.unFailTrade(tradeId);
    }

    public List<PaymentMethod> getPaymentMethods() {
        return paymentAccountsServiceRequest.getPaymentMethods();
    }

    public String getPaymentAcctFormAsJson(String paymentMethodId) {
        return paymentAccountsServiceRequest.getPaymentAcctFormAsJson(paymentMethodId);
    }

    public PaymentAccount createPaymentAccount(String json) {
        return paymentAccountsServiceRequest.createPaymentAccount(json);
    }

    public List<PaymentAccount> getPaymentAccounts() {
        return paymentAccountsServiceRequest.getPaymentAccounts();
    }

    public PaymentAccount getPaymentAccount(String accountName) {
        return paymentAccountsServiceRequest.getPaymentAccount(accountName);
    }

    public PaymentAccount createCryptoCurrencyPaymentAccount(String accountName,
                                                             String currencyCode,
                                                             String address,
                                                             boolean tradeInstant) {
        return paymentAccountsServiceRequest.createCryptoCurrencyPaymentAccount(accountName,
                currencyCode,
                address,
                tradeInstant);
    }

    @SuppressWarnings("unused")
    public List<PaymentMethod> getCryptoPaymentMethods() {
        return paymentAccountsServiceRequest.getCryptoPaymentMethods();
    }

    public void lockWallet() {
        walletsServiceRequest.lockWallet();
    }

    public void unlockWallet(String walletPassword, long timeout) {
        walletsServiceRequest.unlockWallet(walletPassword, timeout);
    }

    public void removeWalletPassword(String walletPassword) {
        walletsServiceRequest.removeWalletPassword(walletPassword);
    }

    public void setWalletPassword(String walletPassword) {
        walletsServiceRequest.setWalletPassword(walletPassword);
    }

    public void setWalletPassword(String oldWalletPassword, String newWalletPassword) {
        walletsServiceRequest.setWalletPassword(oldWalletPassword, newWalletPassword);
    }

    public void registerDisputeAgent(String disputeAgentType, String registrationKey) {
        var request = RegisterDisputeAgentRequest.newBuilder()
                .setDisputeAgentType(disputeAgentType).setRegistrationKey(registrationKey).build();
        grpcStubs.disputeAgentsService.registerDisputeAgent(request);
    }

    public void stopServer() {
        var request = StopRequest.newBuilder().build();
        grpcStubs.shutdownService.stop(request);
    }

    public String getMethodHelp(Method method) {
        var request = GetMethodHelpRequest.newBuilder().setMethodName(method.name()).build();
        return grpcStubs.helpService.getMethodHelp(request).getMethodHelp();
    }
}
