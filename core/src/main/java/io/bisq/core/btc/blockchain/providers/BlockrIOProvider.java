package io.bisq.core.btc.blockchain.providers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.bisq.common.app.Log;
import io.bisq.network.http.HttpClient;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;

public class BlockrIOProvider extends BlockchainTxProvider {
    private static final Logger log = LoggerFactory.getLogger(BlockrIOProvider.class);

    @Inject
    public BlockrIOProvider(HttpClient httpClient) {
        super(httpClient, "https://btc.blockr.io/api/v1/tx/info/");
    }

    @Override
    public Coin getFee(String transactionId) throws IOException {
        Log.traceCall("transactionId=" + transactionId);
        try {
            JsonObject data = new JsonParser()
                    .parse(httpClient.requestWithGET(transactionId, "User-Agent", ""))
                    .getAsJsonObject()
                    .get("data")
                    .getAsJsonObject();
            return Coin.parseCoin(data
                    .get("fee")
                    .getAsString());
        } catch (IOException e) {
            log.debug("Error at requesting transaction data from block explorer " + httpClient + "\n" +
                    "Error =" + e.getMessage());
            throw e;
        }
    }

    @Override
    public String toString() {
        return "BlockrIOProvider{" +
                '}';
    }
}
