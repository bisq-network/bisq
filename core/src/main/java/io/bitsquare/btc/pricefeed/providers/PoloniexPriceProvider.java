package io.bitsquare.btc.pricefeed.providers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;
import io.bitsquare.btc.pricefeed.MarketPrice;
import io.bitsquare.http.HttpClient;
import io.bitsquare.http.HttpException;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.network.Socks5ProxyProvider;
import io.bitsquare.user.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PoloniexPriceProvider extends PriceProvider {
    private static final Logger log = LoggerFactory.getLogger(PoloniexPriceProvider.class);

    @Inject
    public PoloniexPriceProvider(HttpClient httpClient, Preferences preferences, Socks5ProxyProvider socks5ProxyProvider) {
        // Poloniex uses Cloudflare which requires a captcha if they get connected from a Tor exit node.
        // We can't use Tor for Poloniex for that reason and set the ignoreSocks5Proxy flag to true if no 
        // custom socks5ProxyHttp is set.
        super(httpClient, preferences, "https://poloniex.com/public", socks5ProxyProvider.getSocks5ProxyHttp() == null);
    }

    @Override
    public Map<String, MarketPrice> getAllPrices() throws IOException, HttpException {
        Map<String, MarketPrice> marketPriceMap = new HashMap<>();
        String response = httpClient.requestWithGET("?command=returnTicker");
        LinkedTreeMap<String, Object> treeMap = new Gson().fromJson(response, LinkedTreeMap.class);
        Map<String, String> temp = new HashMap<>();
        Set<String> supported = CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                .map(TradeCurrency::getCode)
                .collect(Collectors.toSet());
        treeMap.entrySet().stream().forEach(e -> {
            Object value = e.getValue();
            String currencyPair = e.getKey();
            String otherCurrency = null;
            if (currencyPair.startsWith("BTC")) {
                String[] tokens = currencyPair.split("_");
                if (tokens.length > 1) {
                    otherCurrency = tokens[1];
                    if (supported.contains(otherCurrency)) {
                        if (value instanceof LinkedTreeMap) {
                            LinkedTreeMap<String, Object> treeMap2 = (LinkedTreeMap) value;
                            temp.clear();
                            treeMap2.entrySet().stream().forEach(e2 -> temp.put(e2.getKey(), e2.getValue().toString()));
                            marketPriceMap.put(otherCurrency,
                                    new MarketPrice(otherCurrency, temp.get("lowestAsk"), temp.get("highestBid"), temp.get("last")));
                        }
                    }
                }

            }
        });
        return marketPriceMap;
    }

    @Override
    public MarketPrice getPrice(String currencyCode) throws IOException, HttpException {
        // Log.traceCall("currencyCode=" + currencyCode);
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
        return "PoloniexPriceProvider";
    }
}
