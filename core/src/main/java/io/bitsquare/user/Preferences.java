/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.user;

import io.bitsquare.app.BitsquareEnvironment;
import io.bitsquare.app.Version;
import io.bitsquare.btc.BitcoinNetwork;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.common.persistance.Persistable;
import io.bitsquare.locale.*;
import io.bitsquare.storage.Storage;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

public final class Preferences implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(Preferences.class);

    // Deactivate mBit for now as most screens are not supporting it yet
    private static final List<String> BTC_DENOMINATIONS = Arrays.asList(MonetaryFormat.CODE_BTC/*, MonetaryFormat.CODE_MBTC*/);
    transient static final private ArrayList<BlockChainExplorer> blockChainExplorersTestNet = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("Blocktrail", "https://www.blocktrail.com/tBTC/tx/", "https://www.blocktrail.com/tBTC/address/"),
            new BlockChainExplorer("Blockexplorer", "https://blockexplorer.com/testnet/tx/", "https://blockexplorer.com/testnet/address/"),
            new BlockChainExplorer("Blockr.io", "https://tbtc.blockr.io/tx/info/", "https://tbtc.blockr.io/address/info/"),
            new BlockChainExplorer("Biteasy", "https://www.biteasy.com/testnet/transactions/", "https://www.biteasy.com/testnet/addresses/")
    ));

    transient static final private ArrayList<BlockChainExplorer> blockChainExplorersMainNet = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("Blocktrail", "https://www.blocktrail.com/BTC/tx/", "https://www.blocktrail.com/BTC/address/"),
            new BlockChainExplorer("Tradeblock.com", "https://tradeblock.com/bitcoin/tx/", "https://tradeblock.com/bitcoin/address/"),
            new BlockChainExplorer("Blockchain.info", "https://blockchain.info/tx/", "https://blockchain.info/address/"),
            new BlockChainExplorer("Blockexplorer", "https://blockexplorer.com/tx/", "https://blockexplorer.com/address/"),
            new BlockChainExplorer("Blockr.io", "https://btc.blockr.io/tx/info/", "https://btc.blockr.io/address/info/"),
            new BlockChainExplorer("Biteasy", "https://www.biteasy.com/transactions/", "https://www.biteasy.com/addresses/"),
            new BlockChainExplorer("XVG Explorer", "https://blockexperts.com/xvg/tx/", "https://blockexperts.com/xvg/address/")
    ));

    public static List<String> getBtcDenominations() {
        return BTC_DENOMINATIONS;
    }

    private static Locale defaultLocale = Locale.getDefault();
    //TODO test with other locales
    // private static Locale defaultLocale = Locale.US;

    public static Locale getDefaultLocale() {
        return defaultLocale;
    }

    private static TradeCurrency defaultTradeCurrency = new FiatCurrency(CurrencyUtil.getCurrencyByCountryCode(CountryUtil.getDefaultCountryCode()).getCurrency().getCurrencyCode());

    public static TradeCurrency getDefaultTradeCurrency() {
        return defaultTradeCurrency;
    }

    private static boolean staticUseAnimations = true;

    transient private final Storage<Preferences> storage;
    transient private final BitsquareEnvironment bitsquareEnvironment;

    transient private BitcoinNetwork bitcoinNetwork;

    // Persisted fields
    private String btcDenomination = MonetaryFormat.CODE_BTC;
    private boolean useAnimations = true;
    private final ArrayList<FiatCurrency> fiatCurrencies;
    private final ArrayList<CryptoCurrency> cryptoCurrencies;
    private BlockChainExplorer blockChainExplorerMainNet;
    private BlockChainExplorer blockChainExplorerTestNet;
    private String backupDirectory;
    private boolean autoSelectArbitrators = true;
    private final Map<String, Boolean> dontShowAgainMap;
    private boolean tacAccepted;
    // Don't remove as we don't want to break old serialized data
    private boolean useTorForBitcoinJ = false;
    private boolean showOwnOffersInOfferBook;
    private Locale preferredLocale;
    private TradeCurrency preferredTradeCurrency;
    private long nonTradeTxFeePerKB = FeePolicy.getNonTradeFeePerKb().value;
    private double maxPriceDistanceInPercent;
    private boolean useInvertedMarketPrice;
    private boolean useStickyMarketPrice = false;
    private boolean usePercentageBasedPrice = false;

    // Observable wrappers
    transient private final StringProperty btcDenominationProperty = new SimpleStringProperty(btcDenomination);
    transient private final BooleanProperty useAnimationsProperty = new SimpleBooleanProperty(useAnimations);
    transient private final BooleanProperty useInvertedMarketPriceProperty = new SimpleBooleanProperty(useInvertedMarketPrice);
    transient private final ObservableList<FiatCurrency> fiatCurrenciesAsObservable = FXCollections.observableArrayList();
    transient private final ObservableList<CryptoCurrency> cryptoCurrenciesAsObservable = FXCollections.observableArrayList();
    transient private final ObservableList<TradeCurrency> tradeCurrenciesAsObservable = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Preferences(Storage<Preferences> storage, BitsquareEnvironment bitsquareEnvironment) {
        log.debug("Preferences " + this);
        this.storage = storage;
        this.bitsquareEnvironment = bitsquareEnvironment;

        Preferences persisted = storage.initAndGetPersisted(this);
        if (persisted != null) {
            setBtcDenomination(persisted.btcDenomination);
            setUseAnimations(persisted.useAnimations);
            setUseInvertedMarketPrice(persisted.useInvertedMarketPrice);

            setFiatCurrencies(persisted.fiatCurrencies);
            fiatCurrencies = new ArrayList<>(fiatCurrenciesAsObservable);

            setCryptoCurrencies(persisted.cryptoCurrencies);
            cryptoCurrencies = new ArrayList<>(cryptoCurrenciesAsObservable);

            setBlockChainExplorerTestNet(persisted.getBlockChainExplorerTestNet());
            setBlockChainExplorerMainNet(persisted.getBlockChainExplorerMainNet());
            // In case of an older version without that data we set it to defaults
            if (blockChainExplorerTestNet == null)
                setBlockChainExplorerTestNet(blockChainExplorersTestNet.get(0));
            if (blockChainExplorerMainNet == null)
                setBlockChainExplorerTestNet(blockChainExplorersMainNet.get(0));

            backupDirectory = persisted.getBackupDirectory();
            autoSelectArbitrators = persisted.getAutoSelectArbitrators();
            dontShowAgainMap = persisted.getDontShowAgainMap();
            tacAccepted = persisted.getTacAccepted();

            preferredLocale = persisted.getPreferredLocale();
            defaultLocale = preferredLocale;
            preferredTradeCurrency = persisted.getPreferredTradeCurrency();
            defaultTradeCurrency = preferredTradeCurrency;
            // useTorForBitcoinJ = persisted.getUseTorForBitcoinJ();
            useTorForBitcoinJ = false;
            useStickyMarketPrice = persisted.getUseStickyMarketPrice();
            usePercentageBasedPrice = persisted.getUsePercentageBasedPrice();
            showOwnOffersInOfferBook = persisted.getShowOwnOffersInOfferBook();
            maxPriceDistanceInPercent = persisted.getMaxPriceDistanceInPercent();
            // Backward compatible to version 0.3.6. Can be removed after a while
            if (maxPriceDistanceInPercent == 0d)
                maxPriceDistanceInPercent = 0.2;

            try {
                setNonTradeTxFeePerKB(persisted.getNonTradeTxFeePerKB());
            } catch (Exception e) {
                // leave default value
            }
        } else {
            setFiatCurrencies(CurrencyUtil.getAllMainFiatCurrencies());
            fiatCurrencies = new ArrayList<>(fiatCurrenciesAsObservable);

            setCryptoCurrencies(CurrencyUtil.getMainCryptoCurrencies());
            cryptoCurrencies = new ArrayList<>(cryptoCurrenciesAsObservable);

            setBlockChainExplorerTestNet(blockChainExplorersTestNet.get(0));
            setBlockChainExplorerMainNet(blockChainExplorersMainNet.get(0));

            dontShowAgainMap = new HashMap<>();
            preferredLocale = getDefaultLocale();
            preferredTradeCurrency = getDefaultTradeCurrency();
            maxPriceDistanceInPercent = 0.2;

            storage.queueUpForSave();
        }


        this.bitcoinNetwork = bitsquareEnvironment.getBitcoinNetwork();

        // Use that to guarantee update of the serializable field and to make a storage update in case of a change
        btcDenominationProperty.addListener((ov) -> {
            btcDenomination = btcDenominationProperty.get();
            storage.queueUpForSave(2000);
        });
        useAnimationsProperty.addListener((ov) -> {
            useAnimations = useAnimationsProperty.get();
            staticUseAnimations = useAnimations;
            storage.queueUpForSave(2000);
        });
        useInvertedMarketPriceProperty.addListener((ov) -> {
            useInvertedMarketPrice = useInvertedMarketPriceProperty.get();
            storage.queueUpForSave(2000);
        });
        fiatCurrenciesAsObservable.addListener((Observable ov) -> {
            fiatCurrencies.clear();
            fiatCurrencies.addAll(fiatCurrenciesAsObservable);
            storage.queueUpForSave();
        });
        cryptoCurrenciesAsObservable.addListener((Observable ov) -> {
            cryptoCurrencies.clear();
            cryptoCurrencies.addAll(cryptoCurrenciesAsObservable);
            storage.queueUpForSave();
        });

        fiatCurrenciesAsObservable.addListener((ListChangeListener<FiatCurrency>) this::updateTradeCurrencies);
        cryptoCurrenciesAsObservable.addListener((ListChangeListener<CryptoCurrency>) this::updateTradeCurrencies);
        tradeCurrenciesAsObservable.addAll(fiatCurrencies);
        tradeCurrenciesAsObservable.addAll(cryptoCurrencies);
    }

    public void dontShowAgain(String key, boolean dontShowAgain) {
        dontShowAgainMap.put(key, dontShowAgain);
        storage.queueUpForSave(1000);
    }

    public void resetDontShowAgainForType() {
        dontShowAgainMap.clear();
        storage.queueUpForSave(1000);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setBtcDenomination(String btcDenomination) {
        this.btcDenominationProperty.set(btcDenomination);
    }

    public void setUseAnimations(boolean useAnimations) {
        this.useAnimationsProperty.set(useAnimations);
    }

    public void setUseInvertedMarketPrice(boolean useInvertedMarketPrice) {
        this.useInvertedMarketPriceProperty.set(useInvertedMarketPrice);
    }

    public void setBitcoinNetwork(BitcoinNetwork bitcoinNetwork) {
        if (this.bitcoinNetwork != bitcoinNetwork)
            bitsquareEnvironment.saveBitcoinNetwork(bitcoinNetwork);

        this.bitcoinNetwork = bitcoinNetwork;

        // We don't store the bitcoinNetwork locally as BitcoinNetwork is not serializable!
    }

    public void addFiatCurrency(FiatCurrency tradeCurrency) {
        if (!fiatCurrenciesAsObservable.contains(tradeCurrency))
            fiatCurrenciesAsObservable.add(tradeCurrency);
    }

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

    public void addCryptoCurrency(CryptoCurrency tradeCurrency) {
        if (!cryptoCurrenciesAsObservable.contains(tradeCurrency))
            cryptoCurrenciesAsObservable.add(tradeCurrency);
    }

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

    public void setBlockChainExplorer(BlockChainExplorer blockChainExplorer) {
        if (bitcoinNetwork == BitcoinNetwork.MAINNET)
            setBlockChainExplorerMainNet(blockChainExplorer);
        else
            setBlockChainExplorerTestNet(blockChainExplorer);
    }

    public void setTacAccepted(boolean tacAccepted) {
        this.tacAccepted = tacAccepted;
        storage.queueUpForSave();
    }

    public void setPreferredLocale(Locale preferredLocale) {
        this.preferredLocale = preferredLocale;
        defaultLocale = preferredLocale;
        storage.queueUpForSave();
    }

    public void setPreferredTradeCurrency(TradeCurrency preferredTradeCurrency) {
        if (preferredTradeCurrency != null) {
            this.preferredTradeCurrency = preferredTradeCurrency;
            defaultTradeCurrency = preferredTradeCurrency;
            storage.queueUpForSave();
        }
    }

    public void setNonTradeTxFeePerKB(long nonTradeTxFeePerKB) throws Exception {
        if (nonTradeTxFeePerKB < Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.value)
            throw new Exception("Transaction fee must be at least 5 satoshi/byte");

        if (nonTradeTxFeePerKB > 500_000)
            throw new Exception("Transaction fee is in the range of 10-100 satoshi/byte. Your input is above any reasonable value (>500 satoshi/byte).");

        this.nonTradeTxFeePerKB = nonTradeTxFeePerKB;
        FeePolicy.setNonTradeFeePerKb(Coin.valueOf(nonTradeTxFeePerKB));
        storage.queueUpForSave();
    }

   /* public void setUseTorForBitcoinJ(boolean useTorForBitcoinJ) {
        this.useTorForBitcoinJ = useTorForBitcoinJ;
        storage.queueUpForSave();
    }*/

    public void setShowOwnOffersInOfferBook(boolean showOwnOffersInOfferBook) {
        this.showOwnOffersInOfferBook = showOwnOffersInOfferBook;
        storage.queueUpForSave();
    }

    public void setMaxPriceDistanceInPercent(double maxPriceDistanceInPercent) {
        this.maxPriceDistanceInPercent = maxPriceDistanceInPercent;
        storage.queueUpForSave();
    }

    public void setBackupDirectory(String backupDirectory) {
        this.backupDirectory = backupDirectory;
        storage.queueUpForSave();
    }

    public void setAutoSelectArbitrators(boolean autoSelectArbitrators) {
        this.autoSelectArbitrators = autoSelectArbitrators;
        storage.queueUpForSave();
    }

    public boolean flipUseInvertedMarketPrice() {
        setUseInvertedMarketPrice(!getUseInvertedMarketPrice());
        return getUseInvertedMarketPrice();
    }

    public void setUseStickyMarketPrice(boolean useStickyMarketPrice) {
        this.useStickyMarketPrice = useStickyMarketPrice;
        storage.queueUpForSave();
    }

    public void setUsePercentageBasedPrice(boolean usePercentageBasedPrice) {
        this.usePercentageBasedPrice = usePercentageBasedPrice;
        storage.queueUpForSave();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getBtcDenomination() {
        return btcDenominationProperty.get();
    }

    public StringProperty btcDenominationProperty() {
        return btcDenominationProperty;
    }

    public boolean getUseAnimations() {
        return useAnimationsProperty.get();
    }

    public BooleanProperty useAnimationsProperty() {
        return useAnimationsProperty;
    }


    public static boolean useAnimations() {
        return staticUseAnimations;
    }

    public boolean getUseInvertedMarketPrice() {
        return useInvertedMarketPriceProperty.get();
    }

    public BooleanProperty useInvertedMarketPriceProperty() {
        return useInvertedMarketPriceProperty;
    }

    public BitcoinNetwork getBitcoinNetwork() {
        return bitcoinNetwork;
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

    public BlockChainExplorer getBlockChainExplorerTestNet() {
        return blockChainExplorerTestNet;
    }

    public BlockChainExplorer getBlockChainExplorerMainNet() {
        return blockChainExplorerMainNet;
    }

    public BlockChainExplorer getBlockChainExplorer() {
        if (bitcoinNetwork == BitcoinNetwork.MAINNET)
            return blockChainExplorerMainNet;
        else
            return blockChainExplorerTestNet;
    }

    public ArrayList<BlockChainExplorer> getBlockChainExplorers() {
        if (bitcoinNetwork == BitcoinNetwork.MAINNET)
            return blockChainExplorersMainNet;
        else
            return blockChainExplorersTestNet;
    }

    public String getBackupDirectory() {
        return backupDirectory;
    }


    public boolean getAutoSelectArbitrators() {
        return autoSelectArbitrators;
    }

    public Map<String, Boolean> getDontShowAgainMap() {
        return dontShowAgainMap;
    }

    public boolean showAgain(String key) {
        return !dontShowAgainMap.containsKey(key) || !dontShowAgainMap.get(key);
    }

    public boolean getTacAccepted() {
        return tacAccepted;
    }

    public Locale getPreferredLocale() {
        return preferredLocale;
    }

    public TradeCurrency getPreferredTradeCurrency() {
        return preferredTradeCurrency;
    }

    public long getNonTradeTxFeePerKB() {
        return Math.max(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.value, nonTradeTxFeePerKB);
    }

    public boolean getUseTorForBitcoinJ() {
        return useTorForBitcoinJ;
    }

    public boolean getShowOwnOffersInOfferBook() {
        return showOwnOffersInOfferBook;
    }

    public double getMaxPriceDistanceInPercent() {
        return maxPriceDistanceInPercent;
    }

    public boolean getUseStickyMarketPrice() {
        return useStickyMarketPrice;
    }

    public boolean getUsePercentageBasedPrice() {
        return usePercentageBasedPrice;
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

    private void setBlockChainExplorerTestNet(BlockChainExplorer blockChainExplorerTestNet) {
        this.blockChainExplorerTestNet = blockChainExplorerTestNet;
        storage.queueUpForSave(2000);
    }

    private void setBlockChainExplorerMainNet(BlockChainExplorer blockChainExplorerMainNet) {
        this.blockChainExplorerMainNet = blockChainExplorerMainNet;
        storage.queueUpForSave(2000);
    }
}
