package io.bisq.api;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.inject.Injector;
import io.bisq.api.model.*;
import io.bisq.common.app.DevEnv;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.locale.CryptoCurrency;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.FiatCurrency;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.util.Tuple2;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.core.arbitration.ArbitratorManager;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.WalletsSetup;
import io.bisq.core.offer.*;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.CryptoCurrencyAccount;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.trade.BuyerAsMakerTrade;
import io.bisq.core.trade.SellerAsMakerTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.trade.closed.ClosedTradableManager;
import io.bisq.core.trade.failed.FailedTradesManager;
import io.bisq.core.trade.protocol.*;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.core.util.CoinUtil;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.P2PService;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.validation.ValidationException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bisq.core.payment.PaymentAccountUtil.isPaymentAccountValidForOffer;
import static java.util.stream.Collectors.toList;

/**
 * This class is a proxy for all bitsquare features the model will use.
 * <p/>
 * No methods/representations used in the interface layers (REST/Socket/...) should be used in this class.
 * => this should be the common gateway to bisq used by all outward-facing API classes.
 * <p/>
 * If the bisq code is refactored correctly, this class could become very light.
 */
@Slf4j
public class BisqProxy {
    private final Injector injector;
    private AccountAgeWitnessService accountAgeWitnessService;
    private ArbitratorManager arbitratorManager;
    private BtcWalletService btcWalletService;
    private User user;
    private TradeManager tradeManager;
    private ClosedTradableManager closedTradableManager;
    private FailedTradesManager failedTradesManager;
    private OpenOfferManager openOfferManager;
    private OfferBookService offerBookService;
    private P2PService p2PService;
    private KeyRing keyRing;
    private FeeService feeService;
    private Preferences preferences;
    private BsqWalletService bsqWalletService;
    private final boolean useDevPrivilegeKeys;
    private WalletsSetup walletsSetup;
    @Getter
    private MarketList marketList;
    @Getter
    private CurrencyList currencyList;

    public BisqProxy(Injector injector, AccountAgeWitnessService accountAgeWitnessService, ArbitratorManager arbitratorManager, BtcWalletService btcWalletService, TradeManager tradeManager, OpenOfferManager openOfferManager,
                     OfferBookService offerBookService, P2PService p2PService, KeyRing keyRing, User user,
                     FeeService feeService, Preferences preferences, BsqWalletService bsqWalletService, WalletsSetup walletsSetup,
                     ClosedTradableManager closedTradableManager, FailedTradesManager failedTradesManager, boolean useDevPrivilegeKeys) {
        this.injector = injector;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.arbitratorManager = arbitratorManager;
        this.btcWalletService = btcWalletService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.offerBookService = offerBookService;
        this.p2PService = p2PService;
        this.keyRing = keyRing;
        this.user = user;
        this.feeService = feeService;
        this.preferences = preferences;
        this.bsqWalletService = bsqWalletService;
        this.marketList = calculateMarketList();
        this.currencyList = calculateCurrencyList();
        this.walletsSetup = walletsSetup;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
    }

    public static CurrencyList calculateCurrencyList() {
        CurrencyList currencyList = new CurrencyList();
        CurrencyUtil.getAllSortedCryptoCurrencies().forEach(cryptoCurrency -> currencyList.add(cryptoCurrency.getCode(), cryptoCurrency.getName(), "crypto"));
        CurrencyUtil.getAllSortedFiatCurrencies().forEach(fiatCurrency -> currencyList.add(fiatCurrency.getCurrency().getSymbol(), fiatCurrency.getName(), "fiat"));
        Collections.sort(currencyList.currencies, (io.bisq.api.model.Currency p1, io.bisq.api.model.Currency p2) -> p1.name.compareTo(p2.name));
        return currencyList;
    }

