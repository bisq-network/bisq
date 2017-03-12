package io.bisq.btc.blockchain.providers;

import io.bisq.http.HttpClient;
import io.bisq.http.HttpException;
import io.bisq.messages.btc.provider.HttpClientProvider;
import org.bitcoinj.core.Coin;

import java.io.IOException;

public abstract class BlockchainTxProvider extends HttpClientProvider {
    public BlockchainTxProvider(HttpClient httpClient, String baseUrl) {
        super(httpClient, baseUrl);
    }

    public abstract Coin getFee(String transactionId) throws IOException, HttpException;
}
