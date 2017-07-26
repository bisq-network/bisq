package io.bisq.api;

import com.google.common.base.Strings;
import io.bisq.api.model.*;
import io.bisq.api.model.Currency;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.util.MathUtils;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.WalletService;
import io.bisq.core.offer.*;
import io.bisq.core.payment.*;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.provider.price.MarketPrice;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.trade.protocol.BuyerAsMakerProtocol;
import io.bisq.core.trade.protocol.SellerAsTakerProtocol;
import io.bisq.core.trade.protocol.TradeProtocol;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.core.util.CoinUtil;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.P2PService;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * This class is a proxy for all bitsquare features the model will use.
 * <p>
 * No methods/representations used in the interface layers (REST/Socket/...) should be used in this class.
 */
@Slf4j
public class BisqProxy {
    private BtcWalletService btcWalletService;
    private User user;
    private TradeManager tradeManager;
    private OpenOfferManager openOfferManager;
    private OfferBookService offerBookService;
    private P2PService p2PService;
    private KeyRing keyRing;
    private PriceFeedService priceFeedService;
    private FeeService feeService;
    private Preferences preferences;
    private BsqWalletService bsqWalletService;


    private MarketPrice marketPrice;
    private boolean marketPriceAvailable;


    public BisqProxy(BtcWalletService btcWalletService, TradeManager tradeManager, OpenOfferManager openOfferManager,
                     OfferBookService offerBookService, P2PService p2PService, KeyRing keyRing,
                     PriceFeedService priceFeedService, User user, FeeService feeService, Preferences preferences, BsqWalletService bsqWalletService) {
        this.btcWalletService = btcWalletService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.offerBookService = offerBookService;
        this.p2PService = p2PService;
        this.keyRing = keyRing;
        this.priceFeedService = priceFeedService;
        this.user = user;
        this.feeService = feeService;
        this.preferences = preferences;
        this.bsqWalletService = bsqWalletService;
    }

    public CurrencyList getCurrencyList() {
        CurrencyList currencyList = new CurrencyList();
        CurrencyUtil.getAllSortedCryptoCurrencies().forEach(cryptoCurrency -> currencyList.add(cryptoCurrency.getCode(), cryptoCurrency.getName(), "crypto"));
        CurrencyUtil.getAllSortedFiatCurrencies().forEach(fiatCurrency -> currencyList.add(fiatCurrency.getCurrency().getSymbol(), fiatCurrency.getName(), "fiat"));
        Collections.sort(currencyList.currencies, (io.bisq.api.model.Currency p1, io.bisq.api.model.Currency p2) -> p1.name.compareTo(p2.name));
        return currencyList;
    }

    public MarketList getMarketList() {
        MarketList marketList = new MarketList();
        CurrencyList currencyList = getCurrencyList(); // we calculate this twice but only at startup
        //currencyList.getCurrencies().stream().flatMap(currency -> marketList.getMarkets().forEach(currency1 -> cur))
        List<Market> btc = CurrencyUtil.getAllSortedCryptoCurrencies().stream().filter(cryptoCurrency -> !(cryptoCurrency.getCode().equals("BTC"))).map(cryptoCurrency -> new Market(cryptoCurrency.getCode(), "BTC")).collect(toList());
        marketList.markets.addAll(btc);
        btc = CurrencyUtil.getAllSortedFiatCurrencies().stream().map(cryptoCurrency -> new Market("BTC", cryptoCurrency.getCode())).collect(toList());
        marketList.markets.addAll(btc);
        Collections.sort(currencyList.currencies, (io.bisq.api.model.Currency p1, Currency p2) -> p1.name.compareTo(p2.name));
        return marketList;
    }


    private List<PaymentAccount> getPaymentAccountList() {
        return new ArrayList(user.getPaymentAccounts());
    }

    public AccountList getAccountList() {
        AccountList accountList = new AccountList();
        accountList.accounts = getPaymentAccountList().stream()
                .map(paymentAccount -> new Account(paymentAccount)).collect(Collectors.toSet());
        return accountList;
    }

