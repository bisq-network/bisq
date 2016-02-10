package io.bitsquare.btc.blockchain.providers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.bitsquare.app.Log;
import io.bitsquare.http.HttpClient;
import io.bitsquare.http.HttpException;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BlockrIOProvider implements FeeProvider {
    private static final Logger log = LoggerFactory.getLogger(BlockrIOProvider.class);

    private final HttpClient httpClient;

    public BlockrIOProvider() {
        httpClient = new HttpClient("https://btc.blockr.io/api/v1/tx/info/");
    }

    //https://api.bitcoinaverage.com/ticker/global/EUR/
    @Override
    public Coin getFee(String transactionId) throws IOException, HttpException {
        Log.traceCall("transactionId=" + transactionId);
        try {
            JsonObject data = new JsonParser()
                    .parse(httpClient.requestWithGET(transactionId))
                    .getAsJsonObject()
                    .get("data")
                    .getAsJsonObject();
            return Coin.parseCoin(data
                    .get("fee")
                    .getAsString());
        } catch (IOException | HttpException e) {
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
