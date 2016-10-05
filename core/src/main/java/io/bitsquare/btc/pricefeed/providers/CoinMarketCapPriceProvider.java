package io.bitsquare.btc.pricefeed.providers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;
import io.bitsquare.btc.pricefeed.MarketPrice;
import io.bitsquare.http.HttpClient;
import io.bitsquare.http.HttpException;
import io.bitsquare.user.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CoinMarketCapPriceProvider extends PriceProvider {
    private static final Logger log = LoggerFactory.getLogger(CoinMarketCapPriceProvider.class);

    @Inject
    public CoinMarketCapPriceProvider(HttpClient httpClient, Preferences preferences) {
        super(httpClient, preferences, "https://api.coinmarketcap.com/v1/ticker/", false);
    }

    @Override
    public Map<String, MarketPrice> getAllPrices() throws IOException, HttpException {
        Map<String, MarketPrice> marketPriceMap = new HashMap<>();
        LinkedTreeMap<String, Object> treeMap = new Gson().<LinkedTreeMap<String, 
                              Object>>fromJson(httpClient.requestWithGET(""), LinkedTreeMap.class);
        Map<String, String> temp = new HashMap<>();
        treeMap.entrySet().stream().forEach(e -> {
            Object value = e.getValue();

            // We need to check the type as we get an unexpected "timestamp" object at the end: 
            if (value instanceof LinkedTreeMap) {
                LinkedTreeMap<String, Object> treeMap2 = (LinkedTreeMap) value;
                temp.clear();
                treeMap2.entrySet().stream().forEach(e2 -> temp.put(e2.getKey(),
                                                        e2.getValue().toString()));
                marketPriceMap.put(e.getKey(), new MarketPrice(e.getKey(), 
                                               temp.get("price_btc"), 
                                               temp.get("price_btc"), 
                                               temp.get("price_btc")));
            }
        });
        return marketPriceMap;
    }

    @Override
    public MarketPrice getPrice(String currencyCode) throws IOException, HttpException {
        //Log.traceCall("currencyCode=" + currencyCode);
        JsonObject jsonObject = new JsonParser()
                .parse(httpClient.requestWithGET(currencyCode))
                .getAsJsonObject();
        return new MarketPrice(currencyCode,
                jsonObject.get("ask").getAsString(),
                jsonObject.get("bid").getAsString(),
                jsonObject.get("last").getAsString());
    }

    @Override
    public String toString() {
        return "CoinMarketCapPriceProvider";
    }
}
