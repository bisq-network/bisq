package bisq.httpapi;

import bisq.core.app.AppOptionKeys;
import bisq.core.app.BisqEnvironment;
import bisq.core.arbitration.Arbitrator;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.btc.AddressEntry;
import bisq.core.btc.BitcoinNodes;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.TradeCurrency;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.user.BlockChainExplorer;
import bisq.core.user.User;

import bisq.httpapi.exceptions.NotFoundException;
import bisq.httpapi.exceptions.UnauthorizedException;
import bisq.httpapi.exceptions.WalletNotReadyException;
import bisq.httpapi.facade.ShutdownFacade;
import bisq.httpapi.facade.WalletFacade;
import bisq.httpapi.model.AuthResult;
import bisq.httpapi.model.BitcoinNetworkStatus;
import bisq.httpapi.model.ClosedTradableConverter;
import bisq.httpapi.model.ClosedTradableDetails;
import bisq.httpapi.model.P2PNetworkConnection;
import bisq.httpapi.model.P2PNetworkStatus;
import bisq.httpapi.model.Preferences;
import bisq.httpapi.model.PreferencesAvailableValues;
import bisq.httpapi.model.PriceFeed;
import bisq.httpapi.model.VersionDetails;
import bisq.httpapi.service.auth.TokenRegistry;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.Statistic;

import bisq.common.app.DevEnv;
import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Peer;
import org.bitcoinj.crypto.KeyCrypterScrypt;

import javax.inject.Inject;
import javax.inject.Named;

import org.spongycastle.crypto.params.KeyParameter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import static java.util.stream.Collectors.toList;



import javax.validation.ValidationException;

//TODO @bernard we need ot break that apart to smaller domain specific chunks (or then use core domains directly).
// its very hard atm to get an overview here

/**
 * This class is a proxy for all Bisq features the model will use.
 * <p>
 * No methods/representations used in the interface layers (REST/Socket/...) should be used in this class.
 * => this should be the common gateway to bisq used by all outward-facing API classes.
 * <p>
 * If the bisq code is refactored correctly, this class could become very light.
 */
@Slf4j
public class BisqProxy {
    private final ArbitratorManager arbitratorManager;
    private final BtcWalletService btcWalletService;
    private final User user;
    private final ClosedTradableManager closedTradableManager;
    private final P2PService p2PService;
    private final KeyRing keyRing;
    private final bisq.core.user.Preferences preferences;
    private final WalletsSetup walletsSetup;
    private final ClosedTradableConverter closedTradableConverter;
    private final TokenRegistry tokenRegistry;
    private final WalletsManager walletsManager;
    private final PriceFeedService priceFeedService;
    private final boolean useDevPrivilegeKeys;

    private final BackupManager backupManager;
    private final BackupRestoreManager backupRestoreManager;
    private final ShutdownFacade shutdownFacade;
    private final WalletFacade walletFacade;

