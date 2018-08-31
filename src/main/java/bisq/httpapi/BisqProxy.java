package bisq.httpapi;

import bisq.core.app.AppOptionKeys;
import bisq.core.app.BisqEnvironment;
import bisq.core.arbitration.Arbitrator;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.btc.AddressEntry;
import bisq.core.btc.AddressEntryException;
import bisq.core.btc.BalanceUtil;
import bisq.core.btc.BitcoinNodes;
import bisq.core.btc.InsufficientFundsException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.CryptoCurrencyAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.validation.AltCoinAddressValidator;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.user.BlockChainExplorer;
import bisq.core.user.User;
import bisq.core.util.validation.BtcAddressValidator;
import bisq.core.util.validation.InputValidator;

import bisq.httpapi.exceptions.AmountTooLowException;
import bisq.httpapi.exceptions.NotFoundException;
import bisq.httpapi.exceptions.UnauthorizedException;
import bisq.httpapi.exceptions.WalletNotReadyException;
import bisq.httpapi.model.AuthResult;
import bisq.httpapi.model.BitcoinNetworkStatus;
import bisq.httpapi.model.ClosedTradableConverter;
import bisq.httpapi.model.ClosedTradableDetails;
import bisq.httpapi.model.P2PNetworkConnection;
import bisq.httpapi.model.P2PNetworkStatus;
import bisq.httpapi.model.PaymentAccountList;
import bisq.httpapi.model.Preferences;
import bisq.httpapi.model.PreferencesAvailableValues;
import bisq.httpapi.model.PriceFeed;
import bisq.httpapi.model.SeedWords;
import bisq.httpapi.model.VersionDetails;
import bisq.httpapi.model.WalletAddress;
import bisq.httpapi.model.WalletAddressList;
import bisq.httpapi.model.WalletTransaction;
import bisq.httpapi.model.WalletTransactionList;
import bisq.httpapi.model.payment.PaymentAccountHelper;
import bisq.httpapi.service.auth.TokenRegistry;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.Statistic;

import bisq.common.app.DevEnv;
import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.common.storage.FileUtil;
import bisq.common.storage.Storage;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.util.concurrent.FutureCallback;

