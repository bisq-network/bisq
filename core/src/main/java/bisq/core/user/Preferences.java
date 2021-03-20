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

import bisq.core.btc.nodes.BtcNodes;
import bisq.core.btc.nodes.LocalBitcoinNode;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountUtil;
import bisq.core.provider.fee.FeeService;
import bisq.core.setup.CoreNetworkCapabilities;

import bisq.network.p2p.network.BridgeAddressProvider;

import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;

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
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Singleton
public final class Preferences implements PersistedDataHost, BridgeAddressProvider {

    private static final ArrayList<BlockChainExplorer> BTC_MAIN_NET_EXPLORERS = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("mempool.space (@wiz)", "https://mempool.space/tx/", "https://mempool.space/address/"),
            new BlockChainExplorer("mempool.space Tor V3", "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion/tx/", "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion/address/"),
            new BlockChainExplorer("mempool.emzy.de (@emzy)", "https://mempool.emzy.de/tx/", "https://mempool.emzy.de/address/"),
            new BlockChainExplorer("mempool.emzy.de Tor V3", "http://mempool4t6mypeemozyterviq3i5de4kpoua65r3qkn5i3kknu5l2cad.onion/tx/", "http://mempool4t6mypeemozyterviq3i5de4kpoua65r3qkn5i3kknu5l2cad.onion/address/"),
            new BlockChainExplorer("mempool.bisq.services (@devinbileck)", "https://mempool.bisq.services/tx/", "https://mempool.bisq.services/address/"),
            new BlockChainExplorer("mempool.bisq.services Tor V3", "http://mempoolusb2f67qi7mz2it7n5e77a6komdzx6wftobcduxszkdfun2yd.onion/tx/", "http://mempoolusb2f67qi7mz2it7n5e77a6komdzx6wftobcduxszkdfun2yd.onion/address/"),
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
            new BlockChainExplorer("Insight", "https://insight.bitpay.com/tx/", "https://insight.bitpay.com/address/"),
            new BlockChainExplorer("Blockchair", "https://blockchair.com/bitcoin/transaction/", "https://blockchair.com/bitcoin/address/")
    ));
    private static final ArrayList<BlockChainExplorer> BTC_TEST_NET_EXPLORERS = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("Blockstream.info", "https://blockstream.info/testnet/tx/", "https://blockstream.info/testnet/address/"),
            new BlockChainExplorer("Blockstream.info Tor V3", "http://explorerzydxu5ecjrkwceayqybizmpjjznk5izmitf2modhcusuqlid.onion/testnet/tx/", "http://explorerzydxu5ecjrkwceayqybizmpjjznk5izmitf2modhcusuqlid.onion/testnet/address/"),
            new BlockChainExplorer("Blockcypher", "https://live.blockcypher.com/btc-testnet/tx/", "https://live.blockcypher.com/btc-testnet/address/"),
            new BlockChainExplorer("Blocktrail", "https://www.blocktrail.com/tBTC/tx/", "https://www.blocktrail.com/tBTC/address/"),
            new BlockChainExplorer("Biteasy", "https://www.biteasy.com/testnet/transactions/", "https://www.biteasy.com/testnet/addresses/"),
            new BlockChainExplorer("Smartbit", "https://testnet.smartbit.com.au/tx/", "https://testnet.smartbit.com.au/address/"),
            new BlockChainExplorer("SoChain. Wow.", "https://chain.so/tx/BTCTEST/", "https://chain.so/address/BTCTEST/"),
            new BlockChainExplorer("Blockchair", "https://blockchair.com/bitcoin/testnet/transaction/", "https://blockchair.com/bitcoin/testnet/address/")
    ));
    private static final ArrayList<BlockChainExplorer> BTC_DAO_TEST_NET_EXPLORERS = new ArrayList<>(Collections.singletonList(
            new BlockChainExplorer("BTC DAO-testnet explorer", "https://bisq.network/explorer/btc/dao_testnet/tx/", "https://bisq.network/explorer/btc/dao_testnet/address/")
    ));

    public static final ArrayList<BlockChainExplorer> BSQ_MAIN_NET_EXPLORERS = new ArrayList<>(Arrays.asList(
            new BlockChainExplorer("mempool.space (@wiz)", "https://mempool.space/bisq/tx/", "https://mempool.space/bisq/address/"),
            new BlockChainExplorer("mempool.emzy.de (@emzy)", "https://mempool.emzy.de/bisq/tx/", "https://mempool.emzy.de/bisq/address/"),
            new BlockChainExplorer("mempool.bisq.services (@devinbileck)", "https://mempool.bisq.services/bisq/tx/", "https://mempool.bisq.services/bisq/address/")
    ));

    private static final ArrayList<String> XMR_TX_PROOF_SERVICES_CLEAR_NET = new ArrayList<>(Arrays.asList(
            "xmrblocks.monero.emzy.de", // @emzy
            //"explorer.monero.wiz.biz", // @wiz
            "xmrblocks.bisq.services" // @devinbileck
    ));
    private static final ArrayList<String> XMR_TX_PROOF_SERVICES = new ArrayList<>(Arrays.asList(
            "monero3bec7m26vx6si6qo7q7imlaoz45ot5m2b5z2ppgoooo6jx2rqd.onion", // @emzy
            //"wizxmr4hbdxdszqm5rfyqvceyca5jq62ppvtuznasnk66wvhhvgm3uyd.onion", // @wiz
            "devinxmrwu4jrfq2zmq5kqjpxb44hx7i7didebkwrtvmvygj4uuop2ad.onion" // @devinbileck
    ));


    private static final ArrayList<String> TX_BROADCAST_SERVICES_CLEAR_NET = new ArrayList<>(Arrays.asList(
            "https://mempool.space/api/tx",         // @wiz
            "https://mempool.emzy.de/api/tx",       // @emzy
            "https://mempool.bisq.services/api/tx"  // @devinbileck
    ));

    private static final ArrayList<String> TX_BROADCAST_SERVICES = new ArrayList<>(Arrays.asList(
            "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion/api/tx",     // @wiz
            "http://mempool4t6mypeemozyterviq3i5de4kpoua65r3qkn5i3kknu5l2cad.onion/api/tx",     // @emzy
            "http://mempoolusb2f67qi7mz2it7n5e77a6komdzx6wftobcduxszkdfun2yd.onion/api/tx"      // @devinbileck
    ));

    public static final boolean USE_SYMMETRIC_SECURITY_DEPOSIT = true;


    // payload is initialized so the default values are available for Property initialization.
    @Setter
    @Delegate(excludes = ExcludesDelegateMethods.class)
    private PreferencesPayload prefPayload = new PreferencesPayload();
    private boolean initialReadDone = false;

    @Getter
    private final BooleanProperty useAnimationsProperty = new SimpleBooleanProperty(prefPayload.isUseAnimations());
    @Getter
    private final IntegerProperty cssThemeProperty = new SimpleIntegerProperty(prefPayload.getCssTheme());

    private final ObservableList<FiatCurrency> fiatCurrenciesAsObservable = FXCollections.observableArrayList();
    private final ObservableList<CryptoCurrency> cryptoCurrenciesAsObservable = FXCollections.observableArrayList();
    private final ObservableList<TradeCurrency> tradeCurrenciesAsObservable = FXCollections.observableArrayList();
    private final ObservableMap<String, Boolean> dontShowAgainMapAsObservable = FXCollections.observableHashMap();

    private final PersistenceManager<PreferencesPayload> persistenceManager;
    private final Config config;
    private final FeeService feeService;
    private final LocalBitcoinNode localBitcoinNode;
    private final String btcNodesFromOptions, referralIdFromOptions,
            rpcUserFromOptions, rpcPwFromOptions;
    private final int blockNotifyPortFromOptions;
    private final boolean fullDaoNodeFromOptions;
    @Getter
    private final BooleanProperty useStandbyModeProperty = new SimpleBooleanProperty(prefPayload.isUseStandbyMode());


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Preferences(PersistenceManager<PreferencesPayload> persistenceManager,
                       Config config,
                       FeeService feeService,
                       LocalBitcoinNode localBitcoinNode,
                       @Named(Config.BTC_NODES) String btcNodesFromOptions,
                       @Named(Config.REFERRAL_ID) String referralId,
                       @Named(Config.FULL_DAO_NODE) boolean fullDaoNode,
                       @Named(Config.RPC_USER) String rpcUser,
                       @Named(Config.RPC_PASSWORD) String rpcPassword,
                       @Named(Config.RPC_BLOCK_NOTIFICATION_PORT) int rpcBlockNotificationPort) {

        this.persistenceManager = persistenceManager;
        this.config = config;
        this.feeService = feeService;
        this.localBitcoinNode = localBitcoinNode;
        this.btcNodesFromOptions = btcNodesFromOptions;
        this.referralIdFromOptions = referralId;
        this.fullDaoNodeFromOptions = fullDaoNode;
        this.rpcUserFromOptions = rpcUser;
        this.rpcPwFromOptions = rpcPassword;
        this.blockNotifyPortFromOptions = rpcBlockNotificationPort;

        useAnimationsProperty.addListener((ov) -> {
            prefPayload.setUseAnimations(useAnimationsProperty.get());
            GlobalSettings.setUseAnimations(prefPayload.isUseAnimations());
            requestPersistence();
        });

        cssThemeProperty.addListener((ov) -> {
            prefPayload.setCssTheme(cssThemeProperty.get());
            requestPersistence();
        });

        useStandbyModeProperty.addListener((ov) -> {
            prefPayload.setUseStandbyMode(useStandbyModeProperty.get());
            requestPersistence();
        });

        fiatCurrenciesAsObservable.addListener((javafx.beans.Observable ov) -> {
            prefPayload.getFiatCurrencies().clear();
            prefPayload.getFiatCurrencies().addAll(fiatCurrenciesAsObservable);
            prefPayload.getFiatCurrencies().sort(TradeCurrency::compareTo);
            requestPersistence();
        });
        cryptoCurrenciesAsObservable.addListener((javafx.beans.Observable ov) -> {
            prefPayload.getCryptoCurrencies().clear();
            prefPayload.getCryptoCurrencies().addAll(cryptoCurrenciesAsObservable);
            prefPayload.getCryptoCurrencies().sort(TradeCurrency::compareTo);
            requestPersistence();
        });

        fiatCurrenciesAsObservable.addListener(this::updateTradeCurrencies);
        cryptoCurrenciesAsObservable.addListener(this::updateTradeCurrencies);
    }

    @Override
    public void readPersisted(Runnable completeHandler) {
        persistenceManager.readPersisted("PreferencesPayload",
                persisted -> {
                    initFromPersistedPreferences(persisted);
                    completeHandler.run();
                },
                () -> {
                    initNewPreferences();
                    completeHandler.run();
                });
    }

    private void initFromPersistedPreferences(PreferencesPayload persisted) {
        prefPayload = persisted;
        GlobalSettings.setLocale(new Locale(prefPayload.getUserLanguage(), prefPayload.getUserCountry().code));
        GlobalSettings.setUseAnimations(prefPayload.isUseAnimations());
        TradeCurrency preferredTradeCurrency = checkNotNull(prefPayload.getPreferredTradeCurrency(), "preferredTradeCurrency must not be null");
        setPreferredTradeCurrency(preferredTradeCurrency);
        setFiatCurrencies(prefPayload.getFiatCurrencies());
        setCryptoCurrencies(prefPayload.getCryptoCurrencies());
        setBsqBlockChainExplorer(prefPayload.getBsqBlockChainExplorer());
        GlobalSettings.setDefaultTradeCurrency(preferredTradeCurrency);

        // If a user has updated and the field was not set and get set to 0 by protobuf
        // As there is no way to detect that a primitive value field was set we cannot apply
        // a "marker" value like -1 to it. We also do not want to wrap the value in a new
        // proto message as thats too much for that feature... So we accept that if the user
        // sets the value to 0 it will be overwritten by the default at next startup.
        if (prefPayload.getBsqAverageTrimThreshold() == 0) {
            prefPayload.setBsqAverageTrimThreshold(0.05);
        }

        setupPreferences();
    }

    private void initNewPreferences() {
        prefPayload = new PreferencesPayload();
        prefPayload.setUserLanguage(GlobalSettings.getLocale().getLanguage());
        prefPayload.setUserCountry(CountryUtil.getDefaultCountry());
        GlobalSettings.setLocale(new Locale(prefPayload.getUserLanguage(), prefPayload.getUserCountry().code));
        TradeCurrency preferredTradeCurrency = checkNotNull(CurrencyUtil.getCurrencyByCountryCode(prefPayload.getUserCountry().code),
                "preferredTradeCurrency must not be null");
        prefPayload.setPreferredTradeCurrency(preferredTradeCurrency);
        setFiatCurrencies(CurrencyUtil.getMainFiatCurrencies());
        setCryptoCurrencies(CurrencyUtil.getMainCryptoCurrencies());

        BaseCurrencyNetwork baseCurrencyNetwork = Config.baseCurrencyNetwork();
        if ("BTC".equals(baseCurrencyNetwork.getCurrencyCode())) {
            setBlockChainExplorerMainNet(BTC_MAIN_NET_EXPLORERS.get(0));
            setBlockChainExplorerTestNet(BTC_TEST_NET_EXPLORERS.get(0));
        } else {
            throw new RuntimeException("BaseCurrencyNetwork not defined. BaseCurrencyNetwork=" + baseCurrencyNetwork);
        }

        prefPayload.setDirectoryChooserPath(Utilities.getSystemHomeDirectory());

        prefPayload.setOfferBookChartScreenCurrencyCode(preferredTradeCurrency.getCode());
        prefPayload.setTradeChartsScreenCurrencyCode(preferredTradeCurrency.getCode());
        prefPayload.setBuyScreenCurrencyCode(preferredTradeCurrency.getCode());
        prefPayload.setSellScreenCurrencyCode(preferredTradeCurrency.getCode());
        GlobalSettings.setDefaultTradeCurrency(preferredTradeCurrency);
        setupPreferences();
    }

    private void setupPreferences() {
        persistenceManager.initialize(prefPayload, PersistenceManager.Source.PRIVATE);

        // We don't want to pass Preferences to all popups where the don't show again checkbox is used, so we use
        // that static lookup class to avoid static access to the Preferences directly.
        DontShowAgainLookup.setPreferences(this);

        // set all properties
        useAnimationsProperty.set(prefPayload.isUseAnimations());
        useStandbyModeProperty.set(prefPayload.isUseStandbyMode());
        cssThemeProperty.set(prefPayload.getCssTheme());

        // a list of previously-used federated explorers
        // if user preference references any deprecated explorers we need to select a new valid explorer
        String deprecatedExplorers = "(bsq.bisq.cc|bsq.vante.me|bsq.emzy.de|bsq.sqrrm.net|bsq.bisq.services|bsq.ninja).*";

        // if no valid Bitcoin block explorer is set, select the 1st valid Bitcoin block explorer
        ArrayList<BlockChainExplorer> btcExplorers = getBlockChainExplorers();
        if (getBlockChainExplorer() == null ||
                getBlockChainExplorer().name.length() == 0 ||
                getBlockChainExplorer().name.matches(deprecatedExplorers)) {
            setBlockChainExplorer(btcExplorers.get(0));
        }

        // if no valid BSQ block explorer is set, randomly select a valid BSQ block explorer
        ArrayList<BlockChainExplorer> bsqExplorers = getBsqBlockChainExplorers();
        if (getBsqBlockChainExplorer() == null ||
                getBsqBlockChainExplorer().name.length() == 0 ||
                getBsqBlockChainExplorer().name.matches(deprecatedExplorers)) {
            setBsqBlockChainExplorer(bsqExplorers.get((new Random()).nextInt(bsqExplorers.size())));
        }

        tradeCurrenciesAsObservable.addAll(prefPayload.getFiatCurrencies());
        tradeCurrenciesAsObservable.addAll(prefPayload.getCryptoCurrencies());
        dontShowAgainMapAsObservable.putAll(getDontShowAgainMap());

        // Override settings with options if set
        if (config.useTorForBtcOptionSetExplicitly)
            setUseTorForBitcoinJ(config.useTorForBtc);

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

        if (prefPayload.getIgnoreDustThreshold() < Restrictions.getMinNonDustOutput().value) {
            setIgnoreDustThreshold(600);
        }

        // For users from old versions the 4 flags a false but we want to have it true by default
        // PhoneKeyAndToken is also null so we can use that to enable the flags
        if (prefPayload.getPhoneKeyAndToken() == null) {
            setUseSoundForMobileNotifications(true);
            setUseTradeNotifications(true);
            setUseMarketNotifications(true);
            setUsePriceNotifications(true);
        }

        if (prefPayload.getAutoConfirmSettingsList().isEmpty()) {
            List<String> defaultXmrTxProofServices = getDefaultXmrTxProofServices();
            AutoConfirmSettings.getDefault(defaultXmrTxProofServices, "XMR")
                    .ifPresent(xmrAutoConfirmSettings -> {
                        getAutoConfirmSettingsList().add(xmrAutoConfirmSettings);
                    });
        }

        // We set the capability in CoreNetworkCapabilities if the program argument is set.
        // If we have set it in the preferences view we handle it here.
        CoreNetworkCapabilities.maybeApplyDaoFullMode(config);

        initialReadDone = true;
        requestPersistence();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void dontShowAgain(String key, boolean dontShowAgain) {
        prefPayload.getDontShowAgainMap().put(key, dontShowAgain);
        requestPersistence();
        dontShowAgainMapAsObservable.put(key, dontShowAgain);
    }

    public void resetDontShowAgain() {
        prefPayload.getDontShowAgainMap().clear();
        dontShowAgainMapAsObservable.clear();
        requestPersistence();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setUseAnimations(boolean useAnimations) {
        this.useAnimationsProperty.set(useAnimations);
    }

    public void setCssTheme(boolean useDarkMode) {
        this.cssThemeProperty.set(useDarkMode ? 1 : 0);
    }

    public void addFiatCurrency(FiatCurrency tradeCurrency) {
        if (!fiatCurrenciesAsObservable.contains(tradeCurrency))
            fiatCurrenciesAsObservable.add(tradeCurrency);
    }

    public void removeFiatCurrency(FiatCurrency tradeCurrency) {
        if (tradeCurrenciesAsObservable.size() > 1) {
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
            cryptoCurrenciesAsObservable.remove(tradeCurrency);

            if (prefPayload.getPreferredTradeCurrency() != null &&
                    prefPayload.getPreferredTradeCurrency().equals(tradeCurrency))
                setPreferredTradeCurrency(tradeCurrenciesAsObservable.get(0));
        } else {
            log.error("you cannot remove the last currency");
        }
    }

    public void setBlockChainExplorer(BlockChainExplorer blockChainExplorer) {
        if (Config.baseCurrencyNetwork().isMainnet())
            setBlockChainExplorerMainNet(blockChainExplorer);
        else
            setBlockChainExplorerTestNet(blockChainExplorer);
    }

    public void setTacAccepted(boolean tacAccepted) {
        prefPayload.setTacAccepted(tacAccepted);
        requestPersistence();
    }

    public void setTacAcceptedV120(boolean tacAccepted) {
        prefPayload.setTacAcceptedV120(tacAccepted);
        requestPersistence();
    }

    public void setBsqAverageTrimThreshold(double bsqAverageTrimThreshold) {
        prefPayload.setBsqAverageTrimThreshold(bsqAverageTrimThreshold);
        requestPersistence();
    }

    public Optional<AutoConfirmSettings> findAutoConfirmSettings(String currencyCode) {
        return prefPayload.getAutoConfirmSettingsList().stream()
                .filter(e -> e.getCurrencyCode().equals(currencyCode))
                .findAny();
    }

    public void setAutoConfServiceAddresses(String currencyCode, List<String> serviceAddresses) {
        findAutoConfirmSettings(currencyCode).ifPresent(e -> {
            e.setServiceAddresses(serviceAddresses);
            requestPersistence();
        });
    }

    public void setAutoConfEnabled(String currencyCode, boolean enabled) {
        findAutoConfirmSettings(currencyCode).ifPresent(e -> {
            e.setEnabled(enabled);
            requestPersistence();
        });
    }

    public void setAutoConfRequiredConfirmations(String currencyCode, int requiredConfirmations) {
        findAutoConfirmSettings(currencyCode).ifPresent(e -> {
            e.setRequiredConfirmations(requiredConfirmations);
            requestPersistence();
        });
    }

    public void setAutoConfTradeLimit(String currencyCode, long tradeLimit) {
        findAutoConfirmSettings(currencyCode).ifPresent(e -> {
            e.setTradeLimit(tradeLimit);
            requestPersistence();
        });
    }

    public void setHideNonAccountPaymentMethods(boolean hideNonAccountPaymentMethods) {
        prefPayload.setHideNonAccountPaymentMethods(hideNonAccountPaymentMethods);
        requestPersistence();
    }

    private void requestPersistence() {
        if (initialReadDone)
            persistenceManager.requestPersistence();
    }

    public void setUserLanguage(@NotNull String userLanguageCode) {
        prefPayload.setUserLanguage(userLanguageCode);
        if (prefPayload.getUserCountry() != null && prefPayload.getUserLanguage() != null)
            GlobalSettings.setLocale(new Locale(prefPayload.getUserLanguage(), prefPayload.getUserCountry().code));
        requestPersistence();
    }

    public void setUserCountry(@NotNull Country userCountry) {
        prefPayload.setUserCountry(userCountry);
        if (prefPayload.getUserLanguage() != null)
            GlobalSettings.setLocale(new Locale(prefPayload.getUserLanguage(), userCountry.code));
        requestPersistence();
    }

    public void setPreferredTradeCurrency(TradeCurrency preferredTradeCurrency) {
        if (preferredTradeCurrency != null) {
            prefPayload.setPreferredTradeCurrency(preferredTradeCurrency);
            GlobalSettings.setDefaultTradeCurrency(preferredTradeCurrency);
            requestPersistence();
        }
    }

    public void setUseTorForBitcoinJ(boolean useTorForBitcoinJ) {
        prefPayload.setUseTorForBitcoinJ(useTorForBitcoinJ);
        requestPersistence();
    }

    public void setShowOwnOffersInOfferBook(boolean showOwnOffersInOfferBook) {
        prefPayload.setShowOwnOffersInOfferBook(showOwnOffersInOfferBook);
        requestPersistence();
    }

    public void setMaxPriceDistanceInPercent(double maxPriceDistanceInPercent) {
        prefPayload.setMaxPriceDistanceInPercent(maxPriceDistanceInPercent);
        requestPersistence();
    }

    public void setBackupDirectory(String backupDirectory) {
        prefPayload.setBackupDirectory(backupDirectory);
        requestPersistence();
    }

    public void setAutoSelectArbitrators(boolean autoSelectArbitrators) {
        prefPayload.setAutoSelectArbitrators(autoSelectArbitrators);
        requestPersistence();
    }

    public void setUsePercentageBasedPrice(boolean usePercentageBasedPrice) {
        prefPayload.setUsePercentageBasedPrice(usePercentageBasedPrice);
        requestPersistence();
    }

    public void setTagForPeer(String fullAddress, String tag) {
        prefPayload.getPeerTagMap().put(fullAddress, tag);
        requestPersistence();
    }

    public void setOfferBookChartScreenCurrencyCode(String offerBookChartScreenCurrencyCode) {
        prefPayload.setOfferBookChartScreenCurrencyCode(offerBookChartScreenCurrencyCode);
        requestPersistence();
    }

    public void setBuyScreenCurrencyCode(String buyScreenCurrencyCode) {
        prefPayload.setBuyScreenCurrencyCode(buyScreenCurrencyCode);
        requestPersistence();
    }

    public void setSellScreenCurrencyCode(String sellScreenCurrencyCode) {
        prefPayload.setSellScreenCurrencyCode(sellScreenCurrencyCode);
        requestPersistence();
    }

    public void setIgnoreTradersList(List<String> ignoreTradersList) {
        prefPayload.setIgnoreTradersList(ignoreTradersList);
        requestPersistence();
    }

    public void setDirectoryChooserPath(String directoryChooserPath) {
        prefPayload.setDirectoryChooserPath(directoryChooserPath);
        requestPersistence();
    }

    public void setTradeChartsScreenCurrencyCode(String tradeChartsScreenCurrencyCode) {
        prefPayload.setTradeChartsScreenCurrencyCode(tradeChartsScreenCurrencyCode);
        requestPersistence();
    }

    public void setTradeStatisticsTickUnitIndex(int tradeStatisticsTickUnitIndex) {
        prefPayload.setTradeStatisticsTickUnitIndex(tradeStatisticsTickUnitIndex);
        requestPersistence();
    }

    public void setSortMarketCurrenciesNumerically(boolean sortMarketCurrenciesNumerically) {
        prefPayload.setSortMarketCurrenciesNumerically(sortMarketCurrenciesNumerically);
        requestPersistence();
    }

    public void setBitcoinNodes(String bitcoinNodes) {
        prefPayload.setBitcoinNodes(bitcoinNodes);
        requestPersistence();
    }

    public void setUseCustomWithdrawalTxFee(boolean useCustomWithdrawalTxFee) {
        prefPayload.setUseCustomWithdrawalTxFee(useCustomWithdrawalTxFee);
        requestPersistence();
    }

    public void setWithdrawalTxFeeInVbytes(long withdrawalTxFeeInVbytes) {
        prefPayload.setWithdrawalTxFeeInVbytes(withdrawalTxFeeInVbytes);
        requestPersistence();
    }

    public void setBuyerSecurityDepositAsPercent(double buyerSecurityDepositAsPercent, PaymentAccount paymentAccount) {
        double max = Restrictions.getMaxBuyerSecurityDepositAsPercent();
        double min = Restrictions.getMinBuyerSecurityDepositAsPercent();

        if (PaymentAccountUtil.isCryptoCurrencyAccount(paymentAccount))
            prefPayload.setBuyerSecurityDepositAsPercentForCrypto(Math.min(max, Math.max(min, buyerSecurityDepositAsPercent)));
        else
            prefPayload.setBuyerSecurityDepositAsPercent(Math.min(max, Math.max(min, buyerSecurityDepositAsPercent)));
        requestPersistence();
    }

    public void setSelectedPaymentAccountForCreateOffer(@Nullable PaymentAccount paymentAccount) {
        prefPayload.setSelectedPaymentAccountForCreateOffer(paymentAccount);
        requestPersistence();
    }

    public void setPayFeeInBtc(boolean payFeeInBtc) {
        prefPayload.setPayFeeInBtc(payFeeInBtc);
        requestPersistence();
    }

    private void setFiatCurrencies(List<FiatCurrency> currencies) {
        fiatCurrenciesAsObservable.setAll(currencies.stream()
                .map(fiatCurrency -> new FiatCurrency(fiatCurrency.getCurrency()))
                .distinct().collect(Collectors.toList()));
        requestPersistence();
    }

    private void setCryptoCurrencies(List<CryptoCurrency> currencies) {
        cryptoCurrenciesAsObservable.setAll(currencies.stream().distinct().collect(Collectors.toList()));
        requestPersistence();
    }

    public void setBsqBlockChainExplorer(BlockChainExplorer bsqBlockChainExplorer) {
        prefPayload.setBsqBlockChainExplorer(bsqBlockChainExplorer);
        requestPersistence();
    }

    private void setBlockChainExplorerTestNet(BlockChainExplorer blockChainExplorerTestNet) {
        prefPayload.setBlockChainExplorerTestNet(blockChainExplorerTestNet);
        requestPersistence();
    }

    private void setBlockChainExplorerMainNet(BlockChainExplorer blockChainExplorerMainNet) {
        prefPayload.setBlockChainExplorerMainNet(blockChainExplorerMainNet);
        requestPersistence();
    }

    public void setResyncSpvRequested(boolean resyncSpvRequested) {
        prefPayload.setResyncSpvRequested(resyncSpvRequested);
        // We call that before shutdown so we dont want a delay here
        requestPersistence();
    }

    public void setBridgeAddresses(List<String> bridgeAddresses) {
        prefPayload.setBridgeAddresses(bridgeAddresses);
        // We call that before shutdown so we dont want a delay here
        requestPersistence();
    }

    // Only used from PB but keep it explicit as it may be used from the client and then we want to persist
    public void setPeerTagMap(Map<String, String> peerTagMap) {
        prefPayload.setPeerTagMap(peerTagMap);
        requestPersistence();
    }

    public void setBridgeOptionOrdinal(int bridgeOptionOrdinal) {
        prefPayload.setBridgeOptionOrdinal(bridgeOptionOrdinal);
        requestPersistence();
    }

    public void setTorTransportOrdinal(int torTransportOrdinal) {
        prefPayload.setTorTransportOrdinal(torTransportOrdinal);
        requestPersistence();
    }

    public void setCustomBridges(String customBridges) {
        prefPayload.setCustomBridges(customBridges);
        requestPersistence();
    }

    public void setBitcoinNodesOptionOrdinal(int bitcoinNodesOptionOrdinal) {
        prefPayload.setBitcoinNodesOptionOrdinal(bitcoinNodesOptionOrdinal);
        requestPersistence();
    }

    public void setReferralId(String referralId) {
        prefPayload.setReferralId(referralId);
        requestPersistence();
    }

    public void setPhoneKeyAndToken(String phoneKeyAndToken) {
        prefPayload.setPhoneKeyAndToken(phoneKeyAndToken);
        requestPersistence();
    }

    public void setUseSoundForMobileNotifications(boolean value) {
        prefPayload.setUseSoundForMobileNotifications(value);
        requestPersistence();
    }

    public void setUseTradeNotifications(boolean value) {
        prefPayload.setUseTradeNotifications(value);
        requestPersistence();
    }

    public void setUseMarketNotifications(boolean value) {
        prefPayload.setUseMarketNotifications(value);
        requestPersistence();
    }

    public void setUsePriceNotifications(boolean value) {
        prefPayload.setUsePriceNotifications(value);
        requestPersistence();
    }

    public void setUseStandbyMode(boolean useStandbyMode) {
        this.useStandbyModeProperty.set(useStandbyMode);
    }

    public void setTakeOfferSelectedPaymentAccountId(String value) {
        prefPayload.setTakeOfferSelectedPaymentAccountId(value);
        requestPersistence();
    }

    public void setDaoFullNode(boolean value) {
        // We only persist if we have not set the program argument
        if (config.fullDaoNodeOptionSetExplicitly) {
            prefPayload.setDaoFullNode(value);
            requestPersistence();
        }
    }

    public void setRpcUser(String value) {
        // We only persist if we have not set the program argument
        if (!rpcUserFromOptions.isEmpty()) {
            prefPayload.setRpcUser(value);
        }
        prefPayload.setRpcUser(value);
        requestPersistence();
    }

    public void setRpcPw(String value) {
        // We only persist if we have not set the program argument
        if (rpcPwFromOptions.isEmpty()) {
            prefPayload.setRpcPw(value);
            requestPersistence();
        }
    }

    public void setBlockNotifyPort(int value) {
        // We only persist if we have not set the program argument
        if (blockNotifyPortFromOptions == Config.UNSPECIFIED_PORT) {
            prefPayload.setBlockNotifyPort(value);
            requestPersistence();
        }
    }

    public void setIgnoreDustThreshold(int value) {
        prefPayload.setIgnoreDustThreshold(value);
        requestPersistence();
    }

    public void setShowOffersMatchingMyAccounts(boolean value) {
        prefPayload.setShowOffersMatchingMyAccounts(value);
        requestPersistence();
    }

    public void setDenyApiTaker(boolean value) {
        prefPayload.setDenyApiTaker(value);
        requestPersistence();
    }

    public void setNotifyOnPreRelease(boolean value) {
        prefPayload.setNotifyOnPreRelease(value);
        requestPersistence();
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
        BaseCurrencyNetwork baseCurrencyNetwork = Config.baseCurrencyNetwork();
        switch (baseCurrencyNetwork) {
            case BTC_MAINNET:
                return prefPayload.getBlockChainExplorerMainNet();
            case BTC_TESTNET:
            case BTC_REGTEST:
                return prefPayload.getBlockChainExplorerTestNet();
            case BTC_DAO_TESTNET:
                return BTC_DAO_TEST_NET_EXPLORERS.get(0);
            case BTC_DAO_BETANET:
                return prefPayload.getBlockChainExplorerMainNet();
            case BTC_DAO_REGTEST:
                return BTC_DAO_TEST_NET_EXPLORERS.get(0);
            default:
                throw new RuntimeException("BaseCurrencyNetwork not defined. BaseCurrencyNetwork=" + baseCurrencyNetwork);
        }
    }

    public ArrayList<BlockChainExplorer> getBlockChainExplorers() {
        BaseCurrencyNetwork baseCurrencyNetwork = Config.baseCurrencyNetwork();
        switch (baseCurrencyNetwork) {
            case BTC_MAINNET:
                return BTC_MAIN_NET_EXPLORERS;
            case BTC_TESTNET:
            case BTC_REGTEST:
                return BTC_TEST_NET_EXPLORERS;
            case BTC_DAO_TESTNET:
                return BTC_DAO_TEST_NET_EXPLORERS;
            case BTC_DAO_BETANET:
                return BTC_MAIN_NET_EXPLORERS;
            case BTC_DAO_REGTEST:
                return BTC_DAO_TEST_NET_EXPLORERS;
            default:
                throw new RuntimeException("BaseCurrencyNetwork not defined. BaseCurrencyNetwork=" + baseCurrencyNetwork);
        }
    }

    public ArrayList<BlockChainExplorer> getBsqBlockChainExplorers() {
        return BSQ_MAIN_NET_EXPLORERS;
    }

    public boolean showAgain(String key) {
        return !prefPayload.getDontShowAgainMap().containsKey(key) || !prefPayload.getDontShowAgainMap().get(key);
    }

    public boolean getUseTorForBitcoinJ() {
        // We override the useTorForBitcoinJ and set it to false if we will use a
        // localhost Bitcoin node or if we are not on mainnet, unless the useTorForBtc
        // parameter is explicitly provided. On testnet there are very few Bitcoin tor
        // nodes and we don't provide tor nodes.

        if ((!Config.baseCurrencyNetwork().isMainnet()
                || localBitcoinNode.shouldBeUsed())
                && !config.useTorForBtcOptionSetExplicitly)
            return false;
        else
            return prefPayload.isUseTorForBitcoinJ();
    }

    public double getBuyerSecurityDepositAsPercent(PaymentAccount paymentAccount) {
        double value = PaymentAccountUtil.isCryptoCurrencyAccount(paymentAccount) ?
                prefPayload.getBuyerSecurityDepositAsPercentForCrypto() : prefPayload.getBuyerSecurityDepositAsPercent();

        if (value < Restrictions.getMinBuyerSecurityDepositAsPercent()) {
            value = Restrictions.getMinBuyerSecurityDepositAsPercent();
            setBuyerSecurityDepositAsPercent(value, paymentAccount);
        }

        return value == 0 ? Restrictions.getDefaultBuyerSecurityDepositAsPercent() : value;
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

    public long getWithdrawalTxFeeInVbytes() {
        return Math.max(prefPayload.getWithdrawalTxFeeInVbytes(),
                feeService.getMinFeePerVByte());
    }

    public boolean isDaoFullNode() {
        if (config.fullDaoNodeOptionSetExplicitly) {
            return fullDaoNodeFromOptions;
        } else {
            return prefPayload.isDaoFullNode();
        }
    }

    public String getRpcUser() {
        if (!rpcUserFromOptions.isEmpty()) {
            return rpcUserFromOptions;
        } else {
            return prefPayload.getRpcUser();
        }
    }

    public String getRpcPw() {
        if (!rpcPwFromOptions.isEmpty()) {
            return rpcPwFromOptions;
        } else {
            return prefPayload.getRpcPw();
        }
    }

    public int getBlockNotifyPort() {
        if (blockNotifyPortFromOptions != Config.UNSPECIFIED_PORT) {
            try {
                return blockNotifyPortFromOptions;
            } catch (Throwable ignore) {
                return 0;
            }

        } else {
            return prefPayload.getBlockNotifyPort();
        }
    }

    public List<String> getDefaultXmrTxProofServices() {
        if (config.useLocalhostForP2P) {
            return XMR_TX_PROOF_SERVICES_CLEAR_NET;
        } else {
            return XMR_TX_PROOF_SERVICES;
        }
    }

    public List<String> getDefaultTxBroadcastServices() {
        if (config.useLocalhostForP2P) {
            return TX_BROADCAST_SERVICES_CLEAR_NET;
        } else {
            return TX_BROADCAST_SERVICES;
        }
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

        requestPersistence();
    }

    private interface ExcludesDelegateMethods {
        void setTacAccepted(boolean tacAccepted);

        void setUseAnimations(boolean useAnimations);

        void setCssTheme(int cssTheme);

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

        void setWithdrawalTxFeeInVbytes(long withdrawalTxFeeInVbytes);

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

        long getWithdrawalTxFeeInVbytes();

        void setUseStandbyMode(boolean useStandbyMode);

        void setTakeOfferSelectedPaymentAccountId(String value);

        void setIgnoreDustThreshold(int value);

        void setBuyerSecurityDepositAsPercent(double buyerSecurityDepositAsPercent);

        double getBuyerSecurityDepositAsPercent();

        void setDaoFullNode(boolean value);

        void setRpcUser(String value);

        void setRpcPw(String value);

        void setBlockNotifyPort(int value);

        boolean isDaoFullNode();

        String getRpcUser();

        String getRpcPw();

        int getBlockNotifyPort();

        void setTacAcceptedV120(boolean tacAccepted);

        void setBsqAverageTrimThreshold(double bsqAverageTrimThreshold);

        void setAutoConfirmSettings(AutoConfirmSettings autoConfirmSettings);

        void setHideNonAccountPaymentMethods(boolean hideNonAccountPaymentMethods);

        void setShowOffersMatchingMyAccounts(boolean value);

        void setDenyApiTaker(boolean value);

        void setNotifyOnPreRelease(boolean value);
    }
}
