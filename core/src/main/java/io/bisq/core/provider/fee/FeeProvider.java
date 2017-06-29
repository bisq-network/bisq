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
package io.bisq.core.provider.fee;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import io.bisq.common.app.Version;
import io.bisq.common.util.Tuple2;
import io.bisq.core.provider.HttpClientProvider;
import io.bisq.network.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//TODO use protobuffer instead of json
public class FeeProvider extends HttpClientProvider {
    private static final Logger log = LoggerFactory.getLogger(FeeProvider.class);

    public FeeProvider(HttpClient httpClient, String baseUrl) {
        super(httpClient, baseUrl, false);
    }

    public Tuple2<Map<String, Long>, Map<String, Long>> getFees() throws IOException {
        String json = httpClient.requestWithGET("getFees", "User-Agent", "bisq/" + Version.VERSION + ", uid:" + httpClient.getUid());
        //noinspection unchecked
        LinkedTreeMap<String, Object> linkedTreeMap = new Gson().fromJson(json, LinkedTreeMap.class);
        Map<String, Long> tsMap = new HashMap<>();
        tsMap.put("bitcoinFeesTs", ((Double) linkedTreeMap.get("bitcoinFeesTs")).longValue());

        Map<String, Long> map = new HashMap<>();

        try {
            //noinspection unchecked
            LinkedTreeMap<String, Double> dataMap = (LinkedTreeMap<String, Double>) linkedTreeMap.get("dataMap");
            Long btcTxFee = dataMap.get("btcTxFee").longValue();
            Long ltcTxFee = dataMap.get("ltcTxFee").longValue();
            Long dogeTxFee = dataMap.get("dogeTxFee").longValue();

            map.put("BTC", btcTxFee);
            map.put("LTC", ltcTxFee);
            map.put("DOGE", dogeTxFee);
        } catch (Throwable t) {
            log.error(t.toString());
            t.printStackTrace();
        }
        return new Tuple2<>(tsMap, map);
    }

    @Override
    public String toString() {
        return "FeeProvider";
    }
}
