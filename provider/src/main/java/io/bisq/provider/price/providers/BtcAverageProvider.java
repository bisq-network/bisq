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

package io.bisq.provider.price.providers;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import io.bisq.network.http.HttpClient;
import io.bisq.provider.price.PriceData;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class BtcAverageProvider {
    private static final Logger log = LoggerFactory.getLogger(BtcAverageProvider.class);

    private final HttpClient httpClient;
    private final String pubKey;
    private final SecretKey secretKey;

    public BtcAverageProvider(String privKey, String pubKey) {
        this.httpClient = new HttpClient("https://apiv2.bitcoinaverage.com/");
        this.pubKey = pubKey;
        this.secretKey = new SecretKeySpec(privKey.getBytes(), "HmacSHA256");
    }

    private String getHeader() throws NoSuchAlgorithmException, InvalidKeyException {
        String payload = Instant.now().getEpochSecond() + "." + pubKey;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKey);
        return payload + "." + Hex.toHexString(mac.doFinal(payload.getBytes()));
    }

    public Map<String, PriceData> getLocal() throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        return getMap(httpClient.requestWithGETNoProxy("indices/local/ticker/all?crypto=BTC", "X-signature", getHeader()));
    }

    public Map<String, PriceData> getGlobal() throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        return getMap(httpClient.requestWithGETNoProxy("indices/global/ticker/all?crypto=BTC", "X-signature", getHeader()));
    }

    private Map<String, PriceData> getMap(String json) {
        Map<String, PriceData> marketPriceMap = new HashMap<>();
        LinkedTreeMap<String, Object> treeMap = new Gson().<LinkedTreeMap<String, Object>>fromJson(json, LinkedTreeMap.class);
        long ts = Instant.now().getEpochSecond();
        treeMap.entrySet().stream().forEach(e -> {
            Object value = e.getValue();
            // We need to check the type as we get an unexpected "timestamp" object at the end:
            if (value instanceof LinkedTreeMap) {
                //noinspection unchecked
                LinkedTreeMap<String, Object> data = (LinkedTreeMap) value;
                String currencyCode = e.getKey().substring(3);
                // We ignore venezuelan currency as the official exchange rate is wishful thinking only....
                // We should use that api with a custom provider: http://api.bitcoinvenezuela.com/1
                if (!("VEF".equals(currencyCode))) {
                    try {
                        final Object lastAsObject = data.get("last");
                        double last = 0;
                        if (lastAsObject instanceof String)
                            last = Double.valueOf((String) lastAsObject);
                        else if (lastAsObject instanceof Double)
                            last = (double) lastAsObject;
                        else
                            log.warn("Unexpected data type: lastAsObject=" + lastAsObject);

                        marketPriceMap.put(currencyCode,
                                new PriceData(currencyCode,
                                        last,
                                        ts));
                    } catch (Throwable exception) {
                        log.error("Error converting btcaverage data: " + currencyCode, exception);
                    }
                }
            }
        });
        return marketPriceMap;
    }
}
