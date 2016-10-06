package io.bitsquare.api;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import io.bitsquare.api.api.*;
import io.bitsquare.api.api.Currency;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.pricefeed.PriceFeedService;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.CryptoCurrencyAccount;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OfferBookService;
import io.bitsquare.user.User;
import jersey.repackaged.com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is a proxy for all bitsquare features the api will use.
 * <p>
 * No methods/representations used in the interface layers (REST/Socket/...) should be used in this class.
 */
@Slf4j
public class BitsquareProxy {
    @Inject
    private WalletService walletService;
    @Inject
    private User user;
    @Inject
    private TradeManager tradeManager;
    @Inject
    private OfferBookService offerBookService;
    @Inject
    private P2PService p2PService;
    @Inject
    private KeyRing keyRing;
    @Inject
    private PriceFeedService priceFeedService;

    public BitsquareProxy(WalletService walletService, TradeManager tradeManager, OfferBookService offerBookService,
                          P2PService p2PService, KeyRing keyRing, PriceFeedService priceFeedService, User user) {
        this.walletService = walletService;
        this.tradeManager = tradeManager;
        this.offerBookService = offerBookService;
        this.p2PService = p2PService;
        this.keyRing = keyRing;
        this.priceFeedService = priceFeedService;
        this.user = user;
    }

    public CurrencyList getCurrencyList() {
        CurrencyList currencyList = new CurrencyList();
        CurrencyUtil.getAllSortedCryptoCurrencies().forEach(cryptoCurrency -> currencyList.add(cryptoCurrency.getCode(), cryptoCurrency.getName(), "crypto"));
        CurrencyUtil.getAllSortedFiatCurrencies().forEach(cryptoCurrency -> currencyList.add(cryptoCurrency.getSymbol(), cryptoCurrency.getName(), "fiat"));
        Collections.sort(currencyList.currencies, (Currency p1, Currency p2) -> p1.name.compareTo(p2.name));
        return currencyList;
    }

    public MarketList getMarketList() {
        MarketList marketList = new MarketList();
        CurrencyList currencyList = getCurrencyList(); // we calculate this twice but only at startup
        //currencyList.getCurrencies().stream().flatMap(currency -> marketList.getMarkets().forEach(currency1 -> cur))
        List<Market> btc = CurrencyUtil.getAllSortedCryptoCurrencies().stream().filter(cryptoCurrency -> !(cryptoCurrency.getCode().equals("BTC"))).map(cryptoCurrency -> new Market(cryptoCurrency.getCode(), "BTC")).collect(Collectors.toList());
        marketList.markets.addAll(btc);
        btc = CurrencyUtil.getAllSortedFiatCurrencies().stream().map(cryptoCurrency -> new Market("BTC", cryptoCurrency.getCode())).collect(Collectors.toList());
        marketList.markets.addAll(btc);
        Collections.sort(currencyList.currencies, (Currency p1, Currency p2) -> p1.name.compareTo(p2.name));
        return marketList;
    }


    public AccountList getAccountList() {
        AccountList accountList = new AccountList();
        accountList.accounts = user.getPaymentAccounts().stream()
                .map(paymentAccount -> new Account(paymentAccount)).collect(Collectors.toSet());
        return accountList;
    }

    public boolean offerCancel(String offerId) {
        if (Strings.isNullOrEmpty(offerId)) {
            return false;
        }
        Optional<Offer> offer = offerBookService.getOffers().stream().filter(offer1 -> offerId.equals(offer1.getId())).findAny();
        if (!offer.isPresent()) {
            return false;
        }
        // do something more intelligent here, maybe block till handler is called.
        offerBookService.removeOffer(offer.get(), () -> log.info("offer removed"), (err) -> log.error("Error removing offer: " + err));
        return true;
    }

    public Optional<OfferData> getOfferDetail(String offerId) {
        if (Strings.isNullOrEmpty(offerId)) {
            return Optional.empty();
        }
        Optional<Offer> offer = offerBookService.getOffers().stream().filter(offer1 -> offerId.equals(offer1.getId())).findAny();
        if (!offer.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new OfferData(offer.get()));
    }

    public List<OfferData> getOfferList() {
        List<OfferData> offer = offerBookService.getOffers().stream().map(offer1 -> new OfferData(offer1)).collect(Collectors.toList());
        return offer;

    }

    public void offerMake(String market, String accountId, String direction, BigDecimal amount, BigDecimal minAmount,
                          String fixed, String price) {
        // TODO: detect bad direction, bad market, no paymentaccount for user

        Offer offer = new Offer(UUID.randomUUID().toString(),
                p2PService.getAddress(),
                keyRing.getPubKeyRing(),
                Offer.Direction.valueOf(direction),
                Long.valueOf(price),
                1, //marketPriceMarginParam,
                true, //useMarketBasedPrice.get(),
                amount.longValueExact(),
                minAmount.longValueExact(),
                "MR",  // currencycode
                (ArrayList<NodeAddress>) user.getAcceptedArbitratorAddresses(),
                getAccountList().getPaymentAccounts().stream().findAny().get().getPayment_method().toString(), //paymentAccount.getPaymentMethod().getId(),
                getAccountList().getPaymentAccounts().stream().findAny().get().getAccount_id(), //paymentAccount.getId(),
                null, //countryCode,
                null, //acceptedCountryCodes,
                null, //bankId,
                null, // acceptedBanks,
                priceFeedService); // priceFeedService);

        offerBookService.addOffer(offer, () -> log.info("offer removed"), (err) -> log.error("Error removing offer: " + err));
        // use openoffermanager.placeoffer instead
    }

    public WalletDetails getWalletDetails() {
        Wallet wallet = walletService.getWallet();
        if (wallet == null) {
            return null;
        }
        Coin availableBalance = wallet.getBalance(Wallet.BalanceType.AVAILABLE);
        Coin reservedBalance = wallet.getBalance(Wallet.BalanceType.ESTIMATED_SPENDABLE);
        return new WalletDetails(availableBalance.toPlainString(), reservedBalance.toPlainString());
    }

    public WalletTransactions getWalletTransactions(long start, long end, long limit) {
        boolean includeDeadTransactions = false;
        Set<Transaction> transactions = walletService.getWallet().getTransactions(includeDeadTransactions);
        WalletTransactions walletTransactions = new WalletTransactions();
        List<io.bitsquare.api.api.WalletTransaction> transactionList = walletTransactions.getTransactions();

        for (Transaction t : transactions) {
//            transactionList.add(new io.bitsquare.api.api.WalletTransaction(t.getValue(walletService.getWallet().getTransactionsByTime())))
        }
        return null;
    }

    public List<WalletAddress> getWalletAddresses() {
        return user.getPaymentAccounts().stream()
                .filter(paymentAccount -> paymentAccount instanceof CryptoCurrencyAccount)
                .map(paymentAccount -> (CryptoCurrencyAccount) paymentAccount)
                .map(paymentAccount -> new WalletAddress(((CryptoCurrencyAccount) paymentAccount).getId(), paymentAccount.getPaymentMethod().toString(), ((CryptoCurrencyAccount) paymentAccount).getAddress()))
                .collect(Collectors.toList());
    }
}
