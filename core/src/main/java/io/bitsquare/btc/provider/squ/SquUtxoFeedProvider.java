package io.bitsquare.btc.provider.squ;

import io.bitsquare.btc.provider.HttpClientProvider;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.http.HttpClient;
import io.bitsquare.http.HttpException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SquUtxoFeedProvider extends HttpClientProvider {
    private static final Logger log = LoggerFactory.getLogger(SquUtxoFeedProvider.class);

    public SquUtxoFeedProvider(HttpClient httpClient, String baseUrl) {
        super(httpClient, baseUrl, false);
    }

    public Tuple2<Map<String, Long>, SquUtxoFeedData> getSquUtxo() throws IOException, HttpException {
      /*  String json = httpClient.requestWithGET("getSquUtxo", "User-Agent", "Bitsquare/" + Version.VERSION + ", uid:" + httpClient.getUid());
        LinkedTreeMap<String, Object> linkedTreeMap = new Gson().fromJson(json, LinkedTreeMap.class);
        Map<String, Long> tsMap = new HashMap<>();
        tsMap.put("getSquUtxoTs", ((Double) linkedTreeMap.get("getSquUtxoTs")).longValue());

        LinkedTreeMap<String, Double> dataMap = (LinkedTreeMap<String, Double>) linkedTreeMap.get("data");
       // SquUtxoData squUtxoData = new SquUtxoData(dataMap.get("txFee").longValue(), dataMap.get("createOfferFee").longValue(), dataMap.get("takeOfferFee").longValue());
       */

        // mock
        Map<String, Long> tsMap = new HashMap<>();
        tsMap.put("getSquUtxoTs", 1L);

        Set<UTXO> utxoSet = new HashSet<>();


        UTXO newOut = new UTXO(Sha256Hash.of(Utils.HEX.decode("sdaf9usdoafisdahf;")),
                1,
                Coin.valueOf(1000),
                999,
                false,
                null,
                "address1212121");

        SquUtxoFeedData squUtxoFeedData = new SquUtxoFeedData(utxoSet);

        return new Tuple2<>(tsMap, squUtxoFeedData);
    }

    @Override
    public String toString() {
        return "SquUtxoProvider";
    }
}
