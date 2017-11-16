package io.bisq.core.user;

import io.bisq.common.GlobalSettings;
import io.bisq.common.locale.*;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.BaseCurrencyNetwork;
import io.bisq.core.btc.BtcOptionKeys;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.network.p2p.network.BridgeAddressProvider;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public final class Preferences implements PersistedDataHost, BridgeAddressProvider {

    private static final ArrayList<BlockChainExplorer> BTC_MAIN_NET_EXPLORERS = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("Bitaps", "https://bitaps.com/", "https://bitaps.com/"),
            new BlockChainExplorer("OXT", "https://oxt.me/transaction/", "https://oxt.me/address/"),
            new BlockChainExplorer("Blockcypher", "https://live.blockcypher.com/btc/tx/", "https://live.blockcypher.com/btc/address/"),
            new BlockChainExplorer("Tradeblock", "https://tradeblock.com/bitcoin/tx/", "https://tradeblock.com/bitcoin/address/"),
            new BlockChainExplorer("Blockexplorer", "https://blockexplorer.com/tx/", "https://blockexplorer.com/address/"),
            new BlockChainExplorer("Biteasy", "https://www.biteasy.com/transactions/", "https://www.biteasy.com/addresses/"),
            new BlockChainExplorer("Blockonomics", "https://www.blockonomics.co/api/tx?txid=", "https://www.blockonomics.co/#/search?q="),
            new BlockChainExplorer("Chainflyer", "http://chainflyer.bitflyer.jp/Transaction/", "http://chainflyer.bitflyer.jp/Address/"),
            new BlockChainExplorer("Smartbit", "https://www.smartbit.com.au/tx/", "https://www.smartbit.com.au/address/"),
            new BlockChainExplorer("SoChain. Wow.", "https://chain.so/tx/BTC/", "https://chain.so/address/BTC/"),
            new BlockChainExplorer("Blockchain.info", "https://blockchain.info/tx/", "https://blockchain.info/address/"),
            new BlockChainExplorer("Insight", "https://insight.bitpay.com/tx/", "https://insight.bitpay.com/address/")

    ));
    private static final ArrayList<BlockChainExplorer> BTC_TEST_NET_EXPLORERS = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("Blockcypher", "https://live.blockcypher.com/btc-testnet/tx/", "https://live.blockcypher.com/btc-testnet/address/"),
            new BlockChainExplorer("Blocktrail", "https://www.blocktrail.com/tBTC/tx/", "https://www.blocktrail.com/tBTC/address/"),
            new BlockChainExplorer("Blockexplorer", "https://blockexplorer.com/testnet/tx/", "https://blockexplorer.com/testnet/address/"),
            new BlockChainExplorer("Biteasy", "https://www.biteasy.com/testnet/transactions/", "https://www.biteasy.com/testnet/addresses/"),
            new BlockChainExplorer("Smartbit", "https://testnet.smartbit.com.au/tx/", "https://testnet.smartbit.com.au/address/"),
            new BlockChainExplorer("SoChain. Wow.", "https://chain.so/tx/BTCTEST/", "https://chain.so/address/BTCTEST/")
    ));

    public static final BlockChainExplorer BSQ_MAIN_NET_EXPLORER = new BlockChainExplorer("BSQ", "https://explorer.bisq.network/tx.html?tx=",
            "https://explorer.bisq.network/Address.html?addr=");
    public static final BlockChainExplorer BSQ_TEST_NET_EXPLORER = new BlockChainExplorer("BSQ", "https://explorer.bisq.network/testnet/tx.html?tx=",
            "https://explorer.bisq.network/testnet/Address.html?addr=");

    private static final ArrayList<BlockChainExplorer> LTC_MAIN_NET_EXPLORERS = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("Blockcypher", "https://live.blockcypher.com/ltc/tx", "https://live.blockcypher.com/ltc/address"),
            new BlockChainExplorer("CryptoID", "https://chainz.cryptoid.info/ltc/tx.dws?", "https://chainz.cryptoid.info/ltc/address.dws?"),
            new BlockChainExplorer("Abe Search", "http://explorer.litecoin.net/tx/", "http://explorer.litecoin.net/address/"),
            new BlockChainExplorer("SoChain", "https://chain.so/tx/LTC/", "https://chain.so/address/LTC/"),
            new BlockChainExplorer("Blockr.io", "http://ltc.blockr.io/tx/info/", "http://ltc.blockr.io/address/info/")
    ));

    private static final ArrayList<BlockChainExplorer> LTC_TEST_NET_EXPLORERS = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("SoChain", "https://chain.so/tx/LTCTEST/", "https://chain.so/address/LTCTEST/")
    ));

    private static final ArrayList<BlockChainExplorer> DOGE_MAIN_NET_EXPLORERS = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("SoChain", "https://chain.so/tx/doge/", "https://chain.so/address/doge/")
    ));
    private static final ArrayList<BlockChainExplorer> DOGE_TEST_NET_EXPLORERS = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("SoChain", "https://chain.so/tx/DOGETEST/", "https://chain.so/address/DOGETEST/")
    ));

    private static final ArrayList<BlockChainExplorer> DASH_MAIN_NET_EXPLORERS = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("SoChain", "https://chain.so/tx/dash/", "https://chain.so/address/dash/")
    ));
    private static final ArrayList<BlockChainExplorer> DASH_TEST_NET_EXPLORERS = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("SoChain", "https://chain.so/tx/DASHTEST/", "https://chain.so/address/DASHTEST/")
    ));


    // payload is initialized so the default values are available for Property initialization.
    @Setter
    @Delegate(excludes = ExcludesDelegateMethods.class)
    private PreferencesPayload prefPayload = new PreferencesPayload();
    private boolean initialReadDone = false;

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
    private final BisqEnvironment bisqEnvironment;
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
                       BisqEnvironment bisqEnvironment,
                       @Named(BtcOptionKeys.BTC_NODES) String btcNodesFromOptions,
                       @Named(BtcOptionKeys.USE_TOR_FOR_BTC) String useTorFlagFromOptions) {

        this.storage = storage;
        this.bisqEnvironment = bisqEnvironment;
        this.btcNodesFromOptions = btcNodesFromOptions;
        this.useTorFlagFromOptions = useTorFlagFromOptions;

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

        fiatCurrenciesAsObservable.addListener(this::updateTradeCurrencies);
        cryptoCurrenciesAsObservable.addListener(this::updateTradeCurrencies);
    }

    @Override
    public void readPersisted() {
        PreferencesPayload persisted = storage.initAndGetPersistedWithFileName("PreferencesPayload", 100);
        final BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
        TradeCurrency preferredTradeCurrency;
        if (persisted != null) {
            prefPayload = persisted;
            GlobalSettings.setLocale(new Locale(prefPayload.getUserLanguage(), prefPayload.getUserCountry().code));
            GlobalSettings.setUseAnimations(prefPayload.isUseAnimations());
            preferredTradeCurrency = checkNotNull(prefPayload.getPreferredTradeCurrency(), "preferredTradeCurrency must not be null");
            setPreferredTradeCurrency(preferredTradeCurrency);
            setFiatCurrencies(prefPayload.getFiatCurrencies());
            setCryptoCurrencies(prefPayload.getCryptoCurrencies());
        } else {
            prefPayload = new PreferencesPayload();
            prefPayload.setUserLanguage(GlobalSettings.getLocale().getLanguage());
            prefPayload.setUserCountry(CountryUtil.getDefaultCountry());
            GlobalSettings.setLocale(new Locale(prefPayload.getUserLanguage(), prefPayload.getUserCountry().code));
            preferredTradeCurrency = checkNotNull(CurrencyUtil.getCurrencyByCountryCode(prefPayload.getUserCountry().code),
                    "preferredTradeCurrency must not be null");
            prefPayload.setPreferredTradeCurrency(preferredTradeCurrency);
            setFiatCurrencies(CurrencyUtil.getMainFiatCurrencies());
            setCryptoCurrencies(CurrencyUtil.getMainCryptoCurrencies());

            switch (baseCurrencyNetwork.getCurrencyCode()) {
                case "BTC":
                    setBlockChainExplorerMainNet(BTC_MAIN_NET_EXPLORERS.get(0));
                    setBlockChainExplorerTestNet(BTC_TEST_NET_EXPLORERS.get(0));
                    break;
                case "LTC":
                    setBlockChainExplorerMainNet(LTC_MAIN_NET_EXPLORERS.get(0));
                    setBlockChainExplorerTestNet(LTC_TEST_NET_EXPLORERS.get(0));
                    break;
                case "DOGE":
                    setBlockChainExplorerMainNet(DOGE_MAIN_NET_EXPLORERS.get(0));
                    setBlockChainExplorerTestNet(DOGE_TEST_NET_EXPLORERS.get(0));
                    break;
                case "DASH":
                    setBlockChainExplorerMainNet(DASH_MAIN_NET_EXPLORERS.get(0));
                    setBlockChainExplorerTestNet(DASH_TEST_NET_EXPLORERS.get(0));
                    break;
                default:
                    throw new RuntimeException("BaseCurrencyNetwork not defined. BaseCurrencyNetwork=" + baseCurrencyNetwork);
            }

            prefPayload.setDirectoryChooserPath(Utilities.getSystemHomeDirectory());

            prefPayload.setOfferBookChartScreenCurrencyCode(preferredTradeCurrency.getCode());
            prefPayload.setTradeChartsScreenCurrencyCode(preferredTradeCurrency.getCode());
            prefPayload.setBuyScreenCurrencyCode(preferredTradeCurrency.getCode());
            prefPayload.setSellScreenCurrencyCode(preferredTradeCurrency.getCode());
        }

        prefPayload.setBsqBlockChainExplorer(baseCurrencyNetwork.isMainnet() ? BSQ_MAIN_NET_EXPLORER : BSQ_TEST_NET_EXPLORER);

        // We don't want to pass Preferences to all popups where the dont show again checkbox is used, so we use
        // that static lookup class to avoid static access to the Preferences directly.
        DontShowAgainLookup.setPreferences(this);

        GlobalSettings.setDefaultTradeCurrency(preferredTradeCurrency);

        // set all properties
        useAnimationsProperty.set(prefPayload.isUseAnimations());
        useCustomWithdrawalTxFeeProperty.set(prefPayload.isUseCustomWithdrawalTxFee());
        withdrawalTxFeeInBytesProperty.set(prefPayload.getWithdrawalTxFeeInBytes());

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

        initialReadDone = true;
        persist();
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

            if (prefPayload.getPreferredTradeCurrency() != null &&
                    prefPayload.getPreferredTradeCurrency().equals(tradeCurrency))
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

            if (prefPayload.getPreferredTradeCurrency() != null &&
                    prefPayload.getPreferredTradeCurrency().equals(tradeCurrency))
                setPreferredTradeCurrency(tradeCurrenciesAsObservable.get(0));
        } else {
            log.error("you cannot remove the last currency");
        }
    }

    public void setBlockChainExplorer(BlockChainExplorer blockChainExplorer) {
        if (BisqEnvironment.getBaseCurrencyNetwork().isMainnet())
            setBlockChainExplorerMainNet(blockChainExplorer);
        else
            setBlockChainExplorerTestNet(blockChainExplorer);
    }

    public void setTacAccepted(boolean tacAccepted) {
        prefPayload.setTacAccepted(tacAccepted);
        persist();
    }

    private void persist() {
        if (initialReadDone)
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
        prefPayload.setBuyerSecurityDepositAsLong(Math.min(Restrictions.getMaxBuyerSecurityDeposit().value,
                Math.max(Restrictions.getMinBuyerSecurityDeposit().value,
                        buyerSecurityDepositAsLong)));
        persist();
    }

    public void setSelectedPaymentAccountForCreateOffer(@Nullable PaymentAccount paymentAccount) {
        prefPayload.setSelectedPaymentAccountForCreateOffer(paymentAccount);
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

    public void setBridgeAddresses(List<String> bridgeAddresses) {
        prefPayload.setBridgeAddresses(bridgeAddresses);
        // We call that before shutdown so we dont want a delay here
        storage.queueUpForSave(prefPayload, 1);
    }

    // Only used from PB but keep it explicit as maybe it get used from the client and then we want to persist
    public void setPeerTagMap(Map<String, String> peerTagMap) {
        prefPayload.setPeerTagMap(peerTagMap);
        persist();
    }

    public void setBridgeOptionOrdinal(int bridgeOptionOrdinal) {
        prefPayload.setBridgeOptionOrdinal(bridgeOptionOrdinal);
        persist();
    }

    public void setTorTransportOrdinal(int torTransportOrdinal) {
        prefPayload.setTorTransportOrdinal(torTransportOrdinal);
        persist();
    }

    public void setCustomBridges(String customBridges) {
        prefPayload.setCustomBridges(customBridges);
        persist();
    }

    public void setBitcoinNodesOptionOrdinal(int bitcoinNodesOptionOrdinal) {
        prefPayload.setBitcoinNodesOptionOrdinal(bitcoinNodesOptionOrdinal);
        persist();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

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
        if (BisqEnvironment.getBaseCurrencyNetwork().isMainnet())
            return prefPayload.getBlockChainExplorerMainNet();
        else
            return prefPayload.getBlockChainExplorerTestNet();
    }

    public ArrayList<BlockChainExplorer> getBlockChainExplorers() {
        final BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
        switch (baseCurrencyNetwork) {
            case BTC_MAINNET:
                return BTC_MAIN_NET_EXPLORERS;
            case BTC_TESTNET:
            case BTC_REGTEST:
                return BTC_TEST_NET_EXPLORERS;
            case LTC_MAINNET:
                return LTC_MAIN_NET_EXPLORERS;
            case LTC_TESTNET:
            case LTC_REGTEST:
                return LTC_TEST_NET_EXPLORERS;
            case DOGE_MAINNET:
                return DOGE_MAIN_NET_EXPLORERS;
            case DOGE_TESTNET:
            case DOGE_REGTEST:
                return DOGE_TEST_NET_EXPLORERS;
            case DASH_MAINNET:
                return DASH_MAIN_NET_EXPLORERS;
            case DASH_REGTEST:
            case DASH_TESTNET:
                return DASH_TEST_NET_EXPLORERS;
            default:
                throw new RuntimeException("BaseCurrencyNetwork not defined. BaseCurrencyNetwork=" + baseCurrencyNetwork);
        }
    }

    public boolean showAgain(String key) {
        return !prefPayload.getDontShowAgainMap().containsKey(key) || !prefPayload.getDontShowAgainMap().get(key);
    }

    public boolean getUseTorForBitcoinJ() {
        // We override the useTorForBitcoinJ and set to false if we have bitcoinNodes set
        // This check includes localhost, so we also override useTorForBitcoinJ
        if (BisqEnvironment.getBaseCurrencyNetwork().isRegtest()
                || bisqEnvironment.isBitcoinLocalhostNodeRunning())
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

    @Override
    @Nullable
    public List<String> getBridgeAddresses() {
        return prefPayload.getBridgeAddresses();
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

        void setBridgeAddresses(List<String> bridgeAddresses);

        List<String> getBridgeAddresses();

        void setBridgeOptionOrdinal(int bridgeOptionOrdinal);

        void setTorTransportOrdinal(int torTransportOrdinal);

        void setCustomBridges(String customBridges);

        void setBitcoinNodesOptionOrdinal(int bitcoinNodesOption);
    }
}
