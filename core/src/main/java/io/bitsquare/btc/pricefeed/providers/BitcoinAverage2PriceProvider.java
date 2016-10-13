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

// Using new BitcoinAverage API but we wait for an optimisation as the call contains much more data as we need and 
// the json is about 300kb instead od 30 kb in the old API.
public class BitcoinAverage2PriceProvider extends PriceProvider {
    private static final Logger log = LoggerFactory.getLogger(BitcoinAverage2PriceProvider.class);

    @Inject
    public BitcoinAverage2PriceProvider(HttpClient httpClient, Preferences preferences) {
        super(httpClient, preferences, "https://apiv2.bitcoinaverage.com/indices/global/ticker/", false);
    }

    @Override
    public Map<String, MarketPrice> getAllPrices() throws IOException, HttpException {
        Map<String, MarketPrice> marketPriceMap = new HashMap<>();
        LinkedTreeMap<String, Object> treeMap = new Gson().<LinkedTreeMap<String, Object>>fromJson(httpClient.requestWithGET("all"), LinkedTreeMap.class);
        Map<String, String> temp = new HashMap<>();
        treeMap.entrySet().stream().forEach(e -> {
            Object value = e.getValue();

            // We need to check the type as we get an unexpected "timestamp" object at the end: 
            if (value instanceof LinkedTreeMap) {
                LinkedTreeMap<String, Object> treeMap2 = (LinkedTreeMap) value;
                temp.clear();
                treeMap2.entrySet().stream().forEach(e2 -> temp.put(e2.getKey(), e2.getValue().toString()));
                String key = e.getKey().replace("BTC", "");
                marketPriceMap.put(key,
                        new MarketPrice(key, temp.get("ask"), temp.get("bid"), temp.get("last")));
            }
        });
        return marketPriceMap;
    }

    @Override
    public MarketPrice getPrice(String currencyCode) throws IOException, HttpException {
        //Log.traceCall("currencyCode=" + currencyCode);
        JsonObject jsonObject = new JsonParser()
                .parse(httpClient.requestWithGET("BTC" + currencyCode))
                .getAsJsonObject();
        return new MarketPrice(currencyCode,
                jsonObject.get("ask").getAsString(),
                jsonObject.get("bid").getAsString(),
                jsonObject.get("last").getAsString());
    }

    @Override
    public String toString() {
        return "BitcoinAveragePriceProvider";
    }
}
