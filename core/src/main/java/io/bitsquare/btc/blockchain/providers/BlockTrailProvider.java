package io.bitsquare.btc.blockchain.providers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.bitsquare.app.Log;
import io.bitsquare.btc.blockchain.HttpClient;
import io.bitsquare.btc.blockchain.HttpException;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BlockTrailProvider implements BlockchainApiProvider {
    private static final Logger log = LoggerFactory.getLogger(BlockTrailProvider.class);

    private final HttpClient httpClient;

    public BlockTrailProvider() {
        httpClient = new HttpClient("https://www.blocktrail.com/BTC/json/blockchain/tx/");
    }

    @Override
    public Coin getFee(String transactionId) throws IOException, HttpException {
        Log.traceCall("transactionId=" + transactionId);
        try {
            JsonObject asJsonObject = new JsonParser()
                    .parse(httpClient.requestWithGET(transactionId))
                    .getAsJsonObject();
            return Coin.valueOf(asJsonObject
                    .get("fee")
                    .getAsLong());
        } catch (IOException | HttpException e) {
            log.debug("Error at requesting transaction data from block explorer " + httpClient + "\n" +
                    "Error =" + e.getMessage());
            throw e;
        }
    }

    @Override
    public String toString() {
        return "BlockTrailProvider{" +
                '}';
    }
}