    public static MarketList calculateMarketList() {
        MarketList marketList = new MarketList();
        CurrencyList currencyList = calculateCurrencyList(); // we calculate this twice but only at startup
        //currencyList.getCurrencies().stream().flatMap(currency -> marketList.getMarkets().forEach(currency1 -> cur))
        List<Market> btc = CurrencyUtil.getAllSortedCryptoCurrencies().stream().filter(cryptoCurrency -> !(cryptoCurrency.getCode().equals("BTC"))).map(cryptoCurrency -> new Market(cryptoCurrency.getCode(), "BTC")).collect(toList());
        marketList.markets.addAll(btc);
        btc = CurrencyUtil.getAllSortedFiatCurrencies().stream().map(cryptoCurrency -> new Market("BTC", cryptoCurrency.getCode())).collect(toList());
        marketList.markets.addAll(btc);
        Collections.sort(currencyList.currencies, Comparator.comparing(p -> p.name));
        return marketList;
    }

    public io.bisq.api.model.PaymentAccount addPaymentAccount(AccountToCreate account) {
        final PaymentAccount paymentAccount = PaymentAccountHelper.toBusinessModel(account);
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
        return PaymentAccountHelper.toRestModel(user.currentPaymentAccountProperty().get());
    }


    public void removePaymentAccount(String id) {
        final PaymentAccount paymentAccount = user.getPaymentAccount(id);
        if (null == paymentAccount) {
            throw new NotFoundException("Payment account not found: " + id);
        }
        user.removePaymentAccount(paymentAccount);
    }

    private List<PaymentAccount> getPaymentAccountList() {
        return new ArrayList(user.getPaymentAccounts());
    }

    private PaymentAccount getPaymentAccount(String paymentAccountId) {
        return user.getPaymentAccount(paymentAccountId);
    }

    public PaymentAccountList getAccountList() {
        PaymentAccountList paymentAccountList = new PaymentAccountList();
        paymentAccountList.paymentAccounts = getPaymentAccountList().stream()
                .map(PaymentAccountHelper::toRestModel)
                .collect(Collectors.toList());
        return paymentAccountList;
    }

    public Optional<BisqProxyError> offerCancel(String offerId) {
        if (Strings.isNullOrEmpty(offerId)) {
            BisqProxyError.getOptional("offerId is null");
        }
        Optional<OpenOffer> openOfferById = openOfferManager.getOpenOfferById(offerId);
        if (!openOfferById.isPresent()) {
            BisqProxyError.getOptional("Offer with id:" + offerId + " was not found.");
        }
        // do something more intelligent here, maybe block till handler is called.
        Platform.runLater(() -> openOfferManager.removeOpenOffer(openOfferById.get(), () -> log.info("offer removed"), (err) -> log.error("Error removing offer: " + err)));
        return Optional.empty();
    }

    public Optional<Offer> getOffer(String offerId) throws Exception {
        if (Strings.isNullOrEmpty(offerId)) {
            throw new Exception("OfferId is null");
        }
        Optional<Offer> offer = offerBookService.getOffers().stream().filter(offer1 -> offerId.equals(offer1.getId())).findAny();
        return offer;
    }

    /**
     * Return detail for a particular offerId.
     *
     * @returns a tuple, with as first member an optional result, as second member an optional error.
     */
    public Tuple2<Optional<OfferDetail>, Optional<BisqProxyError>> getOfferDetail(String offerId) throws Exception {
        Optional<OfferDetail> result = Optional.empty();
        Optional<BisqProxyError> error = Optional.empty();

        Optional<Offer> offer = getOffer(offerId);
        if (!offer.isPresent()) {
            error = BisqProxyError.getOptional("OfferId not found");
        }
        result = Optional.of(new OfferDetail(offer.get()));

        return new Tuple2<>(result, error);
    }

