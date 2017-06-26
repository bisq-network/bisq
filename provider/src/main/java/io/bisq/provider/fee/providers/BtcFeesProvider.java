package io.bisq.provider.fee.providers;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import io.bisq.network.http.HttpClient;
import io.bisq.provider.fee.FeeRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//TODO use protobuffer instead of json
public class BtcFeesProvider {
    private static final Logger log = LoggerFactory.getLogger(BtcFeesProvider.class);

    private final HttpClient httpClient;

    // other: https://estimatefee.com/n/2
    public BtcFeesProvider() {
        this.httpClient = new HttpClient("https://bitcoinfees.21.co/api/v1/fees/");
    }

    public Long getFee() throws IOException {
        String response = httpClient.requestWithGET("recommended", "User-Agent", "");
        log.info("Get recommended fee response:  " + response);
        Map<String, Long> map = new HashMap<>();
        //noinspection unchecked
        LinkedTreeMap<String, Double> treeMap = new Gson().fromJson(response, LinkedTreeMap.class);
        treeMap.entrySet().stream().forEach(e -> map.put(e.getKey(), e.getValue().longValue()));

        if (map.get("fastestFee") < FeeRequestService.BTC_MAX_TX_FEE)
            return map.get("fastestFee");
        else if (map.get("halfHourFee") < FeeRequestService.BTC_MAX_TX_FEE)
            return map.get("halfHourFee");
        else if (map.get("hourFee") < FeeRequestService.BTC_MAX_TX_FEE)
            return map.get("hourFee");
        else
            return FeeRequestService.BTC_MAX_TX_FEE;
    }
}
