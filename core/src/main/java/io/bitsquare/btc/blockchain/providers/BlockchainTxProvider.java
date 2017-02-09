package io.bitsquare.btc.blockchain.providers;

import io.bitsquare.messages.btc.provider.HttpClientProvider;
import io.bitsquare.http.HttpClient;
import io.bitsquare.http.HttpException;
import org.bitcoinj.core.Coin;

import java.io.IOException;

public abstract class BlockchainTxProvider extends HttpClientProvider {
    public BlockchainTxProvider(HttpClient httpClient, String baseUrl) {
        super(httpClient, baseUrl);
    }

    public abstract Coin getFee(String transactionId) throws IOException, HttpException;
}
