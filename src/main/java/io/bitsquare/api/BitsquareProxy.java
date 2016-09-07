package io.bitsquare.api;

import com.google.inject.Inject;
import io.bitsquare.api.api.*;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is a proxy for all bitsquare features the api will use.
 *
 * No methods/representations used in the interface layers (REST/Socket/...) should be used in this class.
 */
public class BitsquareProxy {
    @Inject
    private WalletService walletService;
    @Inject
    private User user;
    @Inject
    private TradeManager tradeManager;

    public BitsquareProxy(WalletService walletService, TradeManager tradeManager, User user) {
        this.walletService = walletService;
        this.tradeManager = tradeManager;
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

    public long getWalletDetails() {
        return walletService.getAvailableBalance().getValue();
    }

//    public WalletTransactions getWalletTransactions(long start, long end, long limit) {
//        boolean includeDeadTransactions = false;
//        Set<Transaction> transactions = walletService.getWallet().getTransactions(includeDeadTransactions);
//        WalletTransactions walletTransactions = new WalletTransactions();
//        List<io.bitsquare.api.Transaction> transactionList = walletTransactions.getTransactions();
//
//        for (Transaction t : transactions) {
//            transactionList.add(new io.bitsquare.api.Transaction(t.getValue(walletService.getWallet().getTransactionsByTime())))
//        }
//    }

    public AccountList getAccountList() {
        AccountList accountList = new AccountList();
        accountList.accounts = user.getPaymentAccounts().stream()
                .map(paymentAccount -> new Account(paymentAccount)).collect(Collectors.toSet());
        return accountList;
    }

    public void offerMake() {
//        tradeManager.
    }

}
