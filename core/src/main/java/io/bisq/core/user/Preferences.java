package io.bisq.core.user;

import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import io.bisq.common.GlobalSettings;
import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Version;
import io.bisq.common.locale.*;
import io.bisq.common.persistance.Persistable;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.btc.BitcoinNetwork;
import io.bisq.core.btc.BtcOptionKeys;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.btc.wallet.WalletUtils;
import io.bisq.core.payment.PaymentAccount;
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
public final class Preferences implements Persistable {

    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    // Deactivate mBit for now as most screens are not supporting it yet
    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    transient private static final List<String> BTC_DENOMINATIONS = Arrays.asList(MonetaryFormat.CODE_BTC/*, MonetaryFormat.CODE_MBTC*/);

    transient static final private ArrayList<BlockChainExplorer> BLOCK_CHAIN_EXPLORERS_TEST_NET = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("Blocktrail", "https://www.blocktrail.com/tBTC/tx/", "https://www.blocktrail.com/tBTC/address/"),
            new BlockChainExplorer("Blockexplorer", "https://blockexplorer.com/testnet/tx/", "https://blockexplorer.com/testnet/address/"),
            new BlockChainExplorer("Blockr.io", "https://tbtc.blockr.io/tx/info/", "https://tbtc.blockr.io/address/info/"),
            new BlockChainExplorer("Biteasy", "https://www.biteasy.com/testnet/transactions/", "https://www.biteasy.com/testnet/addresses/"),
            new BlockChainExplorer("Smartbit", "https://testnet.smartbit.com.au/tx/", "https://testnet.smartbit.com.au/address/"),
            new BlockChainExplorer("SoChain. Wow.", "https://chain.so/tx/BTCTEST/", "https://chain.so/address/BTCTEST/")
    ));

    transient static final private ArrayList<BlockChainExplorer> BLOCK_CHAIN_EXPLORERS_MAIN_NET = new ArrayList<>(Arrays.asList(
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


    // Persisted fields
    @Getter
    private String userLanguage;
    @Getter
    private Country userCountry;
    @Getter
    private String btcDenomination = BTC_DENOMINATIONS.get(0);
    @Getter
    private boolean useAnimations = DevEnv.STRESS_TEST_MODE ? false : true;
    @Getter
    private final ArrayList<FiatCurrency> fiatCurrencies = new ArrayList<>();
    @Getter
    private final ArrayList<CryptoCurrency> cryptoCurrencies = new ArrayList<>();
    @Getter
    private BlockChainExplorer blockChainExplorerMainNet;
    @Getter
    private BlockChainExplorer blockChainExplorerTestNet;
    @Getter
    private BlockChainExplorer bsqBlockChainExplorer = new BlockChainExplorer("bisq", "https://explorer.bisq.io/tx.html?tx=",
            "https://explorer.bisq.io/Address.html?addr=");
    @Getter
    @Nullable
    private String backupDirectory;
    @Getter
    private boolean autoSelectArbitrators = true;
    @Getter
    private Map<String, Boolean> dontShowAgainMap = new HashMap<>();
    @Getter
    private boolean tacAccepted;
    @Getter
    private boolean useTorForBitcoinJ = true;
    @Getter
    private boolean showOwnOffersInOfferBook = true;
    @Getter
    @Nullable
    private TradeCurrency preferredTradeCurrency;
    @Getter
    private long withdrawalTxFeeInBytes = 100;
    @Getter
    private boolean useCustomWithdrawalTxFee = false;
    @Getter
    private double maxPriceDistanceInPercent = 0.1;
    @Getter
    @Nullable
    private String offerBookChartScreenCurrencyCode;
    @Getter
    @Nullable
    private String tradeChartsScreenCurrencyCode;
    @Getter
    @Nullable
    private String buyScreenCurrencyCode;
    @Getter
    @Nullable
    private String sellScreenCurrencyCode;
    @Getter
    private int tradeStatisticsTickUnitIndex = 3;
    // TODO can be removed (wait for PB merge)
    private boolean useStickyMarketPrice = false;
    @Getter
    private boolean sortMarketCurrenciesNumerically = true;
    @Getter
    private boolean usePercentageBasedPrice = true;
    @Getter
    private Map<String, String> peerTagMap = new HashMap<>();
    @Getter
    private String bitcoinNodes = "";
    @Getter
    private List<String> ignoreTradersList = new ArrayList<>();
    @Getter
    private String directoryChooserPath;
    @Getter
    private long buyerSecurityDepositAsLong = Restrictions.DEFAULT_BUYER_SECURITY_DEPOSIT.value;
    @Getter
    @Nullable
    private PaymentAccount selectedPaymentAccountForCreateOffer;
    @Getter
    private boolean payFeeInBtc = true;
    @Getter
    private boolean resyncSpvRequested;

    // Observable wrappers
    @Getter
    transient private final StringProperty btcDenominationProperty = new SimpleStringProperty(btcDenomination);
    @Getter
    transient private final BooleanProperty useAnimationsProperty = new SimpleBooleanProperty(useAnimations);
    @Getter
    transient private final BooleanProperty useCustomWithdrawalTxFeeProperty = new SimpleBooleanProperty(useCustomWithdrawalTxFee);
    @Getter
    transient private final LongProperty withdrawalTxFeeInBytesProperty = new SimpleLongProperty(withdrawalTxFeeInBytes);

    transient private final ObservableList<FiatCurrency> fiatCurrenciesAsObservable = FXCollections.observableArrayList();
    transient private final ObservableList<CryptoCurrency> cryptoCurrenciesAsObservable = FXCollections.observableArrayList();
    transient private final ObservableList<TradeCurrency> tradeCurrenciesAsObservable = FXCollections.observableArrayList();

    transient private final Storage<Preferences> storage;
    transient private final String btcNodesFromOptions;
    transient private final String useTorFlagFromOptions;
    @Setter
    transient private boolean doPersist;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////


    @SuppressWarnings("WeakerAccess")
    @Inject
    public Preferences(Storage<Preferences> storage,
                       @Named(BtcOptionKeys.BTC_NODES) String btcNodesFromOptions,
                       @Named(BtcOptionKeys.USE_TOR_FOR_BTC) String useTorFlagFromOptions) {

        this.storage = storage;
        this.btcNodesFromOptions = btcNodesFromOptions;
        this.useTorFlagFromOptions = useTorFlagFromOptions;
    }

    public void init() {
        // We don't want to pass Preferences to all popups where the dont show again checkbox is used, so we use
        // that static lookup class to avoid static access to the Preferences directly.
        DontShowAgainLookup.setPreferences(this);

        btcDenominationProperty.addListener((ov) -> {
            btcDenomination = btcDenominationProperty.get();
            GlobalSettings.setBtcDenomination(btcDenomination);
            persist();
        });
        useAnimationsProperty.addListener((ov) -> {
            useAnimations = useAnimationsProperty.get();
            GlobalSettings.setUseAnimations(useAnimations);
            persist();
        });
        fiatCurrenciesAsObservable.addListener((javafx.beans.Observable ov) -> {
            fiatCurrencies.clear();
            fiatCurrencies.addAll(fiatCurrenciesAsObservable);
            fiatCurrencies.sort(TradeCurrency::compareTo);
            persist();
        });
        cryptoCurrenciesAsObservable.addListener((javafx.beans.Observable ov) -> {
            cryptoCurrencies.clear();
            cryptoCurrencies.addAll(cryptoCurrenciesAsObservable);
            cryptoCurrencies.sort(TradeCurrency::compareTo);
            persist();
        });

        useCustomWithdrawalTxFeeProperty.addListener((ov) -> {
            useCustomWithdrawalTxFee = useCustomWithdrawalTxFeeProperty.get();
            persist();
        });

        withdrawalTxFeeInBytesProperty.addListener((ov) -> {
            withdrawalTxFeeInBytes = withdrawalTxFeeInBytesProperty.get();
            persist();
        });

        TradeCurrency defaultTradeCurrency;
        Preferences persisted = storage.initAndGetPersisted(this);
        doPersist = true;
        if (persisted != null) {
            userLanguage = persisted.getUserLanguage();
            userCountry = persisted.getUserCountry();
            GlobalSettings.setLocale(new Locale(userLanguage, userCountry.code));
            GlobalSettings.setUseAnimations(persisted.getUseAnimations());
            preferredTradeCurrency = persisted.getPreferredTradeCurrency();
            defaultTradeCurrency = preferredTradeCurrency;

            setBtcDenomination(persisted.getBtcDenomination());
            setUseAnimations(persisted.getUseAnimations());

            setFiatCurrencies(persisted.getFiatCurrencies());
            setCryptoCurrencies(persisted.getCryptoCurrencies());

            setBlockChainExplorerTestNet(persisted.getBlockChainExplorerTestNet());
            setBlockChainExplorerMainNet(persisted.getBlockChainExplorerMainNet());
            setBsqBlockChainExplorer(persisted.getBsqBlockChainExplorer());

            setUseCustomWithdrawalTxFee(persisted.getUseCustomWithdrawalTxFee());
            setWithdrawalTxFeeInBytes(persisted.getWithdrawalTxFeeInBytes());

            backupDirectory = persisted.getBackupDirectory();
            autoSelectArbitrators = persisted.isAutoSelectArbitrators();
            dontShowAgainMap = persisted.getDontShowAgainMap();
            tacAccepted = persisted.isTacAccepted();
            useTorForBitcoinJ = persisted.getUseTorForBitcoinJ();
            sortMarketCurrenciesNumerically = persisted.isSortMarketCurrenciesNumerically();
            usePercentageBasedPrice = persisted.isUsePercentageBasedPrice();
            showOwnOffersInOfferBook = persisted.isShowOwnOffersInOfferBook();
            maxPriceDistanceInPercent = persisted.getMaxPriceDistanceInPercent();
            bitcoinNodes = persisted.getBitcoinNodes();
            peerTagMap = persisted.getPeerTagMap();
            offerBookChartScreenCurrencyCode = persisted.getOfferBookChartScreenCurrencyCode();
            buyScreenCurrencyCode = persisted.getBuyScreenCurrencyCode();
            sellScreenCurrencyCode = persisted.getSellScreenCurrencyCode();
            tradeChartsScreenCurrencyCode = persisted.getTradeChartsScreenCurrencyCode();
            tradeStatisticsTickUnitIndex = persisted.getTradeStatisticsTickUnitIndex();
            ignoreTradersList = persisted.getIgnoreTradersList();
            directoryChooserPath = persisted.getDirectoryChooserPath();
            selectedPaymentAccountForCreateOffer = persisted.getSelectedPaymentAccountForCreateOffer();
            payFeeInBtc = persisted.getPayFeeInBtc();
            setBuyerSecurityDepositAsLong(persisted.getBuyerSecurityDepositAsLong());
            resyncSpvRequested = persisted.isResyncSpvRequested();
        } else {
            userLanguage = GlobalSettings.getLocale().getLanguage();
            userCountry = CountryUtil.getDefaultCountry();
            GlobalSettings.setLocale(new Locale(userLanguage, userCountry.code));
            defaultTradeCurrency = CurrencyUtil.getCurrencyByCountryCode(userCountry.code);
            preferredTradeCurrency = defaultTradeCurrency;

            setFiatCurrencies(CurrencyUtil.getMainFiatCurrencies());
            setCryptoCurrencies(CurrencyUtil.getMainCryptoCurrencies());
            setBlockChainExplorerTestNet(BLOCK_CHAIN_EXPLORERS_TEST_NET.get(0));
            setBlockChainExplorerMainNet(BLOCK_CHAIN_EXPLORERS_MAIN_NET.get(0));
            directoryChooserPath = Utilities.getSystemHomeDirectory();
            dontShowAgainMap = new HashMap<>();

            persist();
        }
        GlobalSettings.setDefaultTradeCurrency(defaultTradeCurrency);
        offerBookChartScreenCurrencyCode = defaultTradeCurrency.getCode();
        tradeChartsScreenCurrencyCode = defaultTradeCurrency.getCode();
        buyScreenCurrencyCode = defaultTradeCurrency.getCode();
        sellScreenCurrencyCode = defaultTradeCurrency.getCode();
        btcDenomination = MonetaryFormat.CODE_BTC;

        btcDenominationProperty.set(btcDenomination);
        useAnimationsProperty.set(useAnimations);

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

    public void dontShowAgain(String key, boolean dontShowAgain) {
        dontShowAgainMap.put(key, dontShowAgain);
        persist();
    }

    public void resetDontShowAgain() {
        dontShowAgainMap.clear();
        persist();
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
        if (WalletUtils.getBitcoinNetwork() == BitcoinNetwork.MAINNET)
            setBlockChainExplorerMainNet(blockChainExplorer);
        else
            setBlockChainExplorerTestNet(blockChainExplorer);
    }

    public void setTacAccepted(boolean tacAccepted) {
        this.tacAccepted = tacAccepted;
        persist();
    }

    private void persist() {
        if (doPersist)
            storage.queueUpForSave();
    }

    public void setUserLanguage(@NotNull String userLanguageCode) {
        this.userLanguage = userLanguageCode;
        if (userCountry != null && userLanguage != null)
            GlobalSettings.setLocale(new Locale(userLanguage, userCountry.code));
        persist();
    }

    public void setUserCountry(@NotNull Country userCountry) {
        this.userCountry = userCountry;
        if (userLanguage != null)
            GlobalSettings.setLocale(new Locale(userLanguage, userCountry.code));
        persist();
    }

    public void setPreferredTradeCurrency(TradeCurrency preferredTradeCurrency) {
        if (preferredTradeCurrency != null) {
            this.preferredTradeCurrency = preferredTradeCurrency;
            GlobalSettings.setDefaultTradeCurrency(preferredTradeCurrency);
            persist();
        }
    }

    public void setUseTorForBitcoinJ(boolean useTorForBitcoinJ) {
        this.useTorForBitcoinJ = useTorForBitcoinJ;
        persist();
    }

    public void setShowOwnOffersInOfferBook(boolean showOwnOffersInOfferBook) {
        this.showOwnOffersInOfferBook = showOwnOffersInOfferBook;
        persist();
    }

    public void setMaxPriceDistanceInPercent(double maxPriceDistanceInPercent) {
        this.maxPriceDistanceInPercent = maxPriceDistanceInPercent;
        persist();
    }

    public void setBackupDirectory(String backupDirectory) {
        this.backupDirectory = backupDirectory;
        persist();
    }

    public void setAutoSelectArbitrators(boolean autoSelectArbitrators) {
        this.autoSelectArbitrators = autoSelectArbitrators;
        persist();
    }

    public void setUsePercentageBasedPrice(boolean usePercentageBasedPrice) {
        this.usePercentageBasedPrice = usePercentageBasedPrice;
        persist();
    }

    public void setTagForPeer(String hostName, String tag) {
        peerTagMap.put(hostName, tag);
        persist();
    }

    public void setOfferBookChartScreenCurrencyCode(String offerBookChartScreenCurrencyCode) {
        this.offerBookChartScreenCurrencyCode = offerBookChartScreenCurrencyCode;
        persist();
    }

    public void setBuyScreenCurrencyCode(String buyScreenCurrencyCode) {
        this.buyScreenCurrencyCode = buyScreenCurrencyCode;
        persist();
    }

    public void setSellScreenCurrencyCode(String sellScreenCurrencyCode) {
        this.sellScreenCurrencyCode = sellScreenCurrencyCode;
        persist();
    }

    public void setIgnoreTradersList(List<String> ignoreTradersList) {
        this.ignoreTradersList = ignoreTradersList;
        persist();
    }

    public void setDirectoryChooserPath(String directoryChooserPath) {
        this.directoryChooserPath = directoryChooserPath;
        persist();
    }

    public void setTradeChartsScreenCurrencyCode(String tradeChartsScreenCurrencyCode) {
        this.tradeChartsScreenCurrencyCode = tradeChartsScreenCurrencyCode;
        persist();
    }

    public void setTradeStatisticsTickUnitIndex(int tradeStatisticsTickUnitIndex) {
        this.tradeStatisticsTickUnitIndex = tradeStatisticsTickUnitIndex;
        persist();
    }

    public void setSortMarketCurrenciesNumerically(boolean sortMarketCurrenciesNumerically) {
        this.sortMarketCurrenciesNumerically = sortMarketCurrenciesNumerically;
        persist();
    }

    public void setBitcoinNodes(String bitcoinNodes) {
        this.bitcoinNodes = bitcoinNodes;
        persist();
    }

    public void setUseCustomWithdrawalTxFee(boolean useCustomWithdrawalTxFee) {
        useCustomWithdrawalTxFeeProperty.set(useCustomWithdrawalTxFee);
    }

    public void setWithdrawalTxFeeInBytes(long withdrawalTxFeeInBytes) {
        withdrawalTxFeeInBytesProperty.set(withdrawalTxFeeInBytes);
    }

    public void setBuyerSecurityDepositAsLong(long buyerSecurityDepositAsLong) {
        this.buyerSecurityDepositAsLong = Math.min(Restrictions.MAX_BUYER_SECURITY_DEPOSIT.value,
                Math.max(Restrictions.MIN_BUYER_SECURITY_DEPOSIT.value,
                        buyerSecurityDepositAsLong));
        persist();
    }

    public void setSelectedPaymentAccountForCreateOffer(@Nullable PaymentAccount paymentAccount) {
        this.selectedPaymentAccountForCreateOffer = paymentAccount;
        persist();
    }

    public void setBsqBlockChainExplorer(BlockChainExplorer bsqBlockChainExplorer) {
        this.bsqBlockChainExplorer = bsqBlockChainExplorer;
        persist();
    }

    public void setPayFeeInBtc(boolean payFeeInBtc) {
        this.payFeeInBtc = payFeeInBtc;
        persist();
    }

    private void setFiatCurrencies(List<FiatCurrency> currencies) {
        fiatCurrenciesAsObservable.setAll(currencies);
    }

    private void setCryptoCurrencies(List<CryptoCurrency> currencies) {
        cryptoCurrenciesAsObservable.setAll(currencies);
    }

    public void setBlockChainExplorerTestNet(BlockChainExplorer blockChainExplorerTestNet) {
        this.blockChainExplorerTestNet = blockChainExplorerTestNet;
        persist();
    }

    public void setBlockChainExplorerMainNet(BlockChainExplorer blockChainExplorerMainNet) {
        this.blockChainExplorerMainNet = blockChainExplorerMainNet;
        persist();
    }

    public void setResyncSpvRequested(boolean resyncSpvRequested) {
        this.resyncSpvRequested = resyncSpvRequested;
        // We call that before shutdown so we dont want a delay here
        if (doPersist)
            storage.queueUpForSave(1);
    }

    // Only used from PB but keep it explicit as maybe it get used from the client and then we want to persist
    public void setDontShowAgainMap(Map<String, Boolean> dontShowAgainMap) {
        this.dontShowAgainMap = dontShowAgainMap;
        persist();
    }

    // Only used from PB but keep it explicit as maybe it get used from the client and then we want to persist
    public void setPeerTagMap(Map<String, String> peerTagMap) {
        this.peerTagMap = peerTagMap;
        persist();
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
            return blockChainExplorerMainNet;
        else
            return blockChainExplorerTestNet;
    }

    public ArrayList<BlockChainExplorer> getBlockChainExplorers() {
        if (WalletUtils.getBitcoinNetwork() == BitcoinNetwork.MAINNET)
            return BLOCK_CHAIN_EXPLORERS_MAIN_NET;
        else
            return BLOCK_CHAIN_EXPLORERS_TEST_NET;
    }

    public boolean showAgain(String key) {
        return !dontShowAgainMap.containsKey(key) || !dontShowAgainMap.get(key);
    }

    public boolean getUseTorForBitcoinJ() {
        // We override the useTorForBitcoinJ and set to false if we have bitcoinNodes set
        // Atm we don't support onion addresses there
        // This check includes localhost, so we also override useTorForBitcoinJ
        if (bitcoinNodes != null && !bitcoinNodes.isEmpty() || WalletUtils.getBitcoinNetwork() == BitcoinNetwork.REGTEST)
            return false;
        else
            return useTorForBitcoinJ;
    }


    public boolean getUseCustomWithdrawalTxFee() {
        return useCustomWithdrawalTxFeeProperty.get();
    }

    public BooleanProperty useCustomWithdrawalTxFeeProperty() {
        return useCustomWithdrawalTxFeeProperty;
    }

    public LongProperty withdrawalTxFeeInBytesProperty() {
        return withdrawalTxFeeInBytesProperty;
    }

    public long getWithdrawalTxFeeInBytes() {
        return withdrawalTxFeeInBytesProperty.get();
    }

    public long getBuyerSecurityDepositAsLong() {
        return buyerSecurityDepositAsLong;
    }

    public Coin getBuyerSecurityDepositAsCoin() {
        return Coin.valueOf(buyerSecurityDepositAsLong);
    }

    @Nullable
    public PaymentAccount getSelectedPaymentAccountForCreateOffer() {
        return selectedPaymentAccountForCreateOffer;
    }

    public boolean getPayFeeInBtc() {
        return payFeeInBtc;
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


    @Override
    public Message toProtobuf() {
        PB.Preferences.Builder builder = PB.Preferences.newBuilder()
                .setUserLanguage(userLanguage)
                .setUserCountry((PB.Country) userCountry.toProtobuf())
                .addAllFiatCurrencies(fiatCurrencies.stream().map(fiatCurrency -> ((PB.TradeCurrency) fiatCurrency.toProtobuf())).collect(Collectors.toList()))
                .addAllCryptoCurrencies(cryptoCurrencies.stream().map(cryptoCurrency -> ((PB.TradeCurrency) cryptoCurrency.toProtobuf())).collect(Collectors.toList()))
                .setBlockChainExplorerMainNet((PB.BlockChainExplorer) blockChainExplorerMainNet.toProtobuf())
                .setBlockChainExplorerTestNet((PB.BlockChainExplorer) blockChainExplorerTestNet.toProtobuf())
                .setBsqBlockChainExplorer((PB.BlockChainExplorer) bsqBlockChainExplorer.toProtobuf())
                .setAutoSelectArbitrators(autoSelectArbitrators)
                .putAllDontShowAgainMap(dontShowAgainMap)
                .setTacAccepted(tacAccepted)
                .setUseTorForBitcoinJ(useTorForBitcoinJ)
                .setShowOwnOffersInOfferBook(showOwnOffersInOfferBook)
                .setPreferredTradeCurrency((PB.TradeCurrency) preferredTradeCurrency.toProtobuf())
                .setWithdrawalTxFeeInBytes(withdrawalTxFeeInBytes)
                .setMaxPriceDistanceInPercent(maxPriceDistanceInPercent)
                .setSortMarketCurrenciesNumerically(sortMarketCurrenciesNumerically)
                .setUsePercentageBasedPrice(usePercentageBasedPrice)
                .putAllPeerTagMap(peerTagMap)
                .setBitcoinNodes(bitcoinNodes)
                .addAllIgnoreTradersList(ignoreTradersList)
                .setDirectoryChooserPath(directoryChooserPath)
                .setBuyerSecurityDepositAsLong(buyerSecurityDepositAsLong);

        Optional.ofNullable(backupDirectory).ifPresent(backupDir -> builder.setBackupDirectory(backupDir));
        Optional.ofNullable(offerBookChartScreenCurrencyCode).ifPresent(code -> builder.setOfferBookChartScreenCurrencyCode(code));
        Optional.ofNullable(tradeChartsScreenCurrencyCode).ifPresent(code -> builder.setTradeChartsScreenCurrencyCode(code));
        Optional.ofNullable(buyScreenCurrencyCode).ifPresent(code -> builder.setBuyScreenCurrencyCode(code));
        Optional.ofNullable(sellScreenCurrencyCode).ifPresent(code -> builder.setSellScreenCurrencyCode(code));
        Optional.ofNullable(selectedPaymentAccountForCreateOffer).ifPresent(
                account -> builder.setSelectedPaymentAccountForCreateOffer(selectedPaymentAccountForCreateOffer.toProtobuf()));
        return PB.DiskEnvelope.newBuilder().setPreferences(builder).build();
    }

    @Override
    public Parser getParser() {
        return null;
    }
}