    public boolean offerCancel(String offerId) throws Exception {
        if (Strings.isNullOrEmpty(offerId)) {
            throw new Exception("offerId is null");
        }
        Optional<OpenOffer> openOfferById = openOfferManager.getOpenOfferById(offerId);
        if (!openOfferById.isPresent()) {
            throw new Exception("Offer with id:" + offerId + " was not found.");
        }
        // do something more intelligent here, maybe block till handler is called.
        Platform.runLater(() -> openOfferManager.removeOpenOffer(openOfferById.get(), () -> log.info("offer removed"), (err) -> log.error("Error removing offer: " + err)));
        return true;
    }

    public Optional<OfferData> getOfferDetail(String offerId) throws Exception {
        if (Strings.isNullOrEmpty(offerId)) {
            throw new Exception("OfferId is null");
        }
        Optional<Offer> offer = offerBookService.getOffers().stream().filter(offer1 -> offerId.equals(offer1.getId())).findAny();
        if (!offer.isPresent()) {
            throw new Exception("OfferId not found");
        }
        return Optional.of(new OfferData(offer.get()));
    }

    public List<OfferData> getOfferList() {
        //List<OfferData> offer = offerBookService.getOffers().stream().map(offer1 -> new OfferData(offer1)).collect(toList());
        List<OfferData> offer = openOfferManager.getObservableList().stream().map(offer1 -> new OfferData(offer1.getOffer())).collect(toList());
        return offer;

    }

    public boolean offerMake(String market, String accountId, OfferPayload.Direction direction, BigDecimal amount, BigDecimal minAmount,
                             boolean useMarketBasedPrice, double marketPriceMargin, String currencyCode, String counterCurrencyCode, String fiatPrice) {
        // TODO: detect bad direction, bad market, no paymentaccount for user
        // PaymentAccountUtil.isPaymentAccountValidForOffer
        Optional<PaymentAccount> optionalAccount = getPaymentAccountList().stream()
                .filter(account1 -> account1.getId().equals(accountId)).findFirst();
        if(!optionalAccount.isPresent()) {
            // return an error
            log.error("Colud not find payment account with id:{}", accountId);
            return false;
        }
        PaymentAccount paymentAccount = optionalAccount.get();

        // COPIED from CreateDataOfferModel: TODO refactor uit of GUI module  /////////////////////////////
        String countryCode = paymentAccount instanceof CountryBasedPaymentAccount ? ((CountryBasedPaymentAccount) paymentAccount).getCountry().code : null;
        ArrayList<String> acceptedCountryCodes = null;
        if (paymentAccount instanceof SepaAccount) {
            acceptedCountryCodes = new ArrayList<>();
            acceptedCountryCodes.addAll(((SepaAccount) paymentAccount).getAcceptedCountryCodes());
        } else if (paymentAccount instanceof CountryBasedPaymentAccount) {
            acceptedCountryCodes = new ArrayList<>();
            acceptedCountryCodes.add(((CountryBasedPaymentAccount) paymentAccount).getCountry().code);
        }
        String bankId = paymentAccount instanceof BankAccount ? ((BankAccount) paymentAccount).getBankId() : null;
        ArrayList<String> acceptedBanks = null;
        if (paymentAccount instanceof SpecificBanksAccount) {
            acceptedBanks = new ArrayList<>(((SpecificBanksAccount) paymentAccount).getAcceptedBanks());
        } else if (paymentAccount instanceof SameBankAccount) {
            acceptedBanks = new ArrayList<>();
            acceptedBanks.add(((SameBankAccount) paymentAccount).getBankId());
        }
        long maxTradeLimit = paymentAccount.getPaymentMethod().getMaxTradeLimitAsCoin(currencyCode).value;
        long maxTradePeriod = paymentAccount.getPaymentMethod().getMaxTradePeriod();
        boolean isPrivateOffer = false;
        boolean useAutoClose = false;
        boolean useReOpenAfterAutoClose = false;
        long lowerClosePrice = 0;
        long upperClosePrice = 0;
        String hashOfChallenge = null;
        HashMap<String, String> extraDataMap = null;

        // COPIED from CreateDataOfferModel /////////////////////////////

        updateMarketPriceAvailable(currencyCode);

        // TODO there are a lot of dummy values in this constructor !!!
        Coin coinAmount = Coin.valueOf(amount.longValueExact());
        OfferPayload offerPayload = new OfferPayload(
                UUID.randomUUID().toString(),
                new Date().getTime(),
                p2PService.getAddress(),
                keyRing.getPubKeyRing(),
                direction,
                Long.valueOf(fiatPrice),
                marketPriceMargin,
                useMarketBasedPrice,
                amount.longValueExact(),
                minAmount.longValueExact(),
                currencyCode,
                counterCurrencyCode,
                (ArrayList<NodeAddress>) user.getAcceptedArbitratorAddresses(),
                (ArrayList<NodeAddress>) user.getAcceptedMediatorAddresses(),
                paymentAccount.getPaymentMethod().getId(),
                paymentAccount.getId(),
                null, // "TO BE FILLED IN", // offerfeepaymenttxid ???
                countryCode,
                acceptedCountryCodes,
                bankId,
                acceptedBanks,
                Version.VERSION,
                btcWalletService.getLastBlockSeenHeight(),
                feeService.getTxFee(600).value,
                getMakerFee(coinAmount, marketPriceMargin).value,
                preferences.getPayFeeInBtc() || !isBsqForFeeAvailable(coinAmount, marketPriceMargin),
                preferences.getBuyerSecurityDepositAsCoin().value,
                Restrictions.getSellerSecurityDeposit().value,
                maxTradeLimit,
                maxTradePeriod,
                useAutoClose,
                useReOpenAfterAutoClose,
                upperClosePrice,
                lowerClosePrice,
                isPrivateOffer,
                hashOfChallenge,
                extraDataMap,
                Version.TRADE_PROTOCOL_VERSION
                );

        Offer offer = new Offer(offerPayload); // priceFeedService);

        try {
            // TODO subtract OfferFee: .subtract(FeePolicy.getCreateOfferFee())
            openOfferManager.placeOffer(offer, Coin.valueOf(amount.longValue()),
                    true, (transaction) -> log.info("Result is " + transaction));
        } catch(Throwable e) {
            return false;
        }
        return true;
    }

