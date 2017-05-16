package io.bisq.core.user;

import io.bisq.common.GlobalSettings;
import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.*;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.btc.BitcoinNetwork;
import io.bisq.core.btc.BtcOptionKeys;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.btc.wallet.WalletUtils;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.trade.Tradable;
import io.bisq.core.trade.TradableList;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public final class Preferences implements PersistedDataHost {

    // Deactivate mBit for now as most screens are not supporting it yet
    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    static final List<String> BTC_DENOMINATIONS = Arrays.asList(MonetaryFormat.CODE_BTC/*, MonetaryFormat.CODE_MBTC*/);

    private static final ArrayList<BlockChainExplorer> BLOCK_CHAIN_EXPLORERS_TEST_NET = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("Blocktrail", "https://www.blocktrail.com/tBTC/tx/", "https://www.blocktrail.com/tBTC/address/"),
            new BlockChainExplorer("Blockexplorer", "https://blockexplorer.com/testnet/tx/", "https://blockexplorer.com/testnet/address/"),
            new BlockChainExplorer("Blockr.io", "https://tbtc.blockr.io/tx/info/", "https://tbtc.blockr.io/address/info/"),
            new BlockChainExplorer("Biteasy", "https://www.biteasy.com/testnet/transactions/", "https://www.biteasy.com/testnet/addresses/"),
            new BlockChainExplorer("Smartbit", "https://testnet.smartbit.com.au/tx/", "https://testnet.smartbit.com.au/address/"),
            new BlockChainExplorer("SoChain. Wow.", "https://chain.so/tx/BTCTEST/", "https://chain.so/address/BTCTEST/")
    ));

    private static final ArrayList<BlockChainExplorer> BLOCK_CHAIN_EXPLORERS_MAIN_NET = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("Tradeblock.com", "https://tradeblock.com/bitcoin/tx/", "https://tradeblock.com/bitcoin/address/"),
            new BlockChainExplorer("Blocktrail", "https://www.blocktrail.com/BTC/tx/", "https://www.blocktrail.com/BTC/address/"),
            new BlockChainExplorer("Insight", "https://insight.bitpay.com/tx/", "https://insight.bitpay.com/address/"),
            new BlockChainExplorer("Blockchain.info", "https://blockchain.info/tx/", "https://blockchain.info/address/"),
            new BlockChainExplorer("Blockexplorer", "https://blockexplorer.com/tx/", "https://blockexplorer.com/address/"),
            new BlockChainExplorer("Blockr.io", "https://btc.blockr.io/tx/info/", "https://btc.blockr.io/address/info/"),
            new BlockChainExplorer("Biteasy", "https://www.biteasy.com/transactions/", "https://www.biteasy.com/addresses/"),
            new BlockChainExplorer("Blockonomics", "https://www.blockonomics.co/api/tx?txid=", "https://www.blockonomics.co/#/search?q="),
            new BlockChainExplorer("Chainflyer", "http://chainflyer.bitflyer.jp/Transaction/", "http://chainflyer.bitflyer.jp/Address/"),
            new BlockChainExplorer("Smartbit", "https://www.smartbit.com.au/tx/", "https://www.smartbit.com.au/address/"),
            new BlockChainExplorer("SoChain. Wow.", "https://chain.so/tx/BTC/", "https://chain.so/address/BTC/"),
            new BlockChainExplorer("Bitaps", "https://bitaps.com/", "https://bitaps.com/")
    ));


    // payload is initialized so the default values are available for Property initialization.
    @Setter
    @Delegate(excludes = ExcludesDelegateMethods.class)
    private PreferencesPayload prefPayload = new PreferencesPayload();
    private AtomicBoolean initialReadDone = new AtomicBoolean(false);

    // Observable wrappers
    @Getter
    private final StringProperty btcDenominationProperty = new SimpleStringProperty(prefPayload.getBtcDenomination());
    @Getter
    private final BooleanProperty useAnimationsProperty = new SimpleBooleanProperty(prefPayload.isUseAnimations());
    @Getter
    private final BooleanProperty useCustomWithdrawalTxFeeProperty = new SimpleBooleanProperty(prefPayload.isUseCustomWithdrawalTxFee());
    @Getter
    private final LongProperty withdrawalTxFeeInBytesProperty = new SimpleLongProperty(prefPayload.getWithdrawalTxFeeInBytes());

    private final ObservableList<FiatCurrency> fiatCurrenciesAsObservable = FXCollections.observableArrayList();
    private final ObservableList<CryptoCurrency> cryptoCurrenciesAsObservable = FXCollections.observableArrayList();
    private final ObservableList<TradeCurrency> tradeCurrenciesAsObservable = FXCollections.observableArrayList();

    private final Storage<PreferencesPayload> storage;
    private final String btcNodesFromOptions;
    private final String useTorFlagFromOptions;
    private boolean autoSelectArbitrators;
    private boolean resyncSpvRequested;
    private boolean tacAccepted;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////


    @SuppressWarnings("WeakerAccess")
    @Inject
    public Preferences(Storage<PreferencesPayload> storage,
                       @Named(BtcOptionKeys.BTC_NODES) String btcNodesFromOptions,
                       @Named(BtcOptionKeys.USE_TOR_FOR_BTC) String useTorFlagFromOptions) {

        this.storage = storage;
        this.btcNodesFromOptions = btcNodesFromOptions;
        this.useTorFlagFromOptions = useTorFlagFromOptions;
    }

    @Override
    public void readPersisted() {
        PreferencesPayload persisted = storage.initAndGetPersistedWithFileName("Preferences");

        if (persisted != null) {
            prefPayload = persisted;
            GlobalSettings.setLocale(new Locale(prefPayload.getUserLanguage(), prefPayload.getUserCountry().code));
            GlobalSettings.setUseAnimations(prefPayload.isUseAnimations());
            checkNotNull(prefPayload.getPreferredTradeCurrency(), "preferredTradeCurrency must not be null"); // move to payload?
            setBtcDenominationProperty(prefPayload.getBtcDenomination());
        } else {
            prefPayload = new PreferencesPayload();
            prefPayload.setUserLanguage(GlobalSettings.getLocale().getLanguage());
            prefPayload.setUserCountry(CountryUtil.getDefaultCountry());
            GlobalSettings.setLocale(new Locale(prefPayload.getUserLanguage(), prefPayload.getUserCountry().code));
            prefPayload.setPreferredTradeCurrency(CurrencyUtil.getCurrencyByCountryCode(prefPayload.getUserCountry().code));

            setFiatCurrencies(CurrencyUtil.getMainFiatCurrencies());
            setCryptoCurrencies(CurrencyUtil.getMainCryptoCurrencies());
            setBlockChainExplorerTestNet(BLOCK_CHAIN_EXPLORERS_TEST_NET.get(0));
            setBlockChainExplorerMainNet(BLOCK_CHAIN_EXPLORERS_MAIN_NET.get(0));
            prefPayload.setDirectoryChooserPath(Utilities.getSystemHomeDirectory());
        }


        // We don't want to pass Preferences to all popups where the dont show again checkbox is used, so we use
        // that static lookup class to avoid static access to the Preferences directly.
        DontShowAgainLookup.setPreferences(this);

        btcDenominationProperty.addListener((ov) -> {
            prefPayload.setBtcDenomination(btcDenominationProperty.get());
            GlobalSettings.setBtcDenomination(prefPayload.getBtcDenomination());
            persist();
        });
        useAnimationsProperty.addListener((ov) -> {
            prefPayload.setUseAnimations(useAnimationsProperty.get());
            GlobalSettings.setUseAnimations(prefPayload.isUseAnimations());
            persist();
        });
        fiatCurrenciesAsObservable.addListener((javafx.beans.Observable ov) -> {
            prefPayload.getFiatCurrencies().clear();
            prefPayload.getFiatCurrencies().addAll(fiatCurrenciesAsObservable);
            prefPayload.getFiatCurrencies().sort(TradeCurrency::compareTo);
            persist();
        });
        cryptoCurrenciesAsObservable.addListener((javafx.beans.Observable ov) -> {
            prefPayload.getCryptoCurrencies().clear();
            prefPayload.getCryptoCurrencies().addAll(cryptoCurrenciesAsObservable);
            prefPayload.getCryptoCurrencies().sort(TradeCurrency::compareTo);
            persist();
        });

        useCustomWithdrawalTxFeeProperty.addListener((ov) -> {
            prefPayload.setUseCustomWithdrawalTxFee(useCustomWithdrawalTxFeeProperty.get());
            persist();
        });

        withdrawalTxFeeInBytesProperty.addListener((ov) -> {
            prefPayload.setWithdrawalTxFeeInBytes(withdrawalTxFeeInBytesProperty.get());
            persist();
        });

        GlobalSettings.setDefaultTradeCurrency(prefPayload.getPreferredTradeCurrency());

        // TODO why do we do this ???? Should this be in the null case instead?
        prefPayload.setOfferBookChartScreenCurrencyCode(prefPayload.getPreferredTradeCurrency().getCode());
        prefPayload.setTradeChartsScreenCurrencyCode(prefPayload.getPreferredTradeCurrency().getCode());
        prefPayload.setBuyScreenCurrencyCode(prefPayload.getPreferredTradeCurrency().getCode());
        prefPayload.setSellScreenCurrencyCode(prefPayload.getPreferredTradeCurrency().getCode());
        prefPayload.setBtcDenomination(MonetaryFormat.CODE_BTC);

        // set all properties
        btcDenominationProperty.set(prefPayload.getBtcDenomination());
        useAnimationsProperty.set(prefPayload.isUseAnimations());
        useCustomWithdrawalTxFeeProperty.set(prefPayload.isUseCustomWithdrawalTxFee());
        withdrawalTxFeeInBytesProperty.set(prefPayload.getWithdrawalTxFeeInBytes());

        fiatCurrenciesAsObservable.addListener(this::updateTradeCurrencies);
        cryptoCurrenciesAsObservable.addListener(this::updateTradeCurrencies);
        tradeCurrenciesAsObservable.addAll(prefPayload.getFiatCurrencies());
        tradeCurrenciesAsObservable.addAll(prefPayload.getCryptoCurrencies());

        // Override settings with options if set
        if (useTorFlagFromOptions != null && !useTorFlagFromOptions.isEmpty()) {
            if (useTorFlagFromOptions.equals("false"))
                setUseTorForBitcoinJ(false);
            else if (useTorFlagFromOptions.equals("true"))
                setUseTorForBitcoinJ(true);
        }

        if (btcNodesFromOptions != null && !btcNodesFromOptions.isEmpty())
            setBitcoinNodes(btcNodesFromOptions);

        initialReadDone.set(true);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void dontShowAgain(String key, boolean dontShowAgain) {
        prefPayload.getDontShowAgainMap().put(key, dontShowAgain);
        persist();
    }

    public void resetDontShowAgain() {
        prefPayload.getDontShowAgainMap().clear();
        persist();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setBtcDenominationProperty(String btcDenomination) {
        this.btcDenominationProperty.set(btcDenomination);
    }

    public void setUseAnimations(boolean useAnimations) {
        this.useAnimationsProperty.set(useAnimations);
    }

    public void addFiatCurrency(FiatCurrency tradeCurrency) {
        if (!fiatCurrenciesAsObservable.contains(tradeCurrency))
            fiatCurrenciesAsObservable.add(tradeCurrency);
    }

    public void removeFiatCurrency(FiatCurrency tradeCurrency) {
        if (tradeCurrenciesAsObservable.size() > 1) {
            if (fiatCurrenciesAsObservable.contains(tradeCurrency))
                fiatCurrenciesAsObservable.remove(tradeCurrency);

            if (prefPayload.getPreferredTradeCurrency().equals(tradeCurrency))
                setPreferredTradeCurrency(tradeCurrenciesAsObservable.get(0));
        } else {
            log.error("you cannot remove the last currency");
        }
    }

    public void addCryptoCurrency(CryptoCurrency tradeCurrency) {
        if (!cryptoCurrenciesAsObservable.contains(tradeCurrency))
            cryptoCurrenciesAsObservable.add(tradeCurrency);
    }

    public void removeCryptoCurrency(CryptoCurrency tradeCurrency) {
        if (tradeCurrenciesAsObservable.size() > 1) {
            if (cryptoCurrenciesAsObservable.contains(tradeCurrency))
                cryptoCurrenciesAsObservable.remove(tradeCurrency);

            if (prefPayload.getPreferredTradeCurrency().equals(tradeCurrency))
                setPreferredTradeCurrency(tradeCurrenciesAsObservable.get(0));
        } else {
            log.error("you cannot remove the last currency");
        }
    }

    public void setBlockChainExplorer(BlockChainExplorer blockChainExplorer) {
        if (WalletUtils.getBitcoinNetwork() == BitcoinNetwork.MAINNET)
            setBlockChainExplorerMainNet(blockChainExplorer);
        else
            setBlockChainExplorerTestNet(blockChainExplorer);
    }

    public void setTacAccepted(boolean tacAccepted) {
        prefPayload.setTacAccepted(tacAccepted);
        persist();
    }

    private void persist() {
        if (initialReadDone.get())
            storage.queueUpForSave(prefPayload);
    }

    public void setUserLanguage(@NotNull String userLanguageCode) {
        prefPayload.setUserLanguage(userLanguageCode);
        if (prefPayload.getUserCountry() != null && prefPayload.getUserLanguage() != null)
            GlobalSettings.setLocale(new Locale(prefPayload.getUserLanguage(), prefPayload.getUserCountry().code));
        persist();
    }

    public void setUserCountry(@NotNull Country userCountry) {
        prefPayload.setUserCountry(userCountry);
        if (prefPayload.getUserLanguage() != null)
            GlobalSettings.setLocale(new Locale(prefPayload.getUserLanguage(), userCountry.code));
        persist();
    }

    public void setPreferredTradeCurrency(TradeCurrency preferredTradeCurrency) {
        if (preferredTradeCurrency != null) {
            prefPayload.setPreferredTradeCurrency(preferredTradeCurrency);
            GlobalSettings.setDefaultTradeCurrency(preferredTradeCurrency);
            persist();
        }
    }

    public void setUseTorForBitcoinJ(boolean useTorForBitcoinJ) {
        prefPayload.setUseTorForBitcoinJ(useTorForBitcoinJ);
        persist();
    }

    public void setShowOwnOffersInOfferBook(boolean showOwnOffersInOfferBook) {
        prefPayload.setShowOwnOffersInOfferBook(showOwnOffersInOfferBook);
        persist();
    }

    public void setMaxPriceDistanceInPercent(double maxPriceDistanceInPercent) {
        prefPayload.setMaxPriceDistanceInPercent(maxPriceDistanceInPercent);
        persist();
    }

    public void setBackupDirectory(String backupDirectory) {
        prefPayload.setBackupDirectory(backupDirectory);
        persist();
    }

    public void setAutoSelectArbitrators(boolean autoSelectArbitrators) {
        prefPayload.setAutoSelectArbitrators(autoSelectArbitrators);
        persist();
    }

    public void setUsePercentageBasedPrice(boolean usePercentageBasedPrice) {
        prefPayload.setUsePercentageBasedPrice(usePercentageBasedPrice);
        persist();
    }

    public void setTagForPeer(String hostName, String tag) {
        prefPayload.getPeerTagMap().put(hostName, tag);
        persist();
    }

    public void setOfferBookChartScreenCurrencyCode(String offerBookChartScreenCurrencyCode) {
        prefPayload.setOfferBookChartScreenCurrencyCode(offerBookChartScreenCurrencyCode);
        persist();
    }

    public void setBuyScreenCurrencyCode(String buyScreenCurrencyCode) {
        prefPayload.setBuyScreenCurrencyCode(buyScreenCurrencyCode);
        persist();
    }

    public void setSellScreenCurrencyCode(String sellScreenCurrencyCode) {
        prefPayload.setSellScreenCurrencyCode(sellScreenCurrencyCode);
        persist();
    }

    public void setIgnoreTradersList(List<String> ignoreTradersList) {
        prefPayload.setIgnoreTradersList(ignoreTradersList);
        persist();
    }

    public void setDirectoryChooserPath(String directoryChooserPath) {
        prefPayload.setDirectoryChooserPath(directoryChooserPath);
        persist();
    }

    public void setTradeChartsScreenCurrencyCode(String tradeChartsScreenCurrencyCode) {
        prefPayload.setTradeChartsScreenCurrencyCode(tradeChartsScreenCurrencyCode);
        persist();
    }

    public void setTradeStatisticsTickUnitIndex(int tradeStatisticsTickUnitIndex) {
        prefPayload.setTradeStatisticsTickUnitIndex(tradeStatisticsTickUnitIndex);
        persist();
    }

    public void setSortMarketCurrenciesNumerically(boolean sortMarketCurrenciesNumerically) {
        prefPayload.setSortMarketCurrenciesNumerically(sortMarketCurrenciesNumerically);
        persist();
    }

    public void setBitcoinNodes(String bitcoinNodes) {
        prefPayload.setBitcoinNodes(bitcoinNodes);
        persist();
    }

    public void setUseCustomWithdrawalTxFee(boolean useCustomWithdrawalTxFee) {
        useCustomWithdrawalTxFeeProperty.set(useCustomWithdrawalTxFee);
    }

    public void setWithdrawalTxFeeInBytes(long withdrawalTxFeeInBytes) {
        withdrawalTxFeeInBytesProperty.set(withdrawalTxFeeInBytes);
    }

    public void setBuyerSecurityDepositAsLong(long buyerSecurityDepositAsLong) {
        prefPayload.setBuyerSecurityDepositAsLong(Math.min(Restrictions.MAX_BUYER_SECURITY_DEPOSIT.value,
                Math.max(Restrictions.MIN_BUYER_SECURITY_DEPOSIT.value,
                        buyerSecurityDepositAsLong)));
        persist();
    }

    public void setSelectedPaymentAccountForCreateOffer(@Nullable PaymentAccount paymentAccount) {
        prefPayload.setSelectedPaymentAccountForCreateOffer(paymentAccount);
        persist();
    }

    public void setBsqBlockChainExplorer(BlockChainExplorer bsqBlockChainExplorer) {
        prefPayload.setBsqBlockChainExplorer(bsqBlockChainExplorer);
        persist();
    }

    public void setPayFeeInBtc(boolean payFeeInBtc) {
        prefPayload.setPayFeeInBtc(payFeeInBtc);
        persist();
    }

    private void setFiatCurrencies(List<FiatCurrency> currencies) {
        fiatCurrenciesAsObservable.setAll(currencies);
    }

    private void setCryptoCurrencies(List<CryptoCurrency> currencies) {
        cryptoCurrenciesAsObservable.setAll(currencies);
    }

    public void setBlockChainExplorerTestNet(BlockChainExplorer blockChainExplorerTestNet) {
        prefPayload.setBlockChainExplorerTestNet(blockChainExplorerTestNet);
        persist();
    }

    public void setBlockChainExplorerMainNet(BlockChainExplorer blockChainExplorerMainNet) {
        prefPayload.setBlockChainExplorerMainNet(blockChainExplorerMainNet);
        persist();
    }

    public void setResyncSpvRequested(boolean resyncSpvRequested) {
        prefPayload.setResyncSpvRequested(resyncSpvRequested);
        // We call that before shutdown so we dont want a delay here
        storage.queueUpForSave(prefPayload, 1);
    }

    // Only used from PB but keep it explicit as maybe it get used from the client and then we want to persist
    public void setDontShowAgainMap(Map<String, Boolean> dontShowAgainMap) {
        prefPayload.setDontShowAgainMap(dontShowAgainMap);
        persist();
    }

    // Only used from PB but keep it explicit as maybe it get used from the client and then we want to persist
    public void setPeerTagMap(Map<String, String> peerTagMap) {
        prefPayload.setPeerTagMap(peerTagMap);
        persist();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public StringProperty btcDenominationProperty() {
        return btcDenominationProperty;
    }

    public BooleanProperty useAnimationsProperty() {
        return useAnimationsProperty;
    }

    public ObservableList<FiatCurrency> getFiatCurrenciesAsObservable() {
        return fiatCurrenciesAsObservable;
    }

    public ObservableList<CryptoCurrency> getCryptoCurrenciesAsObservable() {
        return cryptoCurrenciesAsObservable;
    }

    public ObservableList<TradeCurrency> getTradeCurrenciesAsObservable() {
        return tradeCurrenciesAsObservable;
    }

    public BlockChainExplorer getBlockChainExplorer() {
        if (WalletUtils.getBitcoinNetwork() == BitcoinNetwork.MAINNET)
            return prefPayload.getBlockChainExplorerMainNet();
        else
            return prefPayload.getBlockChainExplorerTestNet();
    }

    public ArrayList<BlockChainExplorer> getBlockChainExplorers() {
        if (WalletUtils.getBitcoinNetwork() == BitcoinNetwork.MAINNET)
            return BLOCK_CHAIN_EXPLORERS_MAIN_NET;
        else
            return BLOCK_CHAIN_EXPLORERS_TEST_NET;
    }

    public boolean showAgain(String key) {
        return !prefPayload.getDontShowAgainMap().containsKey(key) || !prefPayload.getDontShowAgainMap().get(key);
    }

    public boolean getUseTorForBitcoinJ() {
        // We override the useTorForBitcoinJ and set to false if we have bitcoinNodes set
        // Atm we don't support onion addresses there
        // This check includes localhost, so we also override useTorForBitcoinJ
        if (prefPayload.getBitcoinNodes() != null && !prefPayload.getBitcoinNodes().isEmpty() || WalletUtils.getBitcoinNetwork() == BitcoinNetwork.REGTEST)
            return false;
        else
            return prefPayload.isUseTorForBitcoinJ();
    }


    public BooleanProperty useCustomWithdrawalTxFeeProperty() {
        return useCustomWithdrawalTxFeeProperty;
    }

    public LongProperty withdrawalTxFeeInBytesProperty() {
        return withdrawalTxFeeInBytesProperty;
    }

    public Coin getBuyerSecurityDepositAsCoin() {
        return Coin.valueOf(prefPayload.getBuyerSecurityDepositAsLong());
    }

    public boolean getPayFeeInBtc() {
        return prefPayload.isPayFeeInBtc();
    }

    public List<String> getBtcDenominations() {
        return BTC_DENOMINATIONS;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateTradeCurrencies(ListChangeListener.Change<? extends TradeCurrency> change) {
        change.next();
        if (change.wasAdded() && change.getAddedSize() == 1)
            tradeCurrenciesAsObservable.add(change.getAddedSubList().get(0));
        else if (change.wasRemoved() && change.getRemovedSize() == 1)
            tradeCurrenciesAsObservable.remove(change.getRemoved().get(0));
    }

    private interface ExcludesDelegateMethods {
        void setTacAccepted(boolean tacAccepted);
        void setUseAnimations(boolean useAnimations);
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
        void setSelectedPaymentAccountForCreateOffer(@Nullable PaymentAccount paymentAccount);
        void setBsqBlockChainExplorer(BlockChainExplorer bsqBlockChainExplorer);
        void setPayFeeInBtc(boolean payFeeInBtc);
        void setFiatCurrencies(List<FiatCurrency> currencies);
        void setCryptoCurrencies(List<CryptoCurrency> currencies);
        void setBlockChainExplorerTestNet(BlockChainExplorer blockChainExplorerTestNet);
        void setBlockChainExplorerMainNet(BlockChainExplorer blockChainExplorerMainNet);
        void setResyncSpvRequested(boolean resyncSpvRequested);
        void setDontShowAgainMap(Map<String, Boolean> dontShowAgainMap);
        void setPeerTagMap(Map<String, String> peerTagMap);
    }
}
