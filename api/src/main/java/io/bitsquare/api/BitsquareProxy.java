package io.bitsquare.api;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import io.bitsquare.api.api.*;
import io.bitsquare.btc.WalletService;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OfferBookService;
import io.bitsquare.user.User;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Wallet;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

    public BitsquareProxy(WalletService walletService, TradeManager tradeManager, OfferBookService offerBookService,
                          User user) {
        this.walletService = walletService;
        this.tradeManager = tradeManager;
        this.offerBookService = offerBookService;
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

    public WalletDetails getWalletDetails() {
        Wallet wallet = walletService.getWallet();
        if (wallet == null) {
            return null;
        }
        Coin availableBalance = wallet.getBalance(Wallet.BalanceType.AVAILABLE);
        Coin reservedBalance = wallet.getBalance(Wallet.BalanceType.ESTIMATED_SPENDABLE);
        return new WalletDetails(availableBalance.longValue(), reservedBalance.longValue());
    }

//    public WalletTransactions getWalletTransactions(long start, long end, long limit) {
//        boolean includeDeadTransactions = false;
//        Set<org.bitcoinj.core.Transaction> transactions = walletService.getWallet().getTransactions(includeDeadTransactions);
//        WalletTransactions walletTransactions = new WalletTransactions();
//        List<io.bitsquare.api.api.WalletTransaction> transactionList = walletTransactions.getTransactions();
//
//        for (Transaction t : transactions) {
//            transactionList.add(new io.bitsquare.api.api.WalletTransaction(t.getValue(walletService.getWallet().getTransactionsByTime())))
//        }
//    }

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
        offerBookService.removeOffer(offer.get(), () -> log.info("offer removed"), (err) -> log.error("Error removing offer" + err));
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
    public void offerMake() {
//        offerbookservice. public void addOffer(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {

    }
}
