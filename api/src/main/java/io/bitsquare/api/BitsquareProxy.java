package io.bitsquare.api;

import com.google.inject.Inject;
import com.sun.tools.javac.util.Bits;
import io.bitsquare.btc.WalletService;
import io.bitsquare.locale.CurrencyUtil;

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

    public BitsquareProxy(WalletService walletService) {
        this.walletService = walletService;
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
}
