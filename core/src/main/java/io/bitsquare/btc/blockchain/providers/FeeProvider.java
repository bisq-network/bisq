package io.bitsquare.btc.blockchain.providers;

import io.bitsquare.btc.HttpClientProvider;
import io.bitsquare.http.HttpClient;
import io.bitsquare.http.HttpException;
import org.bitcoinj.core.Coin;

import java.io.IOException;

public abstract class FeeProvider extends HttpClientProvider {
    public FeeProvider(HttpClient httpClient, String baseUrl) {
        super(httpClient, baseUrl);
    }

    public abstract Coin getFee(String transactionId) throws IOException, HttpException;
}
