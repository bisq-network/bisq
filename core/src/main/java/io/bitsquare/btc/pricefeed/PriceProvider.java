package io.bitsquare.btc.pricefeed;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import io.bitsquare.app.Version;
import io.bitsquare.btc.HttpClientProvider;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.http.HttpClient;
import io.bitsquare.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class PriceProvider extends HttpClientProvider {
    private static final Logger log = LoggerFactory.getLogger(PriceProvider.class);
    private final String uid;

    public PriceProvider(HttpClient httpClient, String baseUrl) {
        super(httpClient, baseUrl, false);

        uid = UUID.randomUUID().toString();
    }

    public Tuple2<Map<String, Long>, Map<String, MarketPrice>> getAll() throws IOException, HttpException {
        Map<String, MarketPrice> marketPriceMap = new HashMap<>();
        String json = httpClient.requestWithGET("all", "User-Agent", "Bitsquare/" + Version.VERSION + ", uid:" + uid);
        LinkedTreeMap<String, Object> map = new Gson().fromJson(json, LinkedTreeMap.class);
        Map<String, Long> tsMap = new HashMap<>();
        tsMap.put("btcAverageTs", ((Double) map.get("btcAverageTs")).longValue());
        tsMap.put("poloniexTs", ((Double) map.get("poloniexTs")).longValue());
        tsMap.put("coinmarketcapTs", ((Double) map.get("coinmarketcapTs")).longValue());

        List<LinkedTreeMap<String, Object>> list = (ArrayList<LinkedTreeMap<String, Object>>) map.get("data");
        list.stream().forEach(treeMap -> {
            marketPriceMap.put((String) treeMap.get("c"),
                    new MarketPrice((String) treeMap.get("c"), (double) treeMap.get("a"), (double) treeMap.get("b"), (double) treeMap.get("l")));
        });
        return new Tuple2<>(tsMap, marketPriceMap);
    }

    @Override
    public String toString() {
        return "PriceProvider";
    }
}
