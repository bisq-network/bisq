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

package bisq.core.provider.fee;

import bisq.core.provider.FeeHttpClient;
import bisq.core.provider.HttpClientProvider;
import bisq.core.provider.ProvidersRepository;

import bisq.network.http.HttpClient;

import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.util.Tuple2;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import com.google.inject.Inject;

import java.io.IOException;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeeProvider extends HttpClientProvider {

    @Inject
    public FeeProvider(FeeHttpClient httpClient, ProvidersRepository providersRepository) {
        super(httpClient, providersRepository.getBaseUrl(), false);
    }

    public Tuple2<Map<String, Long>, Map<String, Long>> getFees() throws IOException {
        String json = httpClient.get("getFees", "User-Agent", "bisq/" + Version.VERSION);

        LinkedTreeMap<?, ?> linkedTreeMap = new Gson().fromJson(json, LinkedTreeMap.class);
        Map<String, Long> tsMap = new HashMap<>();
        tsMap.put(Config.BTC_FEES_TS, ((Double) linkedTreeMap.get(Config.BTC_FEES_TS)).longValue());

        Map<String, Long> map = new HashMap<>();

        try {
            LinkedTreeMap<?, ?> dataMap = (LinkedTreeMap<?, ?>) linkedTreeMap.get("dataMap");
            Long btcTxFee = ((Double) dataMap.get(Config.BTC_TX_FEE)).longValue();
            Long btcMinTxFee = dataMap.get(Config.BTC_MIN_TX_FEE) != null ?
                    ((Double) dataMap.get(Config.BTC_MIN_TX_FEE)).longValue() : Config.baseCurrencyNetwork().getDefaultMinFeePerVbyte();

            map.put(Config.BTC_TX_FEE, btcTxFee);
            map.put(Config.BTC_MIN_TX_FEE, btcMinTxFee);
        } catch (Throwable t) {
            log.error(t.toString());
            t.printStackTrace();
        }
        return new Tuple2<>(tsMap, map);
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }
}
