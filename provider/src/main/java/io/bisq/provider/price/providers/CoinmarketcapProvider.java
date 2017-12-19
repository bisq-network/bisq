package io.bisq.provider.price.providers;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.network.http.HttpClient;
import io.bisq.provider.price.PriceData;
import io.bisq.provider.price.PriceRequestService;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Double.parseDouble;

public class CoinmarketcapProvider {
    private final Set<String> supportedAltcoins;

    private final HttpClient httpClient;

    public CoinmarketcapProvider() {
        this.httpClient = new HttpClient("https://api.coinmarketcap.com/");
        supportedAltcoins = CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                .map(TradeCurrency::getCode)
                .collect(Collectors.toSet());
    }

    public Map<String, PriceData> request() throws IOException {
        Map<String, PriceData> marketPriceMap = new HashMap<>();
        String response = httpClient.requestWithGET("v1/ticker/?limit=200", "User-Agent", "");
        //noinspection unchecked
        List<LinkedTreeMap<String, Object>> list = new Gson().fromJson(response, ArrayList.class);
        long ts = Instant.now().getEpochSecond();
        list.stream().forEach(treeMap -> {
            String code = (String) treeMap.get("symbol");
            if (supportedAltcoins.contains(code)) {
                double price_btc = parseDouble((String) treeMap.get("price_btc"));
                marketPriceMap.put(code, new PriceData(code, price_btc, ts, PriceRequestService.COINMKTC_PROVIDER));
            }
        });
        return marketPriceMap;
    }
}