    /// START TODO refactor out of GUI module ////

    boolean isBsqForFeeAvailable(Coin amount, double marketPriceMargin) {
        return BisqEnvironment.isBaseCurrencySupportingBsq() &&
                getMakerFee(false, amount, marketPriceMargin) != null &&
                bsqWalletService.getAvailableBalance() != null &&
                getMakerFee(false, amount, marketPriceMargin) != null &&
                !bsqWalletService.getAvailableBalance().subtract(getMakerFee(false, amount, marketPriceMargin)).isNegative();
    }

    boolean isCurrencyForMakerFeeBtc(Coin amount, double marketPriceMargin) {
        return preferences.getPayFeeInBtc() || !isBsqForFeeAvailable(amount, marketPriceMargin);
    }

    private void updateMarketPriceAvailable(String baseCurrencyCode) {
        marketPrice = priceFeedService.getMarketPrice(baseCurrencyCode);
        marketPriceAvailable = (marketPrice != null );
    }

    @Nullable
    public Coin getMakerFee(Coin amount, double marketPriceMargin) {
        return getMakerFee(isCurrencyForMakerFeeBtc(amount, marketPriceMargin), amount, marketPriceMargin);
    }

    @Nullable
    Coin getMakerFee(boolean isCurrencyForMakerFeeBtc, Coin amount, double marketPriceMargin) {
        if (amount != null) {
            final Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getMakerFeePerBtc(isCurrencyForMakerFeeBtc), amount);
            double makerFeeAsDouble = (double) feePerBtc.value;
            if (marketPriceAvailable) {
                if (marketPriceMargin > 0)
                    makerFeeAsDouble = makerFeeAsDouble * Math.sqrt(marketPriceMargin * 100);
                else
                    makerFeeAsDouble = 0;
                // For BTC we round so min value change is 100 satoshi
                if (isCurrencyForMakerFeeBtc)
                    makerFeeAsDouble = MathUtils.roundDouble(makerFeeAsDouble / 100, 0) * 100;
            }

            return CoinUtil.maxCoin(Coin.valueOf(MathUtils.doubleToLong(makerFeeAsDouble)), FeeService.getMinMakerFee(isCurrencyForMakerFeeBtc));
        } else {
            return null;
        }
    }

    /// STOP TODO refactor out of GUI module ////

    public void offerTake() {
        //openOfferManager.

    }

    public TradeList getTradeList() {
        TradeList tradeList = new TradeList();
        tradeList.setTrades(tradeManager.getTradableList().sorted());
        return tradeList;
    }

    public Optional<Trade> getTrade(String tradeId) {
        return getTradeList().getTrade().stream().filter(trade -> trade.getId().equals(tradeId)).findAny();
    }

    public WalletDetails getWalletDetails() {
        if (!btcWalletService.isWalletReady()) {
            return null;
        }

        Coin availableBalance = btcWalletService.getAvailableBalance();
        Coin reservedBalance = btcWalletService.getBalance(Wallet.BalanceType.ESTIMATED_SPENDABLE);
        return new WalletDetails(availableBalance.toPlainString(), reservedBalance.toPlainString());
    }

    public WalletTransactions getWalletTransactions(long start, long end, long limit) {
        boolean includeDeadTransactions = false;
        Set<Transaction> transactions = btcWalletService.getTransactions(includeDeadTransactions);
        WalletTransactions walletTransactions = new WalletTransactions();
        List<WalletTransaction> transactionList = walletTransactions.getTransactions();

        for (Transaction t : transactions) {
//            transactionList.add(new WalletTransaction(t.getValue(btcWalletService.getWallet().getTransactionsByTime())))
        }
        return null;
    }

    public List<WalletAddress> getWalletAddresses() {
        return user.getPaymentAccounts().stream()
                .filter(paymentAccount -> paymentAccount instanceof CryptoCurrencyAccount)
                .map(paymentAccount -> (CryptoCurrencyAccount) paymentAccount)
                .map(paymentAccount -> new WalletAddress(((CryptoCurrencyAccount) paymentAccount).getId(), paymentAccount.getPaymentMethod().toString(), ((CryptoCurrencyAccount) paymentAccount).getAddress()))
                .collect(toList());
    }

    public boolean paymentStarted(String tradeId) {
        Optional<Trade> tradeOpt = getTrade(tradeId);
        if(!tradeOpt.isPresent())
            return false;
        Trade trade = tradeOpt.get();

        TradeProtocol tradeProtocol = trade.getTradeProtocol();
        // if test here to decide maker/taker
        Platform.runLater(() -> {
            ((BuyerAsMakerProtocol) tradeProtocol).onFiatPaymentStarted(() -> {
                        log.info("Fiat payment started.");
                    },
                    (e) -> {
                        log.error("Error onFiatPaymentStarted", e);
                    });
        });
        return true; // TODO better return value?
    }

    public boolean paymentReceived(String tradeId) {
        Optional<Trade> tradeOpt = getTrade(tradeId);
        if(!tradeOpt.isPresent())
            return false;
        Trade trade = tradeOpt.get();
        TradeProtocol tradeProtocol = trade.getTradeProtocol();
        // if test here to decide maker/taker
        Platform.runLater(() -> {
            ((SellerAsTakerProtocol) tradeProtocol).onFiatPaymentReceived(() -> {
                        log.info("Fiat payment started.");
                    },
                    (e) -> {
                        log.error("Error onFiatPaymentStarted", e);
                    });
        });
        return true; // TODO better return value?
    }

    public boolean moveFundsToBisqWallet(String tradeId) {
        Optional<Trade> tradeOpt = getTrade(tradeId);
        if(!tradeOpt.isPresent())
            return false;
        Trade trade = tradeOpt.get();

        Platform.runLater(() -> {
            btcWalletService.swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT);
            // TODO do we need to handle this ui stuff? --> handleTradeCompleted();
            tradeManager.addTradeToClosedTrades(trade);
        });

        return true; // TODO better return value?
    }
}
