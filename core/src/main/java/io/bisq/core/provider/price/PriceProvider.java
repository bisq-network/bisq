package io.bisq.core.provider.price;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import io.bisq.common.app.Version;
import io.bisq.common.util.MathUtils;
import io.bisq.common.util.Tuple2;
import io.bisq.core.provider.HttpClientProvider;
import io.bisq.network.http.HttpClient;
import io.bisq.network.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PriceProvider extends HttpClientProvider {
    private static final Logger log = LoggerFactory.getLogger(PriceProvider.class);

    public PriceProvider(HttpClient httpClient, String baseUrl) {
        super(httpClient, baseUrl, false);
    }

    public Tuple2<Map<String, Long>, Map<String, MarketPrice>> getAll() throws IOException, HttpException {
        Map<String, MarketPrice> marketPriceMap = new HashMap<>();
        String json = httpClient.requestWithGET("getAllMarketPrices", "User-Agent", "bisq/"
                + Version.VERSION + ", uid:" + httpClient.getUid());
        LinkedTreeMap<String, Object> map = new Gson().fromJson(json, LinkedTreeMap.class);
        Map<String, Long> tsMap = new HashMap<>();
        tsMap.put("btcAverageTs", ((Double) map.get("btcAverageTs")).longValue());
        tsMap.put("poloniexTs", ((Double) map.get("poloniexTs")).longValue());
        tsMap.put("coinmarketcapTs", ((Double) map.get("coinmarketcapTs")).longValue());

        List<LinkedTreeMap<String, Object>> list = (ArrayList<LinkedTreeMap<String, Object>>) map.get("data");
        list.stream().forEach(treeMap -> {
            try {
                final String currencyCode = (String) treeMap.get("currencyCode");
                final double price = (double) treeMap.get("price");
                // json uses double for our timestampSec long value...
                final long timestampSec = MathUtils.doubleToLong((double) treeMap.get("timestampSec"));
                marketPriceMap.put(currencyCode, new MarketPrice(currencyCode, price, timestampSec));
            } catch (Throwable t) {
                log.error(t.toString());
                t.printStackTrace();
            }

        });
        return new Tuple2<>(tsMap, marketPriceMap);
    }

    @Override
    public String toString() {
        return "PriceProvider";
    }
}
