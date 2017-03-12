/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.provider.fee;

import io.bisq.common.util.Utilities;
import io.bisq.http.HttpException;
import io.bisq.messages.btc.provider.fee.FeeService;
import io.bisq.provider.fee.providers.BtcFeesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

//TODO use protobuffer instead of json
public class FeeRequestService {
    private static final Logger log = LoggerFactory.getLogger(FeeRequestService.class);

    private static final long INTERVAL_BTC_FEES_MS = 600_000;      // 10 min  

    private final Timer timerBitcoinFeesLocal = new Timer();

    private final BtcFeesProvider btcFeesProvider;
    private final Map<String, Long> allFeesMap = new ConcurrentHashMap<>();
    private long bitcoinFeesTs;
    private String json;

    public FeeRequestService() throws IOException {
        btcFeesProvider = new BtcFeesProvider();

        writeToJson();
        startRequests();
    }

    private void startRequests() throws IOException {
        timerBitcoinFeesLocal.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    requestBitcoinFees();
                } catch (HttpException | IOException e) {
                    log.warn(e.toString());
                    e.printStackTrace();
                }
            }
        }, INTERVAL_BTC_FEES_MS, INTERVAL_BTC_FEES_MS);


        try {
            requestBitcoinFees();
        } catch (HttpException e) {
            log.warn(e.toString());
            e.printStackTrace();
        }
    }

    private void requestBitcoinFees() throws IOException, HttpException {
        long ts = System.currentTimeMillis();
        long result = btcFeesProvider.getFee();
        log.info("requestBitcoinFees took {} ms.", (System.currentTimeMillis() - ts));
        if (result < FeeService.MIN_TX_FEE) {
            log.warn("Response for fee is lower as min fee. Fee=" + result);
        } else if (result > FeeService.MAX_TX_FEE) {
            log.warn("Response for fee is larger as max fee. Fee=" + result);
        } else {
            bitcoinFeesTs = Instant.now().getEpochSecond();
            allFeesMap.put("txFee", result);
            writeToJson();
        }
    }

    private void writeToJson() {
        Map<String, Object> map = new HashMap<>();
        map.put("bitcoinFeesTs", bitcoinFeesTs);
        map.put("data", allFeesMap);
        json = Utilities.objectToJson(map);
    }

    public String getJson() {
        return json;
    }
}
