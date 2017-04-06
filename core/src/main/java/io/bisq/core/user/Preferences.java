package io.bisq.core.user;

import com.google.protobuf.Message;
import io.bisq.common.locale.Country;
import io.bisq.common.locale.CryptoCurrency;
import io.bisq.common.locale.FiatCurrency;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.persistance.Persistable;
import io.bisq.core.btc.BitcoinNetwork;
import io.bisq.core.payment.PaymentAccount;
import org.bitcoinj.core.Coin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */
public interface Preferences extends Persistable {

    List<FiatCurrency> getFiatCurrencies();

    List<CryptoCurrency> getCryptoCurrencies();

    void dontShowAgain(String key, boolean dontShowAgain);

    void resetDontShowAgain();

    void setBtcDenomination(String btcDenomination);

    void setUseAnimations(boolean useAnimations);

    void setBitcoinNetwork(BitcoinNetwork bitcoinNetwork);

    void addFiatCurrency(FiatCurrency tradeCurrency);

    void removeFiatCurrency(FiatCurrency tradeCurrency);

    void addCryptoCurrency(CryptoCurrency tradeCurrency);

    void removeCryptoCurrency(CryptoCurrency tradeCurrency);

    void setBlockChainExplorer(BlockChainExplorer blockChainExplorer);

    void setTacAccepted(boolean tacAccepted);

    void setUserLanguage(@NotNull String userLanguageCode);

    void setUserCountry(@NotNull Country userCountry);

    void setPreferredTradeCurrency(TradeCurrency preferredTradeCurrency);

    void setUseTorForBitcoinJ(boolean useTorForBitcoinJ);

    void setShowOwnOffersInOfferBook(boolean showOwnOffersInOfferBook);

    void setMaxPriceDistanceInPercent(double maxPriceDistanceInPercent);

    void setBackupDirectory(String backupDirectory);

    void setAutoSelectArbitrators(boolean autoSelectArbitrators);

    void setUsePercentageBasedPrice(boolean usePercentageBasedPrice);

    void setTagForPeer(String hostName, String tag);

    void setOfferBookChartScreenCurrencyCode(String offerBookChartScreenCurrencyCode);

    void setBuyScreenCurrencyCode(String buyScreenCurrencyCode);

    void setSellScreenCurrencyCode(String sellScreenCurrencyCode);

    void setIgnoreTradersList(List<String> ignoreTradersList);

    void setDirectoryChooserPath(String directoryChooserPath);

    void setTradeChartsScreenCurrencyCode(String tradeChartsScreenCurrencyCode);

    void setTradeStatisticsTickUnitIndex(int tradeStatisticsTickUnitIndex);

    void setSortMarketCurrenciesNumerically(boolean sortMarketCurrenciesNumerically);

    void setBitcoinNodes(String bitcoinNodes);

    void setUseCustomWithdrawalTxFee(boolean useCustomWithdrawalTxFee);

    void setWithdrawalTxFeeInBytes(long withdrawalTxFeeInBytes);

    void setBuyerSecurityDepositAsLong(long buyerSecurityDepositAsLong);

    void setSelectedPaymentAccountForCreateOffer(PaymentAccount paymentAccount);

    void setBlockChainExplorerTestNet(BlockChainExplorer blockChainExplorerTestNet);

    void setBlockChainExplorerMainNet(BlockChainExplorer blockChainExplorerMainNet);

    String getBtcDenomination();

    boolean getUseAnimations();

    BlockChainExplorer getBlockChainExplorer();

    ArrayList<BlockChainExplorer> getBlockChainExplorers();

    boolean showAgain(String key);

    boolean getUseTorForBitcoinJ();

    boolean getUseCustomWithdrawalTxFee();

    long getWithdrawalTxFeeInBytes();

    Coin getBuyerSecurityDepositAsCoin();

    @Override
    Message toProtobuf();

    void setDontShowAgainMap(java.util.Map<String, Boolean> dontShowAgainMap);

    void setPreferredLocale(java.util.Locale preferredLocale);

    void setUseStickyMarketPrice(boolean useStickyMarketPrice);

    void setPeerTagMap(java.util.Map<String, String> peerTagMap);

    BitcoinNetwork getBitcoinNetwork();

    String getUserLanguage();

    Country getUserCountry();

    BlockChainExplorer getBlockChainExplorerMainNet();

    BlockChainExplorer getBlockChainExplorerTestNet();

    String getBackupDirectory();

    boolean isAutoSelectArbitrators();

    java.util.Map<String, Boolean> getDontShowAgainMap();

    boolean isTacAccepted();

    boolean isShowOwnOffersInOfferBook();

    TradeCurrency getPreferredTradeCurrency();

    double getMaxPriceDistanceInPercent();

    String getOfferBookChartScreenCurrencyCode();

    String getTradeChartsScreenCurrencyCode();

    String getBuyScreenCurrencyCode();

    String getSellScreenCurrencyCode();

    int getTradeStatisticsTickUnitIndex();

    boolean isUseStickyMarketPrice();

    boolean isSortMarketCurrenciesNumerically();

    boolean isUsePercentageBasedPrice();

    java.util.Map<String, String> getPeerTagMap();

    String getBitcoinNodes();

    List<String> getIgnoreTradersList();

    String getDirectoryChooserPath();

    long getBuyerSecurityDepositAsLong();

    PaymentAccount getSelectedPaymentAccountForCreateOffer();

    javafx.beans.property.StringProperty getBtcDenominationProperty();

    javafx.beans.property.BooleanProperty getUseAnimationsProperty();

    javafx.beans.property.BooleanProperty getUseCustomWithdrawalTxFeeProperty();

    javafx.beans.property.LongProperty getWithdrawalTxFeeInBytesProperty();

    javafx.collections.ObservableList<FiatCurrency> getFiatCurrenciesAsObservable();

    javafx.collections.ObservableList<CryptoCurrency> getCryptoCurrenciesAsObservable();

    javafx.collections.ObservableList<TradeCurrency> getTradeCurrenciesAsObservable();
}