import org.spongycastle.crypto.params.KeyParameter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import java.io.File;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import static bisq.httpapi.facade.FacadeUtil.failFuture;
import static com.google.common.base.Preconditions.checkNotNull;
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
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final ArbitratorManager arbitratorManager;
    private final BtcWalletService btcWalletService;
    private final User user;
    private final TradeManager tradeManager;
    private final ClosedTradableManager closedTradableManager;
    private final P2PService p2PService;
    private final KeyRing keyRing;
    private final bisq.core.user.Preferences preferences;
    private final BsqWalletService bsqWalletService;
    private final WalletsSetup walletsSetup;
    private final AltCoinAddressValidator altCoinAddressValidator;
    private final ClosedTradableConverter closedTradableConverter;
    private final TokenRegistry tokenRegistry;
    private final WalletsManager walletsManager;
    private final PriceFeedService priceFeedService;
    private final BalanceUtil balanceUtil;
    private final boolean useDevPrivilegeKeys;
    private final File storageDir;

    private final BackupManager backupManager;
    private final BackupRestoreManager backupRestoreManager;

    @Setter
    private Runnable shutdownHandler;

    @Inject
    public BisqProxy(AccountAgeWitnessService accountAgeWitnessService,
                     ArbitratorManager arbitratorManager,
                     BtcWalletService btcWalletService,
                     User user,
                     TradeManager tradeManager,
                     ClosedTradableManager closedTradableManager,
                     P2PService p2PService,
                     KeyRing keyRing,
                     bisq.core.user.Preferences preferences,
                     BsqWalletService bsqWalletService,
                     WalletsSetup walletsSetup,
                     AltCoinAddressValidator altCoinAddressValidator,
                     ClosedTradableConverter closedTradableConverter,
                     TokenRegistry tokenRegistry,
                     WalletsManager walletsManager,
                     PriceFeedService priceFeedService,
                     BalanceUtil balanceUtil,
                     BisqEnvironment bisqEnvironment,
                     @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) Boolean useDevPrivilegeKeys,
                     @Named(Storage.STORAGE_DIR) File storageDir) {
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.arbitratorManager = arbitratorManager;
        this.btcWalletService = btcWalletService;
        this.user = user;
        this.tradeManager = tradeManager;
        this.closedTradableManager = closedTradableManager;
        this.p2PService = p2PService;
        this.keyRing = keyRing;
        this.preferences = preferences;
        this.bsqWalletService = bsqWalletService;
        this.walletsSetup = walletsSetup;
        this.altCoinAddressValidator = altCoinAddressValidator;
        this.closedTradableConverter = closedTradableConverter;
        this.tokenRegistry = tokenRegistry;
        this.walletsManager = walletsManager;
        this.priceFeedService = priceFeedService;
        this.balanceUtil = balanceUtil;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
        this.storageDir = storageDir;

        String appDataDir = bisqEnvironment.getAppDataDir();
        backupManager = new BackupManager(appDataDir);
        backupRestoreManager = new BackupRestoreManager(appDataDir);
    }

    public PaymentAccount addPaymentAccount(PaymentAccount paymentAccount) {
        if (paymentAccount instanceof CryptoCurrencyAccount) {
            final CryptoCurrencyAccount cryptoCurrencyAccount = (CryptoCurrencyAccount) paymentAccount;
            final TradeCurrency tradeCurrency = cryptoCurrencyAccount.getSingleTradeCurrency();
            if (null == tradeCurrency) {
                throw new ValidationException("There must be exactly one trade currency");
            }
            altCoinAddressValidator.setCurrencyCode(tradeCurrency.getCode());
            final InputValidator.ValidationResult validationResult = altCoinAddressValidator.validate(cryptoCurrencyAccount.getAddress());
            if (!validationResult.isValid) {
                throw new ValidationException(validationResult.errorMessage);
            }
        }
        user.addPaymentAccount(paymentAccount);
        TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        List<TradeCurrency> tradeCurrencies = paymentAccount.getTradeCurrencies();
        if (singleTradeCurrency != null) {
            if (singleTradeCurrency instanceof FiatCurrency)
                preferences.addFiatCurrency((FiatCurrency) singleTradeCurrency);
            else
                preferences.addCryptoCurrency((CryptoCurrency) singleTradeCurrency);
        } else if (tradeCurrencies != null && !tradeCurrencies.isEmpty()) {
            if (tradeCurrencies.contains(CurrencyUtil.getDefaultTradeCurrency()))
                paymentAccount.setSelectedTradeCurrency(CurrencyUtil.getDefaultTradeCurrency());
            else
                paymentAccount.setSelectedTradeCurrency(tradeCurrencies.get(0));

            tradeCurrencies.forEach(tradeCurrency -> {
                if (tradeCurrency instanceof FiatCurrency)
                    preferences.addFiatCurrency((FiatCurrency) tradeCurrency);
                else
                    preferences.addCryptoCurrency((CryptoCurrency) tradeCurrency);
            });
        }

        accountAgeWitnessService.publishMyAccountAgeWitness(paymentAccount.getPaymentAccountPayload());
        return paymentAccount;
    }


    public void removePaymentAccount(String id) {
        final PaymentAccount paymentAccount = user.getPaymentAccount(id);
        if (null == paymentAccount) {
            throw new NotFoundException("Payment account not found: " + id);
        }
        user.removePaymentAccount(paymentAccount);
    }

    private List<PaymentAccount> getPaymentAccountList() {
        return new ArrayList<>(user.getPaymentAccounts());
    }

    public PaymentAccountList getAccountList() {
        PaymentAccountList paymentAccountList = new PaymentAccountList();
        paymentAccountList.paymentAccounts = getPaymentAccountList().stream()
                .map(PaymentAccountHelper::toRestModel)
                .collect(Collectors.toList());
        return paymentAccountList;
    }


    /// START TODO REFACTOR OFFER TAKE DEPENDENCIES //////////////////////////


    /// STOP TODO REFACTOR OFFER TAKE DEPENDENCIES //////////////////////////

    public List<ClosedTradableDetails> getClosedTradableList() {
        return closedTradableManager.getClosedTradables().stream()
                .sorted((o1, o2) -> o2.getDate().compareTo(o1.getDate()))
                .map(closedTradableConverter::convert)
                .collect(toList());
    }

    public WalletTransactionList getWalletTransactions() {
        final Wallet wallet = walletsSetup.getBtcWallet();
        WalletTransactionList walletTransactions = new WalletTransactionList();
        walletTransactions.transactions.addAll(btcWalletService.getTransactions(true)
                .stream()
                .map(transaction -> toWalletTransaction(wallet, transaction))
                .collect(Collectors.toList()));
        walletTransactions.total = walletTransactions.transactions.size();
        return walletTransactions;
    }

    @NotNull
    private WalletTransaction toWalletTransaction(Wallet wallet, Transaction transaction) {
        final Coin valueSentFromMe = transaction.getValueSentFromMe(wallet);
        final Coin valueSentToMe = transaction.getValueSentToMe(wallet);
        boolean received = false;
        String addressString = null;

        if (valueSentToMe.isZero()) {
            for (TransactionOutput output : transaction.getOutputs()) {
                if (!btcWalletService.isTransactionOutputMine(output)) {
                    received = false;
                    if (WalletService.isOutputScriptConvertibleToAddress(output)) {
                        addressString = WalletService.getAddressStringFromOutput(output);
                        break;
                    }
                }
            }
        } else if (valueSentFromMe.isZero()) {
            received = true;
            for (TransactionOutput output : transaction.getOutputs()) {
                if (btcWalletService.isTransactionOutputMine(output) &&
                        WalletService.isOutputScriptConvertibleToAddress(output)) {
                    addressString = WalletService.getAddressStringFromOutput(output);
                    break;
                }
            }
        } else {
            boolean outgoing = false;
            for (TransactionOutput output : transaction.getOutputs()) {
                if (!btcWalletService.isTransactionOutputMine(output)) {
                    if (WalletService.isOutputScriptConvertibleToAddress(output)) {
                        addressString = WalletService.getAddressStringFromOutput(output);
                        outgoing = !(BisqEnvironment.isBaseCurrencySupportingBsq() && bsqWalletService.isTransactionOutputMine(output));
                        break;
                    }
                }
            }

            if (outgoing) {
                received = false;
            }
        }
        final TransactionConfidence confidence = transaction.getConfidence();
        int confirmations = null == confidence ? 0 : confidence.getDepthInBlocks();

        final WalletTransaction walletTransaction = new WalletTransaction();
        walletTransaction.updateTime = transaction.getUpdateTime().getTime();
        walletTransaction.hash = transaction.getHashAsString();
        walletTransaction.fee = (transaction.getFee() == null) ? -1 : transaction.getFee().value;
        walletTransaction.value = transaction.getValue(wallet).value;
        walletTransaction.valueSentFromMe = valueSentFromMe.value;
        walletTransaction.valueSentToMe = valueSentToMe.value;
        walletTransaction.confirmations = confirmations;
        walletTransaction.inbound = received;
        walletTransaction.address = addressString;
        return walletTransaction;
    }

    public WalletAddressList getWalletAddresses(WalletAddressPurpose purpose) {
        final Stream<AddressEntry> addressEntryStream;
        if (WalletAddressPurpose.SEND_FUNDS.equals(purpose)) {
            addressEntryStream = balanceUtil.getAddressEntriesForAvailableFunds();
        } else if (WalletAddressPurpose.RESERVED_FUNDS.equals(purpose)) {
            addressEntryStream = balanceUtil.getAddressEntriesForReservedFunds();
        } else if (WalletAddressPurpose.LOCKED_FUNDS.equals(purpose)) {
            addressEntryStream = balanceUtil.getAddressEntriesForLockedFunds();
        } else if (WalletAddressPurpose.RECEIVE_FUNDS.equals(purpose)) {
            addressEntryStream = btcWalletService.getAvailableAddressEntries().stream();
        } else {
            addressEntryStream = btcWalletService.getAddressEntryListAsImmutableList().stream();
        }
        final List<WalletAddress> walletAddresses = addressEntryStream
                .map(entry -> convertAddressEntryToWalletAddress(entry, btcWalletService))
                .collect(toList());
        final WalletAddressList walletAddressList = new WalletAddressList();
        walletAddressList.walletAddresses = walletAddresses;
        walletAddressList.total = walletAddresses.size();
        return walletAddressList;
    }

    public void withdrawFunds(Set<String> sourceAddresses, Coin amountAsCoin, boolean feeExcluded, String targetAddress)
            throws AddressEntryException, InsufficientFundsException, AmountTooLowException {
        // get all address entries
        final List<AddressEntry> sourceAddressEntries = sourceAddresses.stream()
                .filter(address -> null != address)
                .map(address -> btcWalletService.getAddressEntryListAsImmutableList().stream().filter(addressEntry -> address.equals(addressEntry.getAddressString())).findFirst().orElse(null))
                .filter(item -> null != item)
                .collect(Collectors.toList());
        // this filter matches all unauthorized address types
        Predicate<AddressEntry> filterNotAllowedAddressEntries = addressEntry -> !(AddressEntry.Context.AVAILABLE.equals(addressEntry.getContext())
                || AddressEntry.Context.TRADE_PAYOUT.equals(addressEntry.getContext()));
        // check if there are any unauthorized address types
        if (sourceAddressEntries.stream().anyMatch(filterNotAllowedAddressEntries)) {
            throw new ValidationException("Funds can be withdrawn only from addresses with context AVAILABLE and TRADE_PAYOUT");
        }

        Coin sendersAmount;
        // We do not know sendersAmount if senderPaysFee is true. We repeat fee calculation after first attempt if senderPaysFee is true.
        Transaction feeEstimationTransaction;
        try {
            feeEstimationTransaction = btcWalletService.getFeeEstimationTransactionForMultipleAddresses(sourceAddresses, amountAsCoin);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("dust limit")) {
                throw new AmountTooLowException(e.getMessage());
            }
            throw e;
        }
        if (feeExcluded && feeEstimationTransaction != null) {
            sendersAmount = amountAsCoin.add(feeEstimationTransaction.getFee());
            feeEstimationTransaction = btcWalletService.getFeeEstimationTransactionForMultipleAddresses(sourceAddresses, sendersAmount);
        }
        checkNotNull(feeEstimationTransaction, "feeEstimationTransaction must not be null");
        Coin fee = feeEstimationTransaction.getFee();
        sendersAmount = feeExcluded ? amountAsCoin.add(fee) : amountAsCoin;
        Coin receiverAmount = feeExcluded ? amountAsCoin : amountAsCoin.subtract(fee);

        final Coin totalAvailableAmountOfSelectedItems = sourceAddressEntries.stream()
                .map(address -> btcWalletService.getBalanceForAddress(address.getAddress()))
                .reduce(Coin.ZERO, Coin::add);

        if (!sendersAmount.isPositive())
            throw new ValidationException("Senders amount must be positive");
        if (!new BtcAddressValidator().validate(targetAddress).isValid)
            throw new ValidationException("Invalid target address");
        if (sourceAddresses.isEmpty())
            throw new ValidationException("List of source addresses must not be empty");
        if (sendersAmount.compareTo(totalAvailableAmountOfSelectedItems) > 0)
            throw new InsufficientFundsException("Not enough funds in selected addresses");

        if (receiverAmount.isPositive()) {
            try {
//                TODO return completable future
                btcWalletService.sendFundsForMultipleAddresses(sourceAddresses, targetAddress, amountAsCoin, fee, null, null, new FutureCallback<Transaction>() {
                    @Override
                    public void onSuccess(@javax.annotation.Nullable Transaction transaction) {
                        if (transaction != null) {
                            log.debug("onWithdraw onSuccess tx ID:" + transaction.getHashAsString());
                        } else {
                            log.error("onWithdraw transaction is null");
                        }

                        List<Trade> trades = new ArrayList<>(tradeManager.getTradableList());
                        trades.stream()
                                .filter(Trade::isPayoutPublished)
                                .forEach(trade -> btcWalletService.getAddressEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT)
                                        .ifPresent(addressEntry -> {
                                            if (btcWalletService.getBalanceForAddress(addressEntry.getAddress()).isZero())
                                                tradeManager.addTradeToClosedTrades(trade);
                                        }));
                    }

                    @Override
                    public void onFailure(@NotNull Throwable t) {
                        log.error("onWithdraw onFailure");
                    }
                });
            } catch (org.bitcoinj.core.InsufficientMoneyException e) {
                throw new InsufficientFundsException(e.getMessage());
            }
        } else {
            throw new AmountTooLowException(Res.get("portfolio.pending.step5_buyer.amountTooLow"));
        }
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

    public WalletAddress getOrCreateAvailableUnusedWalletAddresses() {
        final AddressEntry entry = btcWalletService.getFreshAddressEntry();
        return convertAddressEntryToWalletAddress(entry, btcWalletService);
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
        final boolean isPasswordValid = btcWalletService.isWalletReady() && btcWalletService.isEncrypted() && isWalletPasswordValid(password);
        if (isPasswordValid) {
            return new AuthResult(tokenRegistry.generateToken());
        }
        throw new UnauthorizedException();
    }

    private boolean isWalletPasswordValid(String password) {
        final KeyParameter aesKey = getAESKey(password);
        return isWalletPasswordValid(aesKey);
    }

    private boolean isWalletPasswordValid(KeyParameter aesKey) {
        return null != aesKey && walletsManager.checkAESKey(aesKey);
    }

    private KeyParameter getAESKey(String password) {
        return getAESKeyAndScrypt(password).first;
    }

    private Tuple2<KeyParameter, KeyCrypterScrypt> getAESKeyAndScrypt(String password) {
        final KeyCrypterScrypt keyCrypterScrypt = walletsManager.getKeyCrypterScrypt();
        return new Tuple2<>(keyCrypterScrypt.deriveKey(password), keyCrypterScrypt);
    }

    public AuthResult changePassword(String oldPassword, String newPassword) {
        if (!btcWalletService.isWalletReady())
            throw new WalletNotReadyException("Wallet not ready yet");
        if (btcWalletService.isEncrypted()) {
            final KeyParameter aesKey = null == oldPassword ? null : getAESKey(oldPassword);
            if (!isWalletPasswordValid(aesKey))
                throw new UnauthorizedException();
            walletsManager.decryptWallets(aesKey);
        }
        if (null != newPassword && newPassword.length() > 0) {
            final Tuple2<KeyParameter, KeyCrypterScrypt> aesKeyAndScrypt = getAESKeyAndScrypt(newPassword);
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
        if (null == shutdownHandler) {
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
            shutdownHandler.run();
        }, "Shutdown before backup restore").start();
    }

    public void uploadBackup(String fileName, InputStream uploadedInputStream) throws IOException {
        backupManager.saveBackup(fileName, uploadedInputStream);
    }

    public SeedWords getSeedWords(String password) {
        final DeterministicSeed keyChainSeed = btcWalletService.getKeyChainSeed();
        final LocalDate walletCreationDate = Instant.ofEpochSecond(walletsManager.getChainSeedCreationTimeSeconds()).atZone(ZoneId.systemDefault()).toLocalDate();

        DeterministicSeed seed = keyChainSeed;
        if (keyChainSeed.isEncrypted()) {
            if (null == password)
                throw new UnauthorizedException();
            final KeyParameter aesKey = getAESKey(password);
            if (!isWalletPasswordValid(aesKey))
                throw new UnauthorizedException();
            seed = walletsManager.getDecryptedSeed(aesKey, btcWalletService.getKeyChainSeed(), btcWalletService.getKeyCrypter());
        }

        return new SeedWords(seed.getMnemonicCode(), walletCreationDate.toString());
    }

    public CompletableFuture<Void> restoreWalletFromSeedWords(List<String> mnemonicCode, String walletCreationDate, String password) {
        if (btcWalletService.isEncrypted() && (null == password || !isWalletPasswordValid(password)))
            throw new UnauthorizedException();
        final CompletableFuture<Void> futureResult = new CompletableFuture<>();
        final long date = walletCreationDate != null ? LocalDate.parse(walletCreationDate).atStartOfDay().toEpochSecond(ZoneOffset.UTC) : 0;
        final DeterministicSeed seed = new DeterministicSeed(mnemonicCode, null, "", date);
//        TODO this logic comes from GUIUtils

        try {
            FileUtil.renameFile(new File(storageDir, "AddressEntryList"), new File(storageDir, "AddressEntryList_wallet_restore_" + System.currentTimeMillis()));
        } catch (Throwable t) {
            return failFuture(futureResult, t);
        }
        walletsManager.restoreSeedWords(
                seed,
                () -> futureResult.complete(null),
                throwable -> failFuture(futureResult, throwable));
        if (null != shutdownHandler)
            futureResult.thenRunAsync(shutdownHandler::run);
        return futureResult;
    }

    public enum WalletAddressPurpose {
        LOCKED_FUNDS,
        RECEIVE_FUNDS,
        RESERVED_FUNDS,
        SEND_FUNDS
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

    @NotNull
    private static WalletAddress convertAddressEntryToWalletAddress(AddressEntry entry, BtcWalletService btcWalletService) {
        final Coin balance;
        if (AddressEntry.Context.MULTI_SIG.equals(entry.getContext())) {
            balance = entry.getCoinLockedInMultiSig();
        } else {
            balance = btcWalletService.getBalanceForAddress(entry.getAddress());
        }
        final TransactionConfidence confidence = btcWalletService.getConfidenceForAddress(entry.getAddress());
        final int confirmations = null == confidence ? 0 : confidence.getDepthInBlocks();
        return new WalletAddress(entry.getAddressString(), balance.getValue(), confirmations, entry.getContext(), entry.getOfferId());
    }

    private static List<String> tradeCurrenciesToCodes(Collection<? extends TradeCurrency> tradeCurrencies) {
        return tradeCurrencies.stream().map(TradeCurrency::getCode).collect(Collectors.toList());
    }
}
