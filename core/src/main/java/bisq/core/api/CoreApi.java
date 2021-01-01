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
import bisq.core.monetary.Price;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.Trade;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;

import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.util.concurrent.FutureCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
    private final CoreOffersService coreOffersService;
    private final CorePaymentAccountsService paymentAccountsService;
    private final CorePriceService corePriceService;
    private final CoreTradesService coreTradesService;
    private final CoreWalletsService walletsService;
    private final TradeStatisticsManager tradeStatisticsManager;

    @Inject
    public CoreApi(Config config,
                   CoreDisputeAgentsService coreDisputeAgentsService,
                   CoreOffersService coreOffersService,
                   CorePaymentAccountsService paymentAccountsService,
                   CorePriceService corePriceService,
                   CoreTradesService coreTradesService,
                   CoreWalletsService walletsService,
                   TradeStatisticsManager tradeStatisticsManager) {
        this.config = config;
        this.coreDisputeAgentsService = coreDisputeAgentsService;
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
    // Offers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer getOffer(String id) {
        return coreOffersService.getOffer(id);
    }

    public List<Offer> getOffers(String direction, String currencyCode) {
        return coreOffersService.getOffers(direction, currencyCode);
    }

    public void createAnPlaceOffer(String currencyCode,
                                   String directionAsString,
                                   String priceAsString,
                                   boolean useMarketBasedPrice,
                                   double marketPriceMargin,
                                   long amountAsLong,
                                   long minAmountAsLong,
                                   double buyerSecurityDeposit,
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
                paymentAccountId,
                makerFeeCurrencyCode,
                resultHandler);
    }

    public Offer editOffer(String offerId,
                           String currencyCode,
                           OfferPayload.Direction direction,
                           Price price,
                           boolean useMarketBasedPrice,
                           double marketPriceMargin,
                           Coin amount,
                           Coin minAmount,
                           double buyerSecurityDeposit,
                           PaymentAccount paymentAccount) {
        return coreOffersService.editOffer(offerId,
                currencyCode,
                direction,
                price,
                useMarketBasedPrice,
                marketPriceMargin,
                amount,
                minAmount,
                buyerSecurityDeposit,
                paymentAccount);
    }

    public void cancelOffer(String id) {
        coreOffersService.cancelOffer(id);
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Prices
    ///////////////////////////////////////////////////////////////////////////////////////////

    public double getMarketPrice(String currencyCode) {
        return corePriceService.getMarketPrice(currencyCode);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trades
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void takeOffer(String offerId,
                          String paymentAccountId,
                          String takerFeeCurrencyCode,
                          Consumer<Trade> resultHandler) {
        Offer offer = coreOffersService.getOffer(offerId);
        coreTradesService.takeOffer(offer,
                paymentAccountId,
                takerFeeCurrencyCode,
                resultHandler);
    }

    public void confirmPaymentStarted(String tradeId) {
        coreTradesService.confirmPaymentStarted(tradeId);
    }

    public void confirmPaymentReceived(String tradeId) {
        coreTradesService.confirmPaymentReceived(tradeId);
    }

    public void keepFunds(String tradeId) {
        coreTradesService.keepFunds(tradeId);
    }

    public void withdrawFunds(String tradeId, String address, String memo) {
        coreTradesService.withdrawFunds(tradeId, address, memo);
    }

    public Trade getTrade(String tradeId) {
        return coreTradesService.getTrade(tradeId);
    }

    public String getTradeRole(String tradeId) {
        return coreTradesService.getTradeRole(tradeId);
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