    public List<OfferDetail> getOfferList() {
        List<OfferDetail> offer = offerBookService.getOffers().stream().map(offer1 -> new OfferDetail(offer1)).collect(toList());
        //List<OfferDetail> offer = openOfferManager.getObservableList().stream().map(offer1 -> new OfferDetail(offer1.getOffer())).collect(toList());
        return offer;

    }

    public CompletableFuture<Transaction> offerMake(String accountId, OfferPayload.Direction direction, BigDecimal amount, BigDecimal minAmount,
                                                    boolean useMarketBasedPrice, Double marketPriceMargin, String marketPair, long fiatPrice) {
//TODO add security deposit parameter
        // exception from gui code is not clear enough, so this check is added. Missing money is another possible check but that's clear in the gui exception.
        final CompletableFuture<Transaction> futureResult = new CompletableFuture<>();

        final OfferBuilder offerBuilder = injector.getInstance(OfferBuilder.class);
        final Offer offer;
        try {
            offer = offerBuilder.build(accountId, direction, amount, minAmount, useMarketBasedPrice, marketPriceMargin, marketPair, fiatPrice);
        } catch (Exception e) {
            return failFuture(futureResult, e);
        }
        Coin reservedFundsForOffer = OfferUtil.isBuyOffer(direction) ? preferences.getBuyerSecurityDepositAsCoin() : Restrictions.getSellerSecurityDeposit();
        if (!OfferUtil.isBuyOffer(direction))
            reservedFundsForOffer = reservedFundsForOffer.add(Coin.valueOf(amount.longValue()));

        openOfferManager.placeOffer(offer, reservedFundsForOffer,
                true,
                futureResult::complete,
                error -> futureResult.completeExceptionally(new RuntimeException(error))
        );

        return futureResult;
    }

    @NotNull
    private <T> CompletableFuture<T> failFuture(CompletableFuture<T> futureResult, Exception ex) {
        futureResult.completeExceptionally(ex);
        return futureResult;
    }

    /// START TODO REFACTOR OFFER TAKE DEPENDENCIES //////////////////////////

    /**
     * TODO TakeOfferDataModel.initWithData(Offer) needs to be refactored for fee calculation etc.
     *
     * @param offerId
     * @param paymentAccountId
     * @param amount
     * @return
     * @throws Exception
     */
    public Optional<BisqProxyError> offerTake(String offerId, String paymentAccountId, String amount, boolean useSavingsWallet) {
        try {
            // check that the offerId is valid
            Optional<Offer> offerOptional = getOffer(offerId);
            if (!offerOptional.isPresent()) {
                return BisqProxyError.getOptional("Unknown offer id");
            }
            Offer offer = offerOptional.get();

            // check the paymentAccountId is valid
            PaymentAccount paymentAccount = getPaymentAccount(paymentAccountId);
            if (paymentAccount == null) {
                return BisqProxyError.getOptional("Unknown payment account id");
            }

            // check the paymentAccountId is compatible with the offer
            if (!isPaymentAccountValidForOffer(offer, paymentAccount)) {
                return BisqProxyError.getOptional("PaymentAccount is not valid for offer, needs " + offer.getCurrencyCode());
            }

            // check the amount is within the range
            Coin coinAmount = Coin.valueOf(Long.valueOf(amount));
            //if(coinAmount.isLessThan(offer.getMinAmount()) || coinAmount.isGreaterThan(offer.getma)

            // workaround because TradeTask does not have an error handler to notify us that something went wrong
            if (btcWalletService.getAvailableBalance().isLessThan(coinAmount)) {
                return BisqProxyError.getOptional("Available balance " + btcWalletService.getAvailableBalance() + " is less than needed amount: " + coinAmount);
            }

            // check that the price is correct ??

            // check taker fee

            // check security deposit for BTC buyer
            // check security deposit for BTC seller

            Coin securityDeposit = offer.getDirection() == OfferPayload.Direction.SELL ?
                    offer.getBuyerSecurityDeposit() :
                    offer.getSellerSecurityDeposit();
            Coin txFeeFromFeeService = feeService.getTxFee(600);
            Coin fundsNeededForTradeTemp = securityDeposit.add(txFeeFromFeeService).add(txFeeFromFeeService);
            final Coin fundsNeededForTrade;
            if (offer.isBuyOffer())
                fundsNeededForTrade = fundsNeededForTradeTemp.add(coinAmount);
            else
                fundsNeededForTrade = fundsNeededForTradeTemp;

            Coin takerFee = getTakerFee(coinAmount);
            checkNotNull(txFeeFromFeeService, "txFeeFromFeeService must not be null");
            checkNotNull(takerFee, "takerFee must not be null");

            Platform.runLater(() -> {
                tradeManager.onTakeOffer(coinAmount,
                        txFeeFromFeeService,
                        takerFee,
                        isCurrencyForTakerFeeBtc(coinAmount),
                        offer.getPrice().getValue(),
                        fundsNeededForTrade,
                        offer,
                        paymentAccount.getId(),
                        useSavingsWallet,
                        (trade) -> log.info("Trade offer taken, offer:{}, trade:{}", offer.getId(), trade.getId()),
                        errorMessage -> {
                            log.warn(errorMessage);
                        }
                );
            });
            return Optional.empty();
        } catch (Throwable e) {
            return BisqProxyError.getOptional(e.getMessage(), e);
        }
    }