    @Inject
    public BisqProxy(ArbitratorManager arbitratorManager,
                     BtcWalletService btcWalletService,
                     User user,
                     ClosedTradableManager closedTradableManager,
                     P2PService p2PService,
                     KeyRing keyRing,
                     bisq.core.user.Preferences preferences,
                     WalletsSetup walletsSetup,
                     ClosedTradableConverter closedTradableConverter,
                     TokenRegistry tokenRegistry,
                     WalletsManager walletsManager,
                     PriceFeedService priceFeedService,
                     BisqEnvironment bisqEnvironment,
                     @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) Boolean useDevPrivilegeKeys,
                     ShutdownFacade shutdownFacade,
                     WalletFacade walletFacade) {
        this.arbitratorManager = arbitratorManager;
        this.btcWalletService = btcWalletService;
        this.user = user;
        this.closedTradableManager = closedTradableManager;
        this.p2PService = p2PService;
        this.keyRing = keyRing;
        this.preferences = preferences;
        this.walletsSetup = walletsSetup;
        this.closedTradableConverter = closedTradableConverter;
        this.tokenRegistry = tokenRegistry;
        this.walletsManager = walletsManager;
        this.priceFeedService = priceFeedService;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
        this.shutdownFacade = shutdownFacade;
        this.walletFacade = walletFacade;

        String appDataDir = bisqEnvironment.getAppDataDir();
        backupManager = new BackupManager(appDataDir);
        backupRestoreManager = new BackupRestoreManager(appDataDir);
    }


    /// START TODO REFACTOR OFFER TAKE DEPENDENCIES //////////////////////////


    /// STOP TODO REFACTOR OFFER TAKE DEPENDENCIES //////////////////////////

    public List<ClosedTradableDetails> getClosedTradableList() {
        return closedTradableManager.getClosedTradables().stream()
                .sorted((o1, o2) -> o2.getDate().compareTo(o1.getDate()))
                .map(closedTradableConverter::convert)
                .collect(toList());
    }

    public void registerArbitrator(List<String> languageCodes) {
//        TODO most of this code is dupplication of ArbitratorRegistrationViewModel.onRegister
        final String privKeyString = useDevPrivilegeKeys ? DevEnv.DEV_PRIVILEGE_PRIV_KEY : null;
        //        TODO hm, are we going to send private key over http?
        if (null == privKeyString) {
            throw new RuntimeException("Missing private key");
        }
        ECKey registrationKey = arbitratorManager.getRegistrationKey(privKeyString);
        if (null == registrationKey) {
            throw new RuntimeException("Missing registration key");
        }
        AddressEntry arbitratorDepositAddressEntry = btcWalletService.getArbitratorAddressEntry();
        String registrationSignature = arbitratorManager.signStorageSignaturePubKey(registrationKey);
        Arbitrator arbitrator = new Arbitrator(
                p2PService.getAddress(),
                arbitratorDepositAddressEntry.getPubKey(),
                arbitratorDepositAddressEntry.getAddressString(),
                keyRing.getPubKeyRing(),
                new ArrayList<>(languageCodes),
                new Date().getTime(),
                registrationKey.getPubKey(),
                registrationSignature,
                null,
                null,
                null
        );
//        TODO I don't know how to deal with those callbacks in order to send response back
        arbitratorManager.addArbitrator(arbitrator, () -> System.out.println("Arbi registered"), message -> System.out.println("Error when registering arbi: " + message));
    }

    public Collection<Arbitrator> getArbitrators(boolean acceptedOnly) {
        if (acceptedOnly) {
            return user.getAcceptedArbitrators();
        }
        return arbitratorManager.getArbitratorsObservableMap().values();
    }

    public Collection<Arbitrator> selectArbitrator(String arbitratorAddress) {
        final Arbitrator arbitrator = getArbitratorByAddress(arbitratorAddress);
        if (null == arbitrator) {
            throw new NotFoundException("Arbitrator not found: " + arbitratorAddress);
        }
        if (!arbitratorIsTrader(arbitrator)) {
            user.addAcceptedArbitrator(arbitrator);
            user.addAcceptedMediator(ArbitratorManager.getMediator(arbitrator));
            return user.getAcceptedArbitrators();
        }
        throw new ValidationException("You cannot select yourself as an arbitrator");
    }

    public Collection<Arbitrator> deselectArbitrator(String arbitratorAddress) {
        final Arbitrator arbitrator = getArbitratorByAddress(arbitratorAddress);
        if (null == arbitrator) {
            throw new NotFoundException("Arbitrator not found: " + arbitratorAddress);
        }
        user.removeAcceptedArbitrator(arbitrator);
        user.removeAcceptedMediator(ArbitratorManager.getMediator(arbitrator));
        return user.getAcceptedArbitrators();
    }

    private Arbitrator getArbitratorByAddress(String arbitratorAddress) {
        return arbitratorManager.getArbitratorsObservableMap().get(new NodeAddress(arbitratorAddress));
    }

    private boolean arbitratorIsTrader(Arbitrator arbitrator) {
        return keyRing.getPubKeyRing().equals(arbitrator.getPubKeyRing());
    }

    public P2PNetworkStatus getP2PNetworkStatus() {
        final P2PNetworkStatus p2PNetworkStatus = new P2PNetworkStatus();
        final NodeAddress address = p2PService.getAddress();
        if (null != address)
            p2PNetworkStatus.address = address.getFullAddress();
        p2PNetworkStatus.p2pNetworkConnection = p2PService.getNetworkNode().getAllConnections().stream()
                .map(P2PNetworkConnection::new)
                .collect(Collectors.toList());
        p2PNetworkStatus.totalSentBytes = Statistic.totalSentBytesProperty().get();
        p2PNetworkStatus.totalReceivedBytes = Statistic.totalReceivedBytesProperty().get();
        return p2PNetworkStatus;
    }

    public BitcoinNetworkStatus getBitcoinNetworkStatus() {
        final BitcoinNetworkStatus networkStatus = new BitcoinNetworkStatus();
        final List<Peer> peers = walletsSetup.connectedPeersProperty().get();
        if (null != peers)
            networkStatus.peers = peers.stream().map(peer -> peer.getAddress().toString()).collect(Collectors.toList());
        else
            networkStatus.peers = Collections.emptyList();
        networkStatus.useTorForBitcoinJ = preferences.getUseTorForBitcoinJ();
        networkStatus.bitcoinNodesOption = BitcoinNodes.BitcoinNodesOption.values()[preferences.getBitcoinNodesOptionOrdinal()];
        networkStatus.bitcoinNodes = preferences.getBitcoinNodes();
        return networkStatus;
    }

    public Preferences getPreferences() {
        final Preferences preferences = new Preferences();
        preferences.autoSelectArbitrators = this.preferences.isAutoSelectArbitrators();
        preferences.baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode();
        preferences.blockChainExplorer = this.preferences.getBlockChainExplorer().name;
        preferences.cryptoCurrencies = tradeCurrenciesToCodes(this.preferences.getCryptoCurrencies());
        preferences.fiatCurrencies = tradeCurrenciesToCodes(this.preferences.getFiatCurrencies());
        preferences.ignoredTraders = this.preferences.getIgnoreTradersList();
        preferences.maxPriceDistance = this.preferences.getMaxPriceDistanceInPercent();
        preferences.preferredTradeCurrency = this.preferences.getPreferredTradeCurrency().getCode();
        preferences.useCustomWithdrawalTxFee = this.preferences.getUseCustomWithdrawalTxFeeProperty().get();
        final Country userCountry = this.preferences.getUserCountry();
        if (null != userCountry)
            preferences.userCountry = userCountry.code;
        preferences.userLanguage = this.preferences.getUserLanguage();
        preferences.withdrawalTxFee = this.preferences.getWithdrawalTxFeeInBytes();
        return preferences;
    }

    public PreferencesAvailableValues getPreferencesAvailableValues() {
        final PreferencesAvailableValues availableValues = new PreferencesAvailableValues();
        availableValues.blockChainExplorers = preferences.getBlockChainExplorers().stream().map(i -> i.name).collect(Collectors.toList());
        availableValues.cryptoCurrencies = tradeCurrenciesToCodes(CurrencyUtil.getAllSortedCryptoCurrencies());
        availableValues.fiatCurrencies = tradeCurrenciesToCodes(CurrencyUtil.getAllSortedFiatCurrencies());
        availableValues.userCountries = CountryUtil.getAllCountries().stream().map(i -> i.code).collect(Collectors.toList());
        return availableValues;
    }

    public Preferences setPreferences(Preferences update) {
        if (null != update.autoSelectArbitrators) {
            preferences.setAutoSelectArbitrators(update.autoSelectArbitrators);
        }
        if (null != update.baseCurrencyNetwork && !update.baseCurrencyNetwork.equals(BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode())) {
            throw new ValidationException("Changing baseCurrencyNetwork is not supported");
        }
        if (null != update.blockChainExplorer) {
            final Optional<BlockChainExplorer> explorerOptional = preferences.getBlockChainExplorers().stream().filter(i -> update.blockChainExplorer.equals(i.name)).findAny();
            if (!explorerOptional.isPresent()) {
                throw new ValidationException("Unsupported value of blockChainExplorer: " + update.blockChainExplorer);
            }
            preferences.setBlockChainExplorer(explorerOptional.get());
        }
        if (null != update.cryptoCurrencies) {
            final List<CryptoCurrency> cryptoCurrencies = preferences.getCryptoCurrencies();
            final Collection<CryptoCurrency> convertedCryptos = codesToCryptoCurrencies(update.cryptoCurrencies);
            cryptoCurrencies.clear();
            cryptoCurrencies.addAll(convertedCryptos);
        }
        if (null != update.fiatCurrencies) {
            final List<FiatCurrency> fiatCurrencies = preferences.getFiatCurrencies();
            final Collection<FiatCurrency> convertedFiat = codesToFiatCurrencies(update.fiatCurrencies);
            fiatCurrencies.clear();
            fiatCurrencies.addAll(convertedFiat);
        }
        if (null != update.ignoredTraders) {
            preferences.setIgnoreTradersList(update.ignoredTraders.stream().map(i -> i.replace(":9999", "").replace(".onion", "")).collect(Collectors.toList()));
        }
        if (null != update.maxPriceDistance) {
            preferences.setMaxPriceDistanceInPercent(update.maxPriceDistance);
        }
        if (null != update.preferredTradeCurrency) {
            preferences.setPreferredTradeCurrency(codeToTradeCurrency(update.preferredTradeCurrency));
        }
        if (null != update.useCustomWithdrawalTxFee) {
            preferences.setUseCustomWithdrawalTxFee(update.useCustomWithdrawalTxFee);
        }
        if (null != update.userCountry) {
            preferences.setUserCountry(codeToCountry(update.userCountry));
        }
        if (null != update.userLanguage) {
            preferences.setUserLanguage(update.userLanguage);
        }
        if (null != update.withdrawalTxFee) {
            preferences.setWithdrawalTxFeeInBytes(update.withdrawalTxFee);
        }
        return getPreferences();
    }

    public VersionDetails getVersionDetails() {
        final VersionDetails versionDetails = new VersionDetails();
        versionDetails.application = Version.VERSION;
        versionDetails.network = Version.P2P_NETWORK_VERSION;
        versionDetails.p2PMessage = Version.getP2PMessageVersion();
        versionDetails.localDB = Version.LOCAL_DB_VERSION;
        versionDetails.tradeProtocol = Version.TRADE_PROTOCOL_VERSION;
        return versionDetails;
    }

    public AuthResult authenticate(String password) {
        final boolean isPasswordValid = btcWalletService.isWalletReady() && btcWalletService.isEncrypted() && walletFacade.isWalletPasswordValid(password);
        if (isPasswordValid) {
            return new AuthResult(tokenRegistry.generateToken());
        }
        throw new UnauthorizedException();
    }

    public AuthResult changePassword(String oldPassword, String newPassword) {
        if (!btcWalletService.isWalletReady())
            throw new WalletNotReadyException("Wallet not ready yet");
        if (btcWalletService.isEncrypted()) {
            final KeyParameter aesKey = null == oldPassword ? null : walletFacade.getAESKey(oldPassword);
            if (!walletFacade.isWalletPasswordValid(aesKey))
                throw new UnauthorizedException();
            walletsManager.decryptWallets(aesKey);
        }
        if (null != newPassword && newPassword.length() > 0) {
            final Tuple2<KeyParameter, KeyCrypterScrypt> aesKeyAndScrypt = walletFacade.getAESKeyAndScrypt(newPassword);
            walletsManager.encryptWallets(aesKeyAndScrypt.second, aesKeyAndScrypt.first);
            tokenRegistry.clear();
            return new AuthResult(tokenRegistry.generateToken());
        }
        return null;
    }

    public PriceFeed getPriceFeed(String[] codes) {
        final List<FiatCurrency> fiatCurrencies = preferences.getFiatCurrencies();
        final List<CryptoCurrency> cryptoCurrencies = preferences.getCryptoCurrencies();
        final Stream<String> codesStream;
        if (null == codes || 0 == codes.length)
            codesStream = Stream.concat(fiatCurrencies.stream(), cryptoCurrencies.stream()).map(TradeCurrency::getCode);
        else
            codesStream = Arrays.asList(codes).stream();
        final List<MarketPrice> marketPrices = codesStream
                .map(priceFeedService::getMarketPrice)
                .filter(i -> null != i)
                .collect(toList());
        final PriceFeed priceFeed = new PriceFeed();
        for (MarketPrice price : marketPrices)
            priceFeed.prices.put(price.getCurrencyCode(), price.getPrice());
        return priceFeed;
    }

    public String createBackup() throws IOException {
        return backupManager.createBackup();
    }

    public FileInputStream getBackup(String fileName) throws FileNotFoundException {
        return backupManager.getBackup(fileName);
    }

    public boolean removeBackup(String fileName) throws FileNotFoundException {
        return backupManager.removeBackup(fileName);
    }

    public List<String> getBackupList() {
        return backupManager.getBackupList();
    }

    public void requestBackupRestore(String fileName) throws IOException {
        backupRestoreManager.requestRestore(fileName);
        if (!shutdownFacade.isShutdownSupported()) {
            log.warn("No shutdown mechanism provided! You have to restart the app manually.");
            return;
        }
        log.info("Backup restore requested. Initiating shutdown.");
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            shutdownFacade.shutDown();
        }, "Shutdown before backup restore").start();
    }

    public void uploadBackup(String fileName, InputStream uploadedInputStream) throws IOException {
        backupManager.saveBackup(fileName, uploadedInputStream);
    }

    @NotNull
    private static Country codeToCountry(String code) {
        final Optional<Country> countryOptional = CountryUtil.findCountryByCode(code);
        if (!countryOptional.isPresent())
            throw new ValidationException("Unsupported country code: " + code);
        return countryOptional.get();
    }

    @NotNull
    private Collection<CryptoCurrency> codesToCryptoCurrencies(List<String> cryptoCurrencies) {
        return cryptoCurrencies.stream().map(code -> {
            final Optional<CryptoCurrency> cryptoCurrency = CurrencyUtil.getCryptoCurrency(code);
            if (!cryptoCurrency.isPresent())
                throw new ValidationException("Unsupported crypto currency code: " + code);
            return cryptoCurrency.get();
        }).collect(Collectors.toList());
    }

    @NotNull
    private Collection<FiatCurrency> codesToFiatCurrencies(List<String> fiatCurrencies) {
        return fiatCurrencies.stream().map(code -> {
            final Optional<FiatCurrency> cryptoCurrency = CurrencyUtil.getFiatCurrency(code);
            if (!cryptoCurrency.isPresent())
                throw new ValidationException("Unsupported fiat currency code: " + code);
            return cryptoCurrency.get();
        }).collect(Collectors.toList());
    }

    @NotNull
    private static TradeCurrency codeToTradeCurrency(String code) {
        final Optional<TradeCurrency> currencyOptional = CurrencyUtil.getTradeCurrency(code);
        if (!currencyOptional.isPresent())
            throw new ValidationException("Unsupported trade currency code: " + code);
        return currencyOptional.get();
    }

    private static List<String> tradeCurrenciesToCodes(Collection<? extends TradeCurrency> tradeCurrencies) {
        return tradeCurrencies.stream().map(TradeCurrency::getCode).collect(Collectors.toList());
    }
}
