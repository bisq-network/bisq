package io.bitsquare.btc.pricefeed.providers;

import io.bitsquare.btc.pricefeed.MarketPrice;
import io.bitsquare.http.HttpException;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

// https://api.bitfinex.com/v1/pubticker/BTCUSD
public interface PriceProvider extends Serializable {
    Map<String, MarketPrice> getAllPrices() throws IOException, HttpException;

    MarketPrice getPrice(String currencyCode) throws IOException, HttpException;
}
