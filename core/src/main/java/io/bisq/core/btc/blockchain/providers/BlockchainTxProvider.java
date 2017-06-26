package io.bisq.core.btc.blockchain.providers;

import io.bisq.core.provider.HttpClientProvider;
import io.bisq.network.http.HttpClient;
import org.bitcoinj.core.Coin;

import java.io.IOException;

public abstract class BlockchainTxProvider extends HttpClientProvider {
    public BlockchainTxProvider(HttpClient httpClient, String baseUrl) {
        super(httpClient, baseUrl);
    }

    public abstract Coin getFee(String transactionId) throws IOException;
}
