package io.bitsquare.btc.http;

import com.google.gson.JsonParser;
import io.bitsquare.app.Log;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

// TODO route over tor, support several providers
public class BlockrIOProvider implements BlockchainApiProvider {
    private static final Logger log = LoggerFactory.getLogger(BlockrIOProvider.class);

    private final HttpClient httpClient;

    public static void main(String[] args) throws HttpException, IOException {
        Coin fee = new BlockrIOProvider()
                .getFee("df67414652722d38b43dcbcac6927c97626a65bd4e76a2e2787e22948a7c5c47");
        log.debug("fee " + fee.toFriendlyString());
    }

    public BlockrIOProvider() {
        httpClient = new HttpClient("https://btc.blockr.io/api/v1/tx/info/");
    }

    @Override
    public Coin getFee(String transactionId) throws IOException, HttpException {
        Log.traceCall("transactionId=" + transactionId);
        try {
            return Coin.parseCoin(new JsonParser()
                    .parse(httpClient.requestWithGET(transactionId))
                    .getAsJsonObject()
                    .get("data")
                    .getAsJsonObject()
                    .get("fee")
                    .getAsString());
        } catch (IOException | HttpException e) {
            log.warn("Error at requesting transaction data from block explorer " + httpClient + "\n" +
                    "Error =" + e.getMessage());
            throw e;
        }
    }
}