    boolean isCurrencyForTakerFeeBtc(Coin amount) {
        return preferences.getPayFeeInBtc() || !isBsqForFeeAvailable(amount);
    }

    @Nullable
    Coin getTakerFee(Coin amount, boolean isCurrencyForTakerFeeBtc) {
        if (amount != null) {
            // TODO write unit test for that
            Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getTakerFeePerBtc(isCurrencyForTakerFeeBtc), amount);
            return CoinUtil.maxCoin(feePerBtc, FeeService.getMinTakerFee(isCurrencyForTakerFeeBtc));
        } else {
            return null;
        }
    }

    @Nullable
    public Coin getTakerFee(Coin amount) {
        return getTakerFee(amount, isCurrencyForTakerFeeBtc(amount));
    }


    boolean isBsqForFeeAvailable(Coin amount) {
        return BisqEnvironment.isBaseCurrencySupportingBsq() &&
                getTakerFee(amount, false) != null &&
                bsqWalletService.getAvailableBalance() != null &&
                getTakerFee(amount, false) != null &&
                !bsqWalletService.getAvailableBalance().subtract(getTakerFee(amount, false)).isNegative();
    }

    /// STOP TODO REFACTOR OFFER TAKE DEPENDENCIES //////////////////////////

    public TradeList getTradeList() {
        TradeList tradeList = new TradeList();
        ObservableList<Trade> tradableList = tradeManager.getTradableList();
        tradeList.setTrades(tradableList == null || tradableList.size() == 0 ? Lists.newArrayList() : tradableList.sorted());
        return tradeList;
    }

    public Optional<Trade> getTrade(String tradeId) {
        return getTradeList().getTrade().stream().filter(trade -> trade.getId().equals(tradeId)).findAny();
    }

    public BisqProxyResult<WalletDetails> getWalletDetails() {
        if (!btcWalletService.isWalletReady()) {
            return BisqProxyResult.createSimpleError("Wallet is not ready");
        }

        Coin availableBalance = btcWalletService.getAvailableBalance();
        Coin reservedBalance = updateReservedBalance();
        Coin lockedBalance = updateLockedBalance();
        return new BisqProxyResult<>(new WalletDetails(availableBalance.toPlainString(), reservedBalance.toPlainString(), lockedBalance.toPlainString()));
    }

    // TODO copied from MainViewModel - refactor !
    private Coin updateLockedBalance() {
        Stream<Trade> lockedTrades = Stream.concat(closedTradableManager.getLockedTradesStream(), failedTradesManager.getLockedTradesStream());
        lockedTrades = Stream.concat(lockedTrades, tradeManager.getLockedTradesStream());
        Coin sum = Coin.valueOf(lockedTrades
                .mapToLong(trade -> {
                    final Optional<AddressEntry> addressEntryOptional = btcWalletService.getAddressEntry(trade.getId(), AddressEntry.Context.MULTI_SIG);
                    if (addressEntryOptional.isPresent())
                        return addressEntryOptional.get().getCoinLockedInMultiSig().getValue();
                    else
                        return 0;
                })
                .sum());
        return sum;
    }

    // TODO copied from MainViewModel - refactor !
    private Coin updateReservedBalance() {
        Coin sum = Coin.valueOf(openOfferManager.getObservableList().stream()
                .map(openOffer -> {
                    final Optional<AddressEntry> addressEntryOptional = btcWalletService.getAddressEntry(openOffer.getId(), AddressEntry.Context.RESERVED_FOR_TRADE);
                    if (addressEntryOptional.isPresent()) {
                        Address address = addressEntryOptional.get().getAddress();
                        return btcWalletService.getBalanceForAddress(address);
                    } else {
                        return null;
                    }
                })
                .filter(e -> e != null)
                .mapToLong(Coin::getValue)
                .sum());

        return sum;
    }


    public WalletTransactionList getWalletTransactions() {
        boolean includeDeadTransactions = true;
        Set<Transaction> transactions = btcWalletService.getTransactions(includeDeadTransactions);

        WalletTransactionList walletTransactions = new WalletTransactionList();

        for (Transaction t : transactions) {
            walletTransactions.transactions.add(new WalletTransaction(t, walletsSetup.getBtcWallet()));
        }
        return walletTransactions;
    }

    public List<WalletAddress> getWalletAddresses() {
        return user.getPaymentAccounts().stream()
                .filter(paymentAccount -> paymentAccount instanceof CryptoCurrencyAccount)
                .map(paymentAccount -> (CryptoCurrencyAccount) paymentAccount)
                .map(paymentAccount -> new WalletAddress(((CryptoCurrencyAccount) paymentAccount).getId(), paymentAccount.getPaymentMethod().toString(), ((CryptoCurrencyAccount) paymentAccount).getAddress()))
                .collect(toList());
    }

    public Optional<BisqProxyError> paymentStarted(String tradeId) {
        try {
            Optional<Trade> tradeOpt = getTrade(tradeId);
            if (!tradeOpt.isPresent()) {
                return BisqProxyError.getOptional("Could not find trade id " + tradeId);
            }
            Trade trade = tradeOpt.get();

            if (!Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN.equals(trade.getState())) {
                return BisqProxyError.getOptional("Trade is not in the correct state to start payment: " + trade.getState());
            }
            TradeProtocol tradeProtocol = trade.getTradeProtocol();

            // use countdownlatch to block this method until there's a success/error callback call
            CountDownLatch startPaymentLatch = new CountDownLatch(1);
            // TODO remove ugly workaround - probably implies refactoring
            String[] errorResult = new String[1];

            // common resulthandler
            ResultHandler resultHandler = () -> {
                log.info("Fiat payment started.");
                errorResult[0] = "";
                startPaymentLatch.countDown();

            };
            // comon errorhandler
            ErrorMessageHandler errorResultHandler = (error) -> {
                log.error("Error onFiatPaymentStarted", error);
                startPaymentLatch.countDown();
                errorResult[0] = error;
            };

            Runnable buyerAsMakerStartFiatPayment = () -> {
                ((BuyerAsMakerProtocol) tradeProtocol).onFiatPaymentStarted(resultHandler, errorResultHandler);
            };

            Runnable buyerAsTakerStartFiatPayment = () -> {
                ((BuyerAsTakerProtocol) tradeProtocol).onFiatPaymentStarted(resultHandler, errorResultHandler);
            };

            Platform.runLater(trade instanceof BuyerAsMakerTrade ? buyerAsMakerStartFiatPayment : buyerAsTakerStartFiatPayment);
            // wait X seconds for a result or timeout
            if (startPaymentLatch.await(5L, TimeUnit.SECONDS))
                if (errorResult[0] == "")
                    return Optional.empty();
                else
                    return BisqProxyError.getOptional("Error while starting payment:" + errorResult[0]);
            else
                return BisqProxyError.getOptional("Timeout exceeded, check the logs for errors."); // Timeout exceeded
        } catch (Throwable e) {
            return BisqProxyError.getOptional(e.getMessage(), e);
        }
    }

    public Optional<BisqProxyError> paymentReceived(String tradeId) {
        try {
            Optional<Trade> tradeOpt = getTrade(tradeId);
            if (!tradeOpt.isPresent()) {
                return BisqProxyError.getOptional("Could not find trade id " + tradeId);
            }
            Trade trade = tradeOpt.get();

            if (!Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG.equals(trade.getState())) {
                return BisqProxyError.getOptional("Trade is not in the correct state to start payment: " + trade.getState());
            }
            TradeProtocol tradeProtocol = trade.getTradeProtocol();

            if (!(tradeProtocol instanceof SellerAsTakerProtocol || tradeProtocol instanceof SellerAsMakerProtocol)) {
                return BisqProxyError.getOptional("Trade is not in the correct state to start payment received: " + tradeProtocol.getClass().getSimpleName());
            }

            // use countdownlatch to block this method until there's a success/error callback call
            CountDownLatch startPaymentLatch = new CountDownLatch(1);
            // TODO remove ugly workaround - probably implies refactoring
            String[] errorResult = new String[1];

            // common resulthandler
            ResultHandler resultHandler = () -> {
                log.info("Fiat payment received.");
                errorResult[0] = "";
                startPaymentLatch.countDown();

            };
            // comon errorhandler
            ErrorMessageHandler errorResultHandler = (error) -> {
                log.error("Error onFiatPaymentReceived", error);
                startPaymentLatch.countDown();
                errorResult[0] = error;
            };

            Runnable sellerAsMakerStartFiatPayment = () -> {
                ((SellerAsMakerProtocol) tradeProtocol).onFiatPaymentReceived(resultHandler, errorResultHandler);
            };

            Runnable sellerAsTakerStartFiatPayment = () -> {
                ((SellerAsTakerProtocol) tradeProtocol).onFiatPaymentReceived(resultHandler, errorResultHandler);
            };

            Platform.runLater(trade instanceof SellerAsMakerTrade ? sellerAsMakerStartFiatPayment : sellerAsTakerStartFiatPayment);
            // wait X seconds for a result or timeout
            if (startPaymentLatch.await(5L, TimeUnit.SECONDS))
                if (errorResult[0] == "")
                    return Optional.empty();
                else
                    return BisqProxyError.getOptional("Error while executing payment received:" + errorResult[0]);
            else
                return BisqProxyError.getOptional("Timeout exceeded, check the logs for errors."); // Timeout exceeded
        } catch (Throwable e) {
            return BisqProxyError.getOptional(e.getMessage(), e);
        }
    }

    public boolean moveFundsToBisqWallet(String tradeId) {
        Optional<Trade> tradeOpt = getTrade(tradeId);
        if (!tradeOpt.isPresent())
            return false;
        Trade trade = tradeOpt.get();

        Platform.runLater(() -> {
            btcWalletService.swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT);
            // TODO do we need to handle this ui stuff? --> handleTradeCompleted();
            tradeManager.addTradeToClosedTrades(trade);
        });

        return true; // TODO better return value?
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
        AddressEntry arbitratorDepositAddressEntry = btcWalletService.getOrCreateAddressEntry(AddressEntry.Context.ARBITRATOR);
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
}
