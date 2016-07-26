package io.bitsquare.btc.pricefeed.providers;

import io.bitsquare.btc.HttpClientProvider;
import io.bitsquare.btc.pricefeed.MarketPrice;
import io.bitsquare.http.HttpClient;
import io.bitsquare.http.HttpException;
import io.bitsquare.user.Preferences;

import java.io.IOException;
import java.util.Map;

public abstract class PriceProvider extends HttpClientProvider {

    public PriceProvider(HttpClient httpClient, Preferences preferences, String baseUrl, boolean ignoreSocks5Proxy) {
        super(httpClient, preferences, baseUrl, ignoreSocks5Proxy);
    }

    abstract public Map<String, MarketPrice> getAllPrices() throws IOException, HttpException;

    abstract public MarketPrice getPrice(String currencyCode) throws IOException, HttpException;
}
