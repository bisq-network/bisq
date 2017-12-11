package io.bisq.provider.fee.providers;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import io.bisq.common.util.MathUtils;
import io.bisq.network.http.HttpClient;
import io.bisq.provider.fee.FeeRequestService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

//TODO consider alternative https://www.bitgo.com/api/v1/tx/fee?numBlocks=3
@Slf4j
public class BtcFeesProvider {
    public static int CAPACITY = 4; // if we request each 5 min. we take average of last 20 min.
    public static int MAX_BLOCKS = 10;

    private final HttpClient httpClient;
    LinkedList<Long> fees = new LinkedList<>();
    private final int capacity;
    private final int maxBlocks;

    // other: https://estimatefee.com/n/2
    public BtcFeesProvider(int capacity, int maxBlocks) {
        this.capacity = capacity;
        this.maxBlocks = maxBlocks;
        this.httpClient = new HttpClient("https://bitcoinfees.earn.com/api/v1/fees/");
    }

    public Long getFee() throws IOException {
        // prev. used:  https://bitcoinfees.earn.com/api/v1/fees/recommended
        // but was way too high

        // https://bitcoinfees.earn.com/api/v1/fees/list
        String response = httpClient.requestWithGET("list", "User-Agent", "");
        log.info("Get recommended fee response:  " + response);

        LinkedTreeMap<String, ArrayList<LinkedTreeMap<String, Double>>> treeMap = new Gson().fromJson(response, LinkedTreeMap.class);
        final long[] fee = new long[1];
        // we want a fee which is at least in 20 blocks in (21.co estimation seem to be way too high, so we get
        // prob much faster in
        treeMap.entrySet().stream()
                .flatMap(e -> e.getValue().stream())
                .forEach(e -> {
                    Double maxDelay = e.get("maxDelay");
                    if (maxDelay <= maxBlocks && fee[0] == 0)
                        fee[0] = MathUtils.roundDoubleToLong(e.get("maxFee"));
                });
        fee[0] = Math.min(Math.max(fee[0], FeeRequestService.BTC_MIN_TX_FEE), FeeRequestService.BTC_MAX_TX_FEE);

        return getAverage(fee[0]);
    }

    // We take the average of the last 12 calls (every 5 minute) so we smooth extreme values.
    // We observed very radical jumps in the fee estimations, so that should help to avoid that.
    long getAverage(long newFee) {
        log.info("new fee " + newFee);
        fees.add(newFee);
        long average = ((Double) fees.stream().mapToDouble(e -> e).average().getAsDouble()).longValue();
        log.info("average of last {} calls: {}", fees.size(), average);
        if (fees.size() == capacity)
            fees.removeFirst();

        return average;
    }
}
