package io.bisq.provider.price.providers;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import io.bisq.http.HttpClient;
import io.bisq.http.HttpException;
import io.bisq.messages.locale.CurrencyUtil;
import io.bisq.messages.locale.TradeCurrency;
import io.bisq.provider.price.PriceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Double.parseDouble;

public class CoinmarketcapProvider {
    private static final Logger log = LoggerFactory.getLogger(CoinmarketcapProvider.class);
    private final Set<String> supportedAltcoins;

    private final HttpClient httpClient;

    public CoinmarketcapProvider() {
        this.httpClient = new HttpClient("https://api.coinmarketcap.com/");
        supportedAltcoins = CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                .map(TradeCurrency::getCode)
                .collect(Collectors.toSet());
    }

    public Map<String, PriceData> request() throws IOException, HttpException {
        Map<String, PriceData> marketPriceMap = new HashMap<>();
        String response = httpClient.requestWithGET("v1/ticker/?limit=200", "User-Agent", "");
        List<LinkedTreeMap<String, Object>> list = new Gson().fromJson(response, ArrayList.class);
        list.stream().forEach(treeMap -> {
            String code = (String) treeMap.get("symbol");
            if (supportedAltcoins.contains(code)) {
                double price_btc = parseDouble((String) treeMap.get("price_btc"));
                marketPriceMap.put(code, new PriceData(code, price_btc, price_btc, price_btc));
            }
        });
        return marketPriceMap;
    }
}
