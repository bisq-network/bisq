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

package io.bisq.core.user;

import com.google.protobuf.Message;
import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Version;
import io.bisq.common.locale.*;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.BitcoinNetwork;
import io.bisq.core.btc.BtcOptionKeys;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.generated.protobuffer.PB;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public final class PreferencesImpl implements Preferences {

    ///////////////// START of STATIC ////////////////////////////////////////////////////////////////////////


    static {
        Locale locale = Locale.getDefault();
        PreferencesImpl.defaultLocale = locale;
        Res.applyLocaleToResourceBundle(getDefaultLocale());

        CountryUtil.setDefaultLocale(locale);
        CurrencyUtil.setDefaultLocale(locale);
        LanguageUtil.setDefaultLocale(locale);
        FiatCurrency.setDefaultLocale(locale);

        FiatCurrency currencyByCountryCode = CurrencyUtil.getCurrencyByCountryCode(CountryUtil.getDefaultCountryCode(locale), locale);
        PreferencesImpl.defaultTradeCurrency = currencyByCountryCode;
        CurrencyUtil.setDefaultTradeCurrency(currencyByCountryCode);
    }


    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    // Deactivate mBit for now as most screens are not supporting it yet
    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    private static final List<String> BTC_DENOMINATIONS = Arrays.asList(MonetaryFormat.CODE_BTC/*, MonetaryFormat.CODE_MBTC*/);

    transient static final private ArrayList<BlockChainExplorer> blockChainExplorersTestNet = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("Blocktrail", "https://www.blocktrail.com/tBTC/tx/", "https://www.blocktrail.com/tBTC/address/"),
            new BlockChainExplorer("Blockexplorer", "https://blockexplorer.com/testnet/tx/", "https://blockexplorer.com/testnet/address/"),
            new BlockChainExplorer("Blockr.io", "https://tbtc.blockr.io/tx/info/", "https://tbtc.blockr.io/address/info/"),
            new BlockChainExplorer("Biteasy", "https://www.biteasy.com/testnet/transactions/", "https://www.biteasy.com/testnet/addresses/"),
            new BlockChainExplorer("Smartbit", "https://testnet.smartbit.com.au/tx/", "https://testnet.smartbit.com.au/address/"),
            new BlockChainExplorer("SoChain. Wow.", "https://chain.so/tx/BTCTEST/", "https://chain.so/address/BTCTEST/")
    ));

    transient static final private ArrayList<BlockChainExplorer> blockChainExplorersMainNet = new ArrayList<>(Arrays.asList(
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

    @Getter
    private static Locale defaultLocale/* = Locale.getDefault()*/;

    @Getter
    private static TradeCurrency defaultTradeCurrency;
    private static boolean staticUseAnimations = true;

    transient private final Storage<Preferences> storage;
    transient private final BisqEnvironment bisqEnvironment;

    public static List<String> getBtcDenominations() {
        return BTC_DENOMINATIONS;
    }

    public static boolean useAnimations() {
        return staticUseAnimations;
    }

    ///////////////// END of STATIC /////////////////////////////////////////////////////////////////

    @Getter
    transient private BitcoinNetwork bitcoinNetwork;

    // Persisted fields
    @Getter
    private String userLanguage = LanguageUtil.getDefaultLanguage(defaultLocale);
    @Getter
    private Country userCountry = CountryUtil.getDefaultCountry(defaultLocale);
    private String btcDenomination = MonetaryFormat.CODE_BTC;
    @Getter
    private boolean useAnimations = DevEnv.STRESS_TEST_MODE ? false : true;
    @Getter
    private final List<FiatCurrency> fiatCurrencies;
    @Getter
    private final List<CryptoCurrency> cryptoCurrencies;
    @Getter
    private BlockChainExplorer blockChainExplorerMainNet;
    @Getter
    private BlockChainExplorer blockChainExplorerTestNet;
    @Nullable
    @Getter
    private String backupDirectory;
    @Getter
    private boolean autoSelectArbitrators = true;
    @Getter
    @Setter
    private Map<String, Boolean> dontShowAgainMap;
    @Getter
    private boolean tacAccepted;
    private boolean useTorForBitcoinJ = true;

    @Getter
    private boolean showOwnOffersInOfferBook = true;
    @Setter
    private Locale preferredLocale;
    @Getter
    private TradeCurrency preferredTradeCurrency;
    @Getter
    private long withdrawalTxFeeInBytes = 100;
    @Getter
    private boolean useCustomWithdrawalTxFee = false;

    @Getter
    private double maxPriceDistanceInPercent;
    @Getter
    private String offerBookChartScreenCurrencyCode = getDefaultTradeCurrency().getCode();
    @Getter
    private String tradeChartsScreenCurrencyCode = getDefaultTradeCurrency().getCode();

    @Getter
    private String buyScreenCurrencyCode = getDefaultTradeCurrency().getCode();
    @Getter
    private String sellScreenCurrencyCode = getDefaultTradeCurrency().getCode();
    @Getter
    private int tradeStatisticsTickUnitIndex = 3;

    @Getter
    @Setter
    private boolean useStickyMarketPrice = false;
    @Getter
    private boolean sortMarketCurrenciesNumerically = true;
    @Getter
    private boolean usePercentageBasedPrice = true;
    @Getter
    @Setter
    private Map<String, String> peerTagMap = new HashMap<>();
    @Getter
    private String bitcoinNodes = "";

    @Getter
    private List<String> ignoreTradersList = new ArrayList<>();
    @Getter
    private String directoryChooserPath;
    @Getter
    private long buyerSecurityDepositAsLong = Restrictions.DEFAULT_BUYER_SECURITY_DEPOSIT.value;
    @Nullable
    @Getter
    private PaymentAccount selectedPaymentAccountForCreateOffer;

    // Observable wrappers
    @Getter
    transient private final StringProperty btcDenominationProperty = new SimpleStringProperty(btcDenomination);
    @Getter
    transient private final BooleanProperty useAnimationsProperty = new SimpleBooleanProperty(useAnimations);
    @Getter
    transient private final BooleanProperty useCustomWithdrawalTxFeeProperty = new SimpleBooleanProperty(useCustomWithdrawalTxFee);
    @Getter
    transient private final LongProperty withdrawalTxFeeInBytesProperty = new SimpleLongProperty(withdrawalTxFeeInBytes);
    @Getter
    transient private final ObservableList<FiatCurrency> fiatCurrenciesAsObservable = FXCollections.observableArrayList();
    @Getter
    transient private final ObservableList<CryptoCurrency> cryptoCurrenciesAsObservable = FXCollections.observableArrayList();
    @Getter
    transient private final ObservableList<TradeCurrency> tradeCurrenciesAsObservable = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public PreferencesImpl(Storage<Preferences> storage, BisqEnvironment bisqEnvironment,
                           FeeService feeService,
                           @Named(BtcOptionKeys.BTC_NODES) String btcNodesFromOptions,
                           @Named(BtcOptionKeys.USE_TOR_FOR_BTC) String useTorFlagFromOptions) {
        this.storage = storage;
        this.bisqEnvironment = bisqEnvironment;

        // setup
      /*  
       Res.applyLocaleToResourceBundle(defaultLocale);
        CountryUtil.setDefaultLocale(defaultLocale);
        CurrencyUtil.setDefaultLocale(defaultLocale);
        LanguageUtil.setDefaultLocale(defaultLocale);
        FiatCurrency.setDefaultLocale(defaultLocale);

        FiatCurrency currencyByCountryCode = CurrencyUtil.getCurrencyByCountryCode(CountryUtil.getDefaultCountryCode(defaultLocale), defaultLocale);
        defaultTradeCurrency = currencyByCountryCode;
        CurrencyUtil.setDefaultTradeCurrency(currencyByCountryCode);
        */
        // end setup

        directoryChooserPath = Utilities.getSystemHomeDirectory();

        fiatCurrencies = new ArrayList<>(fiatCurrenciesAsObservable);
        cryptoCurrencies = new ArrayList<>(cryptoCurrenciesAsObservable);

        btcDenominationProperty.addListener((ov) -> {
            btcDenomination = btcDenominationProperty.get();
            storage.queueUpForSave();
        });
        useAnimationsProperty.addListener((ov) -> {
            useAnimations = useAnimationsProperty.get();
            staticUseAnimations = useAnimations;
            storage.queueUpForSave();
        });
        fiatCurrenciesAsObservable.addListener((javafx.beans.Observable ov) -> {
            fiatCurrencies.clear();
            fiatCurrencies.addAll(fiatCurrenciesAsObservable);
            fiatCurrencies.sort(TradeCurrency::compareTo);
            storage.queueUpForSave();
        });
        cryptoCurrenciesAsObservable.addListener((javafx.beans.Observable ov) -> {
            cryptoCurrencies.clear();
            cryptoCurrencies.addAll(cryptoCurrenciesAsObservable);
            cryptoCurrencies.sort(TradeCurrency::compareTo);
            storage.queueUpForSave();
        });

        useCustomWithdrawalTxFeeProperty.addListener((ov) -> {
            useCustomWithdrawalTxFee = useCustomWithdrawalTxFeeProperty.get();
            storage.queueUpForSave();
        });

        withdrawalTxFeeInBytesProperty.addListener((ov) -> {
            withdrawalTxFeeInBytes = withdrawalTxFeeInBytesProperty.get();
            storage.queueUpForSave();
        });

        Preferences persisted = storage.initAndGetPersisted(this);
        if (persisted != null) {
            setBtcDenomination(persisted.getBtcDenomination());
            setUseAnimations(persisted.getUseAnimations());

            setFiatCurrencies(persisted.getFiatCurrencies());
            setCryptoCurrencies(persisted.getCryptoCurrencies());

            setBlockChainExplorerTestNet(persisted.getBlockChainExplorerTestNet());
            setBlockChainExplorerMainNet(persisted.getBlockChainExplorerMainNet());

            setUseCustomWithdrawalTxFee(persisted.getUseCustomWithdrawalTxFee());
            setWithdrawalTxFeeInBytes(persisted.getWithdrawalTxFeeInBytes());

            // In case of an older version without that data we set it to defaults
            if (blockChainExplorerTestNet == null)
                setBlockChainExplorerTestNet(blockChainExplorersTestNet.get(0));
            if (blockChainExplorerMainNet == null)
                setBlockChainExplorerTestNet(blockChainExplorersMainNet.get(0));

            backupDirectory = persisted.getBackupDirectory();
            autoSelectArbitrators = persisted.isAutoSelectArbitrators();
            dontShowAgainMap = persisted.getDontShowAgainMap();
            tacAccepted = persisted.isTacAccepted();

            userLanguage = persisted.getUserLanguage();
            if (userLanguage == null)
                userLanguage = LanguageUtil.getDefaultLanguage(defaultLocale);
            userCountry = persisted.getUserCountry();
            if (userCountry == null)
                userCountry = CountryUtil.getDefaultCountry(defaultLocale);
            updateDefaultLocale();
            preferredTradeCurrency = persisted.getPreferredTradeCurrency();
            defaultTradeCurrency = preferredTradeCurrency;
            useTorForBitcoinJ = persisted.getUseTorForBitcoinJ();

            useStickyMarketPrice = persisted.isUseStickyMarketPrice();
            sortMarketCurrenciesNumerically = persisted.isSortMarketCurrenciesNumerically();

            usePercentageBasedPrice = persisted.isUsePercentageBasedPrice();
            showOwnOffersInOfferBook = persisted.isShowOwnOffersInOfferBook();
            maxPriceDistanceInPercent = persisted.getMaxPriceDistanceInPercent();

            bitcoinNodes = persisted.getBitcoinNodes();
            if (bitcoinNodes == null)
                bitcoinNodes = "";

            if (persisted.getPeerTagMap() != null)
                peerTagMap = persisted.getPeerTagMap();

            offerBookChartScreenCurrencyCode = persisted.getOfferBookChartScreenCurrencyCode();
            buyScreenCurrencyCode = persisted.getBuyScreenCurrencyCode();
            sellScreenCurrencyCode = persisted.getSellScreenCurrencyCode();
            tradeChartsScreenCurrencyCode = persisted.getTradeChartsScreenCurrencyCode();
            tradeStatisticsTickUnitIndex = persisted.getTradeStatisticsTickUnitIndex();

            if (persisted.getIgnoreTradersList() != null)
                ignoreTradersList = persisted.getIgnoreTradersList();

            if (persisted.getDirectoryChooserPath() != null)
                directoryChooserPath = persisted.getDirectoryChooserPath();

            buyerSecurityDepositAsLong = Math.min(Restrictions.MAX_BUYER_SECURITY_DEPOSIT.value,
                    Math.max(Restrictions.MIN_BUYER_SECURITY_DEPOSIT.value,
                            persisted.getBuyerSecurityDepositAsLong())
            );

            selectedPaymentAccountForCreateOffer = persisted.getSelectedPaymentAccountForCreateOffer();
        } else {
            setFiatCurrencies(CurrencyUtil.getAllMainFiatCurrencies(defaultLocale, getDefaultTradeCurrency()));
            setCryptoCurrencies(CurrencyUtil.getMainCryptoCurrencies());

            setBlockChainExplorerTestNet(blockChainExplorersTestNet.get(0));
            setBlockChainExplorerMainNet(blockChainExplorersMainNet.get(0));

            dontShowAgainMap = new HashMap<>();
            preferredLocale = defaultLocale;
            preferredTradeCurrency = getDefaultTradeCurrency();
            maxPriceDistanceInPercent = 0.1;

            storage.queueUpForSave();
        }

        this.bitcoinNetwork = bisqEnvironment.getBitcoinNetwork();

        fiatCurrenciesAsObservable.addListener(this::updateTradeCurrencies);
        cryptoCurrenciesAsObservable.addListener(this::updateTradeCurrencies);
        tradeCurrenciesAsObservable.addAll(fiatCurrencies);
        tradeCurrenciesAsObservable.addAll(cryptoCurrencies);

        // Override settings with options if set
        if (useTorFlagFromOptions != null && !useTorFlagFromOptions.isEmpty()) {
            if (useTorFlagFromOptions.equals("false"))
                setUseTorForBitcoinJ(false);
            else if (useTorFlagFromOptions.equals("true"))
                setUseTorForBitcoinJ(true);
        }

        if (btcNodesFromOptions != null && !btcNodesFromOptions.isEmpty())
            setBitcoinNodes(btcNodesFromOptions);
    }

    @Override
    public void dontShowAgain(String key, boolean dontShowAgain) {
        dontShowAgainMap.put(key, dontShowAgain);
        storage.queueUpForSave();
    }

    @Override
    public void resetDontShowAgain() {
        dontShowAgainMap.clear();
        storage.queueUpForSave();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setBtcDenomination(String btcDenomination) {
        this.btcDenominationProperty.set(btcDenomination);
    }

    @Override
    public void setUseAnimations(boolean useAnimations) {
        this.useAnimationsProperty.set(useAnimations);
    }

    @Override
    public void setBitcoinNetwork(BitcoinNetwork bitcoinNetwork) {
        if (this.bitcoinNetwork != bitcoinNetwork)
            bisqEnvironment.saveBitcoinNetwork(bitcoinNetwork);

        this.bitcoinNetwork = bitcoinNetwork;

        // We don't store the bitcoinNetwork locally as BitcoinNetwork is not serializable!
    }

    @Override
    public void addFiatCurrency(FiatCurrency tradeCurrency) {
        if (!fiatCurrenciesAsObservable.contains(tradeCurrency))
            fiatCurrenciesAsObservable.add(tradeCurrency);
    }

    @Override
    public void removeFiatCurrency(FiatCurrency tradeCurrency) {
        if (tradeCurrenciesAsObservable.size() > 1) {
            if (fiatCurrenciesAsObservable.contains(tradeCurrency))
                fiatCurrenciesAsObservable.remove(tradeCurrency);

            if (preferredTradeCurrency.equals(tradeCurrency))
                setPreferredTradeCurrency(tradeCurrenciesAsObservable.get(0));
        } else {
            log.error("you cannot remove the last currency");
        }
    }

    @Override
    public void addCryptoCurrency(CryptoCurrency tradeCurrency) {
        if (!cryptoCurrenciesAsObservable.contains(tradeCurrency))
            cryptoCurrenciesAsObservable.add(tradeCurrency);
    }

    @Override
    public void removeCryptoCurrency(CryptoCurrency tradeCurrency) {
        if (tradeCurrenciesAsObservable.size() > 1) {
            if (cryptoCurrenciesAsObservable.contains(tradeCurrency))
                cryptoCurrenciesAsObservable.remove(tradeCurrency);

            if (preferredTradeCurrency.equals(tradeCurrency))
                setPreferredTradeCurrency(tradeCurrenciesAsObservable.get(0));
        } else {
            log.error("you cannot remove the last currency");
        }
    }

    @Override
    public void setBlockChainExplorer(BlockChainExplorer blockChainExplorer) {
        if (bitcoinNetwork == BitcoinNetwork.MAINNET)
            setBlockChainExplorerMainNet(blockChainExplorer);
        else
            setBlockChainExplorerTestNet(blockChainExplorer);
    }

    @Override
    public void setTacAccepted(boolean tacAccepted) {
        this.tacAccepted = tacAccepted;
        storage.queueUpForSave();
    }

    @Override
    public void setUserLanguage(@NotNull String userLanguageCode) {
        this.userLanguage = userLanguageCode;
        updateDefaultLocale();
        storage.queueUpForSave();
    }

    @Override
    public void setUserCountry(@NotNull Country userCountry) {
        this.userCountry = userCountry;
        updateDefaultLocale();
        storage.queueUpForSave();
    }

    @Override
    public void setPreferredTradeCurrency(TradeCurrency preferredTradeCurrency) {
        if (preferredTradeCurrency != null) {
            this.preferredTradeCurrency = preferredTradeCurrency;
            defaultTradeCurrency = preferredTradeCurrency;
            storage.queueUpForSave();
        }
    }

    @Override
    public void setUseTorForBitcoinJ(boolean useTorForBitcoinJ) {
        this.useTorForBitcoinJ = useTorForBitcoinJ;
        storage.queueUpForSave();
    }

    @Override
    public void setShowOwnOffersInOfferBook(boolean showOwnOffersInOfferBook) {
        this.showOwnOffersInOfferBook = showOwnOffersInOfferBook;
        storage.queueUpForSave();
    }

    @Override
    public void setMaxPriceDistanceInPercent(double maxPriceDistanceInPercent) {
        this.maxPriceDistanceInPercent = maxPriceDistanceInPercent;
        storage.queueUpForSave();
    }

    @Override
    public void setBackupDirectory(String backupDirectory) {
        this.backupDirectory = backupDirectory;
        storage.queueUpForSave();
    }

    @Override
    public void setAutoSelectArbitrators(boolean autoSelectArbitrators) {
        this.autoSelectArbitrators = autoSelectArbitrators;
        storage.queueUpForSave();
    }

    @Override
    public void setUsePercentageBasedPrice(boolean usePercentageBasedPrice) {
        this.usePercentageBasedPrice = usePercentageBasedPrice;
        storage.queueUpForSave();
    }

    @Override
    public void setTagForPeer(String hostName, String tag) {
        peerTagMap.put(hostName, tag);
        storage.queueUpForSave();
    }

    @Override
    public void setOfferBookChartScreenCurrencyCode(String offerBookChartScreenCurrencyCode) {
        this.offerBookChartScreenCurrencyCode = offerBookChartScreenCurrencyCode;
        storage.queueUpForSave();
    }

    @Override
    public void setBuyScreenCurrencyCode(String buyScreenCurrencyCode) {
        this.buyScreenCurrencyCode = buyScreenCurrencyCode;
        storage.queueUpForSave();
    }

    @Override
    public void setSellScreenCurrencyCode(String sellScreenCurrencyCode) {
        this.sellScreenCurrencyCode = sellScreenCurrencyCode;
        storage.queueUpForSave();
    }

    @Override
    public void setIgnoreTradersList(List<String> ignoreTradersList) {
        this.ignoreTradersList = ignoreTradersList;
        storage.queueUpForSave();
    }

    @Override
    public void setDirectoryChooserPath(String directoryChooserPath) {
        this.directoryChooserPath = directoryChooserPath;
        storage.queueUpForSave();
    }

    @Override
    public void setTradeChartsScreenCurrencyCode(String tradeChartsScreenCurrencyCode) {
        this.tradeChartsScreenCurrencyCode = tradeChartsScreenCurrencyCode;
        storage.queueUpForSave();
    }

    @Override
    public void setTradeStatisticsTickUnitIndex(int tradeStatisticsTickUnitIndex) {
        this.tradeStatisticsTickUnitIndex = tradeStatisticsTickUnitIndex;
        storage.queueUpForSave();
    }

    @Override
    public void setSortMarketCurrenciesNumerically(boolean sortMarketCurrenciesNumerically) {
        this.sortMarketCurrenciesNumerically = sortMarketCurrenciesNumerically;
        storage.queueUpForSave();
    }

    @Override
    public void setBitcoinNodes(String bitcoinNodes) {
        this.bitcoinNodes = bitcoinNodes;
        storage.queueUpForSave(50);
    }

    @Override
    public void setUseCustomWithdrawalTxFee(boolean useCustomWithdrawalTxFee) {
        useCustomWithdrawalTxFeeProperty.set(useCustomWithdrawalTxFee);
    }

    @Override
    public void setWithdrawalTxFeeInBytes(long withdrawalTxFeeInBytes) {
        withdrawalTxFeeInBytesProperty.set(withdrawalTxFeeInBytes);
    }

    @Override
    public void setBuyerSecurityDepositAsLong(long buyerSecurityDepositAsLong) {
        this.buyerSecurityDepositAsLong = buyerSecurityDepositAsLong;
        storage.queueUpForSave();
    }

    @Override
    public void setSelectedPaymentAccountForCreateOffer(PaymentAccount paymentAccount) {
        this.selectedPaymentAccountForCreateOffer = paymentAccount;
        storage.queueUpForSave();
    }

    @Override
    public void setBlockChainExplorerTestNet(BlockChainExplorer blockChainExplorerTestNet) {
        this.blockChainExplorerTestNet = blockChainExplorerTestNet;
        storage.queueUpForSave();
    }

    @Override
    public void setBlockChainExplorerMainNet(BlockChainExplorer blockChainExplorerMainNet) {
        this.blockChainExplorerMainNet = blockChainExplorerMainNet;
        storage.queueUpForSave();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getBtcDenomination() {
        return btcDenominationProperty.get();
    }

    @Override
    public boolean getUseAnimations() {
        return useAnimationsProperty.get();
    }

    @Override
    public BlockChainExplorer getBlockChainExplorer() {
        if (bitcoinNetwork == BitcoinNetwork.MAINNET)
            return blockChainExplorerMainNet;
        else
            return blockChainExplorerTestNet;
    }

    @Override
    public ArrayList<BlockChainExplorer> getBlockChainExplorers() {
        if (bitcoinNetwork == BitcoinNetwork.MAINNET)
            return blockChainExplorersMainNet;
        else
            return blockChainExplorersTestNet;
    }

    @Override
    public boolean showAgain(String key) {
        return !dontShowAgainMap.containsKey(key) || !dontShowAgainMap.get(key);
    }

    @Override
    public boolean getUseTorForBitcoinJ() {
        // We override the useTorForBitcoinJ and set to false if we have bitcoinNodes set
        // Atm we don't support onion addresses there
        // This check includes localhost, so we also override useTorForBitcoinJ
        if (bitcoinNodes != null && !bitcoinNodes.isEmpty())
            return false;
        else
            return useTorForBitcoinJ;
    }

    @Override
    public boolean getUseCustomWithdrawalTxFee() {
        return useCustomWithdrawalTxFeeProperty.get();
    }

    @Override
    public long getWithdrawalTxFeeInBytes() {
        return withdrawalTxFeeInBytesProperty.get();
    }

    @Override
    public Coin getBuyerSecurityDepositAsCoin() {
        return Coin.valueOf(buyerSecurityDepositAsLong);
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

    private void setFiatCurrencies(List<FiatCurrency> currencies) {
        fiatCurrenciesAsObservable.setAll(currencies);
    }

    private void setCryptoCurrencies(List<CryptoCurrency> currencies) {
        cryptoCurrenciesAsObservable.setAll(currencies);
    }


    private void updateDefaultLocale() {
        defaultLocale = new Locale(userLanguage, userCountry.code);
        Res.applyLocaleToResourceBundle(defaultLocale);
    }

    @Override
    public Message toProtobuf() {
        PB.Preferences.Builder builder = PB.Preferences.newBuilder()
                .setUserLanguage(userLanguage)
                .setUserCountry((PB.Country) userCountry.toProtobuf())
                .setBtcDenomination(btcDenomination)
                .setUseAnimations(useAnimations)
                .addAllFiatCurrencies(fiatCurrencies.stream().map(fiatCurrency -> ((PB.TradeCurrency) fiatCurrency.toProtobuf())).collect(Collectors.toList()))
                .addAllCryptoCurrencies(cryptoCurrencies.stream().map(cryptoCurrency -> ((PB.TradeCurrency) cryptoCurrency.toProtobuf())).collect(Collectors.toList()))
                .setBlockChainExplorerMainNet((PB.BlockChainExplorer) blockChainExplorerMainNet.toProtobuf())
                .setBlockChainExplorerTestNet((PB.BlockChainExplorer) blockChainExplorerTestNet.toProtobuf())
                .setAutoSelectArbitrators(autoSelectArbitrators)
                .putAllDontShowAgainMap(dontShowAgainMap)
                .setTacAccepted(tacAccepted)
                .setUseTorForBitcoinJ(useTorForBitcoinJ)
                .setShowOwnOffersInOfferBook(showOwnOffersInOfferBook)
                .setPreferredTradeCurrency((PB.TradeCurrency) preferredTradeCurrency.toProtobuf())
                .setWithdrawalTxFeeInBytes(withdrawalTxFeeInBytes)
                .setMaxPriceDistanceInPercent(maxPriceDistanceInPercent)
                .setOfferBookChartScreenCurrencyCode(offerBookChartScreenCurrencyCode)
                .setTradeChartsScreenCurrencyCode(tradeChartsScreenCurrencyCode)
                .setUseStickyMarketPrice(useStickyMarketPrice)
                .setSortMarketCurrenciesNumerically(sortMarketCurrenciesNumerically)
                .setUsePercentageBasedPrice(usePercentageBasedPrice)
                .putAllPeerTagMap(peerTagMap)
                .setBitcoinNodes(bitcoinNodes)
                .addAllIgnoreTradersList(ignoreTradersList)
                .setDirectoryChooserPath(directoryChooserPath)
                .setBuyerSecurityDepositAsLong(buyerSecurityDepositAsLong);
        Optional.ofNullable(backupDirectory).ifPresent(backupDir -> builder.setBackupDirectory(backupDir));
        Optional.ofNullable(preferredLocale).ifPresent(locale -> builder.setPreferredLocale(PB.Locale.newBuilder().setCountry(preferredLocale.getCountry()).setLanguage(preferredLocale.getLanguage()).setVariant(preferredLocale.getVariant())));

        return PB.DiskEnvelope.newBuilder().setPreferences(builder).build();
    }
}
