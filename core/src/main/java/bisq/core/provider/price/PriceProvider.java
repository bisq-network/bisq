/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.provider.price;

import bisq.core.provider.HttpClientProvider;

import bisq.network.http.HttpClient;

import bisq.common.app.Version;
import bisq.common.util.MathUtils;
import bisq.common.util.Tuple2;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PriceProvider extends HttpClientProvider {

    // Do not use Guice here as we might create multiple instances
    public PriceProvider(HttpClient httpClient, String baseUrl) {
        super(httpClient, baseUrl, false);
    }

    public Tuple2<Map<String, Long>, Map<String, MarketPrice>> getAll() throws IOException {
        Map<String, MarketPrice> marketPriceMap = new HashMap<>();
        String json = httpClient.requestWithGET("getAllMarketPrices", "User-Agent", "bisq/"
                + Version.VERSION + ", uid:" + httpClient.getUid());

        LinkedTreeMap<?, ?> map = new Gson().fromJson(json, LinkedTreeMap.class);
        Map<String, Long> tsMap = new HashMap<>();
        tsMap.put("btcAverageTs", ((Double) map.get("btcAverageTs")).longValue());
        tsMap.put("poloniexTs", ((Double) map.get("poloniexTs")).longValue());
        tsMap.put("coinmarketcapTs", ((Double) map.get("coinmarketcapTs")).longValue());

        List<?> list = (ArrayList<?>) map.get("data");
        list.forEach(obj -> {
            try {
                LinkedTreeMap<?, ?> treeMap = (LinkedTreeMap<?, ?>) obj;
                final String currencyCode = (String) treeMap.get("currencyCode");
                final double price = (Double) treeMap.get("price");
                // json uses double for our timestampSec long value...
                final long timestampSec = MathUtils.doubleToLong((Double) treeMap.get("timestampSec"));
                marketPriceMap.put(currencyCode, new MarketPrice(currencyCode, price, timestampSec, true));
            } catch (Throwable t) {
                log.error(t.toString());
                t.printStackTrace();
            }

        });
        return new Tuple2<>(tsMap, marketPriceMap);
    }

    public String getBaseUrl() {
        return httpClient.getBaseUrl();
    }
}
