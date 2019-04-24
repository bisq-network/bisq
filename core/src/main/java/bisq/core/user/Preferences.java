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

package bisq.core.user;

import bisq.core.app.AppOptionKeys;
import bisq.core.app.BisqEnvironment;
import bisq.core.btc.BaseCurrencyNetwork;
import bisq.core.btc.BtcOptionKeys;
import bisq.core.btc.nodes.BtcNodes;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.dao.DaoOptionKeys;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.PaymentAccount;

import bisq.network.p2p.network.BridgeAddressProvider;

import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public final class Preferences implements PersistedDataHost, BridgeAddressProvider {

    private static final ArrayList<BlockChainExplorer> BTC_MAIN_NET_EXPLORERS = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("Blockstream.info", "https://blockstream.info/tx/", "https://blockstream.info/address/"),
            new BlockChainExplorer("Blockstream.info Tor V3", "http://explorerzydxu5ecjrkwceayqybizmpjjznk5izmitf2modhcusuqlid.onion/tx/", "http://explorerzydxu5ecjrkwceayqybizmpjjznk5izmitf2modhcusuqlid.onion/address/"),
            new BlockChainExplorer("OXT", "https://oxt.me/transaction/", "https://oxt.me/address/"),
            new BlockChainExplorer("Bitaps", "https://bitaps.com/", "https://bitaps.com/"),
            new BlockChainExplorer("Blockcypher", "https://live.blockcypher.com/btc/tx/", "https://live.blockcypher.com/btc/address/"),
            new BlockChainExplorer("Tradeblock", "https://tradeblock.com/bitcoin/tx/", "https://tradeblock.com/bitcoin/address/"),
            new BlockChainExplorer("Biteasy", "https://www.biteasy.com/transactions/", "https://www.biteasy.com/addresses/"),
            new BlockChainExplorer("Blockonomics", "https://www.blockonomics.co/api/tx?txid=", "https://www.blockonomics.co/#/search?q="),
            new BlockChainExplorer("Chainflyer", "http://chainflyer.bitflyer.jp/Transaction/", "http://chainflyer.bitflyer.jp/Address/"),
            new BlockChainExplorer("Smartbit", "https://www.smartbit.com.au/tx/", "https://www.smartbit.com.au/address/"),
            new BlockChainExplorer("SoChain. Wow.", "https://chain.so/tx/BTC/", "https://chain.so/address/BTC/"),
            new BlockChainExplorer("Blockchain.info", "https://blockchain.info/tx/", "https://blockchain.info/address/"),
            new BlockChainExplorer("Insight", "https://insight.bitpay.com/tx/", "https://insight.bitpay.com/address/")
    ));
    private static final ArrayList<BlockChainExplorer> BTC_TEST_NET_EXPLORERS = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("Blockstream.info", "https://blockstream.info/testnet/tx/", "https://blockstream.info/testnet/address/"),
            new BlockChainExplorer("Blockstream.info Tor V3", "http://explorerzydxu5ecjrkwceayqybizmpjjznk5izmitf2modhcusuqlid.onion/testnet/tx/", "http://explorerzydxu5ecjrkwceayqybizmpjjznk5izmitf2modhcusuqlid.onion/testnet/address/"),
            new BlockChainExplorer("Blockcypher", "https://live.blockcypher.com/btc-testnet/tx/", "https://live.blockcypher.com/btc-testnet/address/"),
            new BlockChainExplorer("Blocktrail", "https://www.blocktrail.com/tBTC/tx/", "https://www.blocktrail.com/tBTC/address/"),
            new BlockChainExplorer("Biteasy", "https://www.biteasy.com/testnet/transactions/", "https://www.biteasy.com/testnet/addresses/"),
            new BlockChainExplorer("Smartbit", "https://testnet.smartbit.com.au/tx/", "https://testnet.smartbit.com.au/address/"),
            new BlockChainExplorer("SoChain. Wow.", "https://chain.so/tx/BTCTEST/", "https://chain.so/address/BTCTEST/")
    ));
    private static final ArrayList<BlockChainExplorer> BTC_DAO_TEST_NET_EXPLORERS = new ArrayList<>(Collections.singletonList(
            new BlockChainExplorer("BTC DAO-testnet explorer", "https://bisq.network/explorer/btc/dao_testnet/tx/", "https://bisq.network/explorer/btc/dao_testnet/address/")
    ));

    public static final BlockChainExplorer BSQ_MAIN_NET_EXPLORER = new BlockChainExplorer("BSQ", "https://explorer.bisq.network/tx.html?tx=",
            "https://explorer.bisq.network/Address.html?addr=");
    public static final BlockChainExplorer BSQ_TEST_NET_EXPLORER = new BlockChainExplorer("BSQ", "https://explorer.bisq.network/testnet/tx.html?tx=",
            "https://explorer.bisq.network/testnet/Address.html?addr=");

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
    private final ObservableMap<String, Boolean> dontShowAgainMapAsObservable = FXCollections.observableHashMap();

    private final Storage<PreferencesPayload> storage;
    private final BisqEnvironment bisqEnvironment;
    private final String btcNodesFromOptions, useTorFlagFromOptions, referralIdFromOptions, fullDaoNodeFromOptions,
            rpcUserFromOptions, rpcPasswordFromOptions;
    @Getter
    private final BooleanProperty useStandbyModeProperty = new SimpleBooleanProperty(prefPayload.isUseStandbyMode());


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////


    @SuppressWarnings("WeakerAccess")
    @Inject
    public Preferences(Storage<PreferencesPayload> storage,
                       BisqEnvironment bisqEnvironment,
                       @Named(BtcOptionKeys.BTC_NODES) String btcNodesFromOptions,
                       @Named(BtcOptionKeys.USE_TOR_FOR_BTC) String useTorFlagFromOptions,
                       @Named(AppOptionKeys.REFERRAL_ID) String referralId,
                       @Named(DaoOptionKeys.FULL_DAO_NODE) String fullDaoNode,
                       @Named(DaoOptionKeys.RPC_USER) String rpcUser,
                       @Named(DaoOptionKeys.RPC_PASSWORD) String rpcPassword) {


        this.storage = storage;
        this.bisqEnvironment = bisqEnvironment;
        this.btcNodesFromOptions = btcNodesFromOptions;
        this.useTorFlagFromOptions = useTorFlagFromOptions;
        this.referralIdFromOptions = referralId;
        this.fullDaoNodeFromOptions = fullDaoNode;
        this.rpcUserFromOptions = rpcUser;
        this.rpcPasswordFromOptions = rpcPassword;

        useAnimationsProperty.addListener((ov) -> {
            prefPayload.setUseAnimations(useAnimationsProperty.get());
            GlobalSettings.setUseAnimations(prefPayload.isUseAnimations());
            persist();
        });

        useStandbyModeProperty.addListener((ov) -> {
            prefPayload.setUseStandbyMode(useStandbyModeProperty.get());
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
        BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
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
        useStandbyModeProperty.set(prefPayload.isUseStandbyMode());
        useCustomWithdrawalTxFeeProperty.set(prefPayload.isUseCustomWithdrawalTxFee());
        withdrawalTxFeeInBytesProperty.set(prefPayload.getWithdrawalTxFeeInBytes());

        tradeCurrenciesAsObservable.addAll(prefPayload.getFiatCurrencies());
        tradeCurrenciesAsObservable.addAll(prefPayload.getCryptoCurrencies());
        dontShowAgainMapAsObservable.putAll(getDontShowAgainMap());

        // Override settings with options if set
        if (useTorFlagFromOptions != null && !useTorFlagFromOptions.isEmpty()) {
            if (useTorFlagFromOptions.equals("false"))
                setUseTorForBitcoinJ(false);
            else if (useTorFlagFromOptions.equals("true"))
                setUseTorForBitcoinJ(true);
        }

        if (btcNodesFromOptions != null && !btcNodesFromOptions.isEmpty()) {
            if (getBitcoinNodes() != null && !getBitcoinNodes().equals(btcNodesFromOptions)) {
                log.warn("The Bitcoin node(s) from the program argument and the one(s) persisted in the UI are different. " +
                        "The Bitcoin node(s) {} from the program argument will be used.", btcNodesFromOptions);
            }
            setBitcoinNodes(btcNodesFromOptions);
            setBitcoinNodesOptionOrdinal(BtcNodes.BitcoinNodesOption.CUSTOM.ordinal());
        }
        if (referralIdFromOptions != null && !referralIdFromOptions.isEmpty())
            setReferralId(referralIdFromOptions);

        if (fullDaoNodeFromOptions != null && !fullDaoNodeFromOptions.isEmpty())
            setDaoFullNode(fullDaoNodeFromOptions.toLowerCase().equals("true"));

        if (rpcUserFromOptions != null && !rpcUserFromOptions.isEmpty())
            setRpcUser(rpcUserFromOptions);

        if (rpcPasswordFromOptions != null && !rpcPasswordFromOptions.isEmpty())
            setRpcPw(rpcPasswordFromOptions);

        // For users from old versions the 4 flags a false but we want to have it true by default
        // PhoneKeyAndToken is also null so we can use that to enable the flags
        if (prefPayload.getPhoneKeyAndToken() == null) {
            setUseSoundForMobileNotifications(true);
            setUseTradeNotifications(true);
            setUseMarketNotifications(true);
            setUsePriceNotifications(true);
        }

        initialReadDone = true;
        persist();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void dontShowAgain(String key, boolean dontShowAgain) {
        prefPayload.getDontShowAgainMap().put(key, dontShowAgain);
        persist();
        dontShowAgainMapAsObservable.put(key, dontShowAgain);
    }

    public void resetDontShowAgain() {
        prefPayload.getDontShowAgainMap().clear();
        persist();
        dontShowAgainMapAsObservable.clear();
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
        fiatCurrenciesAsObservable.setAll(currencies.stream()
                .map(fiatCurrency -> new FiatCurrency(fiatCurrency.getCurrency()))
                .distinct().collect(Collectors.toList()));
    }

    private void setCryptoCurrencies(List<CryptoCurrency> currencies) {
        cryptoCurrenciesAsObservable.setAll(currencies.stream().distinct().collect(Collectors.toList()));
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

    public void setReferralId(String referralId) {
        prefPayload.setReferralId(referralId);
        persist();
    }

    public void setPhoneKeyAndToken(String phoneKeyAndToken) {
        prefPayload.setPhoneKeyAndToken(phoneKeyAndToken);
        persist();
    }

    public void setUseSoundForMobileNotifications(boolean value) {
        prefPayload.setUseSoundForMobileNotifications(value);
        persist();
    }

    public void setUseTradeNotifications(boolean value) {
        prefPayload.setUseTradeNotifications(value);
        persist();
    }

    public void setUseMarketNotifications(boolean value) {
        prefPayload.setUseMarketNotifications(value);
        persist();
    }

    public void setUsePriceNotifications(boolean value) {
        prefPayload.setUsePriceNotifications(value);
        persist();
    }

    public void setUseStandbyMode(boolean useStandbyMode) {
        this.useStandbyModeProperty.set(useStandbyMode);
    }

    public void setDaoFullNode(boolean value) {
        prefPayload.setDaoFullNode(value);
        persist();
    }

    public void setRpcUser(String value) {
        prefPayload.setRpcUser(value);
        persist();
    }

    public void setRpcPw(String value) {
        prefPayload.setRpcPw(value);
        persist();
    }

    public void setTakeOfferSelectedPaymentAccountId(String value) {
        prefPayload.setTakeOfferSelectedPaymentAccountId(value);
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

    public ObservableMap<String, Boolean> getDontShowAgainMapAsObservable() {
        return dontShowAgainMapAsObservable;
    }

    public BlockChainExplorer getBlockChainExplorer() {
        BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
        switch (baseCurrencyNetwork) {
            case BTC_MAINNET:
                return prefPayload.getBlockChainExplorerMainNet();
            case BTC_TESTNET:
            case BTC_REGTEST:
                return prefPayload.getBlockChainExplorerTestNet();
            case BTC_DAO_TESTNET:
                return BTC_DAO_TEST_NET_EXPLORERS.get(0);
            default:
                throw new RuntimeException("BaseCurrencyNetwork not defined. BaseCurrencyNetwork=" + baseCurrencyNetwork);
        }
    }

    public ArrayList<BlockChainExplorer> getBlockChainExplorers() {
        BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
        switch (baseCurrencyNetwork) {
            case BTC_MAINNET:
                return BTC_MAIN_NET_EXPLORERS;
            case BTC_TESTNET:
            case BTC_REGTEST:
                return BTC_TEST_NET_EXPLORERS;
            case BTC_DAO_TESTNET:
                return BTC_DAO_TEST_NET_EXPLORERS;
            default:
                throw new RuntimeException("BaseCurrencyNetwork not defined. BaseCurrencyNetwork=" + baseCurrencyNetwork);
        }
    }

    public boolean showAgain(String key) {
        return !prefPayload.getDontShowAgainMap().containsKey(key) || !prefPayload.getDontShowAgainMap().get(key);
    }

    public boolean getUseTorForBitcoinJ() {
        // We override the useTorForBitcoinJ and set it to false if we detected a localhost node or if we are not on mainnet,
        // unless the useTorForBtc parameter is explicitly provided.
        // On testnet there are very few Bitcoin tor nodes and we don't provide tor nodes.
        if ((!BisqEnvironment.getBaseCurrencyNetwork().isMainnet()
                || bisqEnvironment.isBitcoinLocalhostNodeRunning())
                && (useTorFlagFromOptions == null || useTorFlagFromOptions.isEmpty()))
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

    //TODO remove and use isPayFeeInBtc instead
    public boolean getPayFeeInBtc() {
        return prefPayload.isPayFeeInBtc();
    }

    @Override
    @Nullable
    public List<String> getBridgeAddresses() {
        return prefPayload.getBridgeAddresses();
    }

    public long getWithdrawalTxFeeInBytes() {
        return Math.max(prefPayload.getWithdrawalTxFeeInBytes(), BisqEnvironment.getBaseCurrencyNetwork().getDefaultMinFeePerByte());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateTradeCurrencies(ListChangeListener.Change<? extends TradeCurrency> change) {
        change.next();
        if (change.wasAdded() && change.getAddedSize() == 1 && initialReadDone)
            tradeCurrenciesAsObservable.add(change.getAddedSubList().get(0));
        else if (change.wasRemoved() && change.getRemovedSize() == 1 && initialReadDone)
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

        void setBridgeOptionOrdinal(int bridgeOptionOrdinal);

        void setTorTransportOrdinal(int torTransportOrdinal);

        void setCustomBridges(String customBridges);

        void setBitcoinNodesOptionOrdinal(int bitcoinNodesOption);

        void setReferralId(String referralId);

        void setPhoneKeyAndToken(String phoneKeyAndToken);

        void setUseSoundForMobileNotifications(boolean value);

        void setUseTradeNotifications(boolean value);

        void setUseMarketNotifications(boolean value);

        void setUsePriceNotifications(boolean value);

        List<String> getBridgeAddresses();

        long getWithdrawalTxFeeInBytes();

        void setUseStandbyMode(boolean useStandbyMode);

        void setDaoFullNode(boolean value);

        void setRpcUser(String value);

        void setRpcPw(String value);

        void setTakeOfferSelectedPaymentAccountId(String value);
    }
}
