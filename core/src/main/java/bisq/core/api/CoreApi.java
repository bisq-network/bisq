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

package bisq.core.api;

import bisq.core.api.model.AddressBalanceInfo;
import bisq.core.api.model.BalancesInfo;
import bisq.core.api.model.TxFeeRateInfo;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.bisq_v1.TradeResultHandler;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;

import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import bisq.proto.grpc.GetTradesRequest;

import org.bitcoinj.core.Transaction;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.util.concurrent.FutureCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static bisq.proto.grpc.EditOfferRequest.EditType;

/**
 * Provides high level interface to functionality of core Bisq features.
 * E.g. useful for different APIs to access data of different domains of Bisq.
 */
@Singleton
@Slf4j
public class CoreApi {

    @Getter
    private final Config config;
    private final CoreDisputeAgentsService coreDisputeAgentsService;
    private final CoreHelpService coreHelpService;
    private final CoreOffersService coreOffersService;
    private final CorePaymentAccountsService paymentAccountsService;
    private final CorePriceService corePriceService;
    private final CoreTradesService coreTradesService;
    private final CoreWalletsService walletsService;
    private final TradeStatisticsManager tradeStatisticsManager;

    @Inject
    public CoreApi(Config config,
                   CoreDisputeAgentsService coreDisputeAgentsService,
                   CoreHelpService coreHelpService,
                   CoreOffersService coreOffersService,
                   CorePaymentAccountsService paymentAccountsService,
                   CorePriceService corePriceService,
                   CoreTradesService coreTradesService,
                   CoreWalletsService walletsService,
                   TradeStatisticsManager tradeStatisticsManager) {
        this.config = config;
        this.coreDisputeAgentsService = coreDisputeAgentsService;
        this.coreHelpService = coreHelpService;
        this.coreOffersService = coreOffersService;
        this.paymentAccountsService = paymentAccountsService;
        this.coreTradesService = coreTradesService;
        this.corePriceService = corePriceService;
        this.walletsService = walletsService;
        this.tradeStatisticsManager = tradeStatisticsManager;
    }

