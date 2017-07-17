package io.bisq.provider.fee.providers;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import io.bisq.common.util.MathUtils;
import io.bisq.network.http.HttpClient;
import io.bisq.provider.fee.FeeRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;

//TODO use protobuffer instead of json
public class BtcFeesProvider {
    private static final Logger log = LoggerFactory.getLogger(BtcFeesProvider.class);

    private final HttpClient httpClient;

    // other: https://estimatefee.com/n/2
    public BtcFeesProvider() {
        this.httpClient = new HttpClient("https://bitcoinfees.21.co/api/v1/fees/");
    }

    public Long getFee() throws IOException {
        // prev. used:  https://bitcoinfees.21.co/api/v1/fees/recommended
        // but was way too high

        //https://bitcoinfees.21.co/api/v1/fees/list
        String response = httpClient.requestWithGET("list", "User-Agent", "");
        log.info("Get recommended fee response:  " + response);

        LinkedTreeMap<String, ArrayList<LinkedTreeMap<String, Double>>> treeMap = new Gson().fromJson(response, LinkedTreeMap.class);
        final long[] fee = new long[1];
        // we want a fee which is at least in 10 blocks in (21.co estimation seem to be way too high, so we get 
        // prob much faster in
        int maxBlocks = 10;
        treeMap.entrySet().stream()
                .flatMap(e -> e.getValue().stream())
                .forEach(e -> {
                    Double maxDelay = e.get("maxDelay");
                    if (maxDelay <= maxBlocks && fee[0] == 0)
                        fee[0] = MathUtils.roundDoubleToLong(e.get("maxFee"));
                });
        fee[0] = Math.min(Math.max(fee[0], FeeRequestService.BTC_MIN_TX_FEE), FeeRequestService.BTC_MAX_TX_FEE);
        log.info("fee " + fee[0]);
        return fee[0];
    }
}