    @SuppressWarnings("SameReturnValue")
    public String getVersion() {
        return Version.VERSION;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute Agents
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void registerDisputeAgent(String disputeAgentType, String registrationKey) {
        coreDisputeAgentsService.registerDisputeAgent(disputeAgentType, registrationKey);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Help
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getMethodHelp(String methodName) {
        return coreHelpService.getMethodHelp(methodName);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Offers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isFiatOffer(String id, boolean isMyOffer) {
        return coreOffersService.isFiatOffer(id, isMyOffer);
    }

    public boolean isAltcoinOffer(String id, boolean isMyOffer) {
        return coreOffersService.isAltcoinOffer(id, isMyOffer);
    }

    public boolean isBsqSwapOffer(String id, boolean isMyOffer) {
        return coreOffersService.isBsqSwapOffer(id, isMyOffer);
    }

    public Offer getBsqSwapOffer(String id) {
        return coreOffersService.getBsqSwapOffer(id);
    }

    public Offer getOffer(String id) {
        return coreOffersService.getOffer(id);
    }

    public Optional<Offer> findAvailableOffer(String id) {
        return coreOffersService.findAvailableOffer(id);
    }

    public OpenOffer getMyOffer(String id) {
        return coreOffersService.getMyOffer(id);
    }

    public Optional<OpenOffer> findMyOpenOffer(String id) {
        return coreOffersService.findMyOpenOffer(id);
    }

    public Offer getMyBsqSwapOffer(String id) {
        return coreOffersService.getMyBsqSwapOffer(id);
    }

    public List<Offer> getBsqSwapOffers(String direction) {
        return coreOffersService.getBsqSwapOffers(direction);
    }

    public List<Offer> getOffers(String direction, String currencyCode) {
        return coreOffersService.getOffers(direction, currencyCode);
    }

    public List<OpenOffer> getMyOffers(String direction, String currencyCode) {
        return coreOffersService.getMyOffers(direction, currencyCode);
    }

    public List<Offer> getMyBsqSwapOffers(String direction) {
        return coreOffersService.getMyBsqSwapOffers(direction);
    }

    public OpenOffer getMyOpenBsqSwapOffer(String id) {
        return coreOffersService.getMyOpenBsqSwapOffer(id);
    }

    public void createAndPlaceBsqSwapOffer(String directionAsString,
                                           long amountAsLong,
                                           long minAmountAsLong,
                                           String priceAsString,
                                           Consumer<Offer> resultHandler) {
        coreOffersService.createAndPlaceBsqSwapOffer(directionAsString,
                amountAsLong,
                minAmountAsLong,
                priceAsString,
                resultHandler);
    }

    public void createAndPlaceOffer(String currencyCode,
                                    String directionAsString,
                                    String priceAsString,
                                    boolean useMarketBasedPrice,
                                    double marketPriceMargin,
                                    long amountAsLong,
                                    long minAmountAsLong,
                                    double buyerSecurityDeposit,
                                    long triggerPrice,
                                    String paymentAccountId,
                                    String makerFeeCurrencyCode,
                                    Consumer<Offer> resultHandler) {
        coreOffersService.createAndPlaceOffer(currencyCode,
                directionAsString,
                priceAsString,
                useMarketBasedPrice,
                marketPriceMargin,
                amountAsLong,
                minAmountAsLong,
                buyerSecurityDeposit,
                triggerPrice,
                paymentAccountId,
                makerFeeCurrencyCode,
                resultHandler);
    }

    public void editOffer(String offerId,
                          String priceAsString,
                          boolean useMarketBasedPrice,
                          double marketPriceMargin,
                          long triggerPrice,
                          int enable,
                          EditType editType) {
        coreOffersService.editOffer(offerId,
                priceAsString,
                useMarketBasedPrice,
                marketPriceMargin,
                triggerPrice,
                enable,
                editType);
    }

    public void cancelOffer(String id) {
        coreOffersService.cancelOffer(id);
    }

    public boolean isMyOffer(Offer offer) {
        return coreOffersService.isMyOffer(offer);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PaymentAccounts
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PaymentAccount createPaymentAccount(String jsonString) {
        return paymentAccountsService.createPaymentAccount(jsonString);
    }

    public Set<PaymentAccount> getPaymentAccounts() {
        return paymentAccountsService.getPaymentAccounts();
    }

    public List<PaymentMethod> getFiatPaymentMethods() {
        return paymentAccountsService.getFiatPaymentMethods();
    }

    public String getPaymentAccountForm(String paymentMethodId) {
        return paymentAccountsService.getPaymentAccountFormAsString(paymentMethodId);
    }

    public PaymentAccount createCryptoCurrencyPaymentAccount(String accountName,
                                                             String currencyCode,
                                                             String address,
                                                             boolean tradeInstant) {
        return paymentAccountsService.createCryptoCurrencyPaymentAccount(accountName,
                currencyCode,
                address,
                tradeInstant);
    }

    public List<PaymentMethod> getCryptoCurrencyPaymentMethods() {
        return paymentAccountsService.getCryptoCurrencyPaymentMethods();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Prices
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void getMarketPrice(String currencyCode, Consumer<Double> resultHandler) {
        corePriceService.getMarketPrice(currencyCode, resultHandler);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trades
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void takeBsqSwapOffer(String offerId,
                                 TradeResultHandler<BsqSwapTrade> tradeResultHandler,
                                 ErrorMessageHandler errorMessageHandler) {
        Offer bsqSwapOffer = coreOffersService.getBsqSwapOffer(offerId);
        coreTradesService.takeBsqSwapOffer(bsqSwapOffer,
                tradeResultHandler,
                errorMessageHandler);
    }

    public void takeOffer(String offerId,
                          String paymentAccountId,
                          String takerFeeCurrencyCode,
                          Consumer<Trade> resultHandler,
                          ErrorMessageHandler errorMessageHandler) {
        Offer offer = coreOffersService.getOffer(offerId);
        coreTradesService.takeOffer(offer,
                paymentAccountId,
                takerFeeCurrencyCode,
                resultHandler,
                errorMessageHandler);
    }

    public void confirmPaymentStarted(String tradeId) {
        coreTradesService.confirmPaymentStarted(tradeId);
    }

    public void confirmPaymentReceived(String tradeId) {
        coreTradesService.confirmPaymentReceived(tradeId);
    }

    public void closeTrade(String tradeId) {
        coreTradesService.closeTrade(tradeId);
    }

    public void withdrawFunds(String tradeId, String address, String memo) {
        coreTradesService.withdrawFunds(tradeId, address, memo);
    }

    public TradeModel getTradeModel(String tradeId) {
        return coreTradesService.getTradeModel(tradeId);
    }

    public List<TradeModel> getOpenTrades() {
        return coreTradesService.getOpenTrades();
    }

    public List<TradeModel> getTradeHistory(GetTradesRequest.Category category) {
        return coreTradesService.getTradeHistory(category);
    }

    public String getTradeRole(TradeModel tradeModel) {
        return coreTradesService.getTradeRole(tradeModel);
    }

    public void failTrade(String tradeId) {
        coreTradesService.failTrade(tradeId);
    }

    public void unFailTrade(String tradeId) {
        coreTradesService.unFailTrade(tradeId);
    }

    public List<OpenOffer> getCanceledOpenOffers() {
        return coreTradesService.getCanceledOpenOffers();
    }

    public String getClosedTradeStateAsString(Tradable tradable) {
        return coreTradesService.getClosedTradeStateAsString(tradable);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Wallets
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BalancesInfo getBalances(String currencyCode) {
        return walletsService.getBalances(currencyCode);
    }

    public long getAddressBalance(String addressString) {
        return walletsService.getAddressBalance(addressString);
    }

    public AddressBalanceInfo getAddressBalanceInfo(String addressString) {
        return walletsService.getAddressBalanceInfo(addressString);
    }

    public List<AddressBalanceInfo> getFundingAddresses() {
        return walletsService.getFundingAddresses();
    }

    public String getUnusedBsqAddress() {
        return walletsService.getUnusedBsqAddress();
    }

    public void sendBsq(String address,
                        String amount,
                        String txFeeRate,
                        TxBroadcaster.Callback callback) {
        walletsService.sendBsq(address, amount, txFeeRate, callback);
    }

    public void sendBtc(String address,
                        String amount,
                        String txFeeRate,
                        String memo,
                        FutureCallback<Transaction> callback) {
        walletsService.sendBtc(address, amount, txFeeRate, memo, callback);
    }

    public boolean verifyBsqSentToAddress(String address, String amount) {
        return walletsService.verifyBsqSentToAddress(address, amount);
    }

    public void getTxFeeRate(ResultHandler resultHandler) {
        walletsService.getTxFeeRate(resultHandler);
    }

    public void setTxFeeRatePreference(long txFeeRate,
                                       ResultHandler resultHandler) {
        walletsService.setTxFeeRatePreference(txFeeRate, resultHandler);
    }

    public void unsetTxFeeRatePreference(ResultHandler resultHandler) {
        walletsService.unsetTxFeeRatePreference(resultHandler);
    }

    public TxFeeRateInfo getMostRecentTxFeeRateInfo() {
        return walletsService.getMostRecentTxFeeRateInfo();
    }

    public Transaction getTransaction(String txId) {
        return walletsService.getTransaction(txId);
    }

    public int getTransactionConfirmations(String txId) {
        return walletsService.getTransactionConfirmations(txId);
    }

    public void setWalletPassword(String password, String newPassword) {
        walletsService.setWalletPassword(password, newPassword);
    }

    public void lockWallet() {
        walletsService.lockWallet();
    }

    public void unlockWallet(String password, long timeout) {
        walletsService.unlockWallet(password, timeout);
    }

    public void removeWalletPassword(String password) {
        walletsService.removeWalletPassword(password);
    }

    public List<TradeStatistics3> getTradeStatistics() {
        return new ArrayList<>(tradeStatisticsManager.getObservableTradeStatisticsSet());
    }

    public int getNumConfirmationsForMostRecentTransaction(String addressString) {
        return walletsService.getNumConfirmationsForMostRecentTransaction(addressString);
    }
}
