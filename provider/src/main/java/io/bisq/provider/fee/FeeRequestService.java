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

package io.bisq.provider.fee;

import io.bisq.common.util.Utilities;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.provider.fee.providers.BtcFeesProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class FeeRequestService {
    public static int REQUEST_INTERVAL_MIN = 5;

    public static final long BTC_MIN_TX_FEE = 10; // satoshi/byte
    public static final long BTC_MAX_TX_FEE = 1000;

    private final Timer timerBitcoinFeesLocal = new Timer();

    private final BtcFeesProvider btcFeesProvider;
    private final Map<String, Long> dataMap = new ConcurrentHashMap<>();
    private long bitcoinFeesTs;
    private String json;

    public FeeRequestService(int capacity, int maxBlocks, long requestIntervalInMs) throws IOException {
        btcFeesProvider = new BtcFeesProvider(capacity, maxBlocks);

        // For now we don't need a fee estimation for LTC so we set it fixed, but we keep it in the provider to
        // be flexible if fee pressure grows on LTC
        dataMap.put("ltcTxFee", FeeService.LTC_DEFAULT_TX_FEE);
        dataMap.put("dogeTxFee", FeeService.DOGE_DEFAULT_TX_FEE);
        dataMap.put("dashTxFee", FeeService.DASH_DEFAULT_TX_FEE);

        writeToJson();
        startRequests(requestIntervalInMs);
    }

    private void startRequests(long requestIntervalInMs) throws IOException {
        timerBitcoinFeesLocal.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    requestBitcoinFees();
                } catch (IOException e) {
                    log.warn(e.toString());
                    e.printStackTrace();
                }
            }
        }, requestIntervalInMs, requestIntervalInMs);


        requestBitcoinFees();
    }

    private void requestBitcoinFees() throws IOException {
        long ts = System.currentTimeMillis();
        long btcFee = btcFeesProvider.getFee();
        log.info("requestBitcoinFees took {} ms.", (System.currentTimeMillis() - ts));
        if (btcFee < FeeRequestService.BTC_MIN_TX_FEE) {
            log.warn("Response for fee is lower as min fee. Fee=" + btcFee);
        } else if (btcFee > FeeRequestService.BTC_MAX_TX_FEE) {
            log.warn("Response for fee is larger as max fee. Fee=" + btcFee);
        } else {
            bitcoinFeesTs = Instant.now().getEpochSecond();
            dataMap.put("btcTxFee", btcFee);
            writeToJson();
        }
    }

    private void writeToJson() {
        Map<String, Object> map = new HashMap<>();
        map.put("bitcoinFeesTs", bitcoinFeesTs);
        map.put("dataMap", dataMap);
        json = Utilities.objectToJson(map);
    }

    public String getJson() {
        return json;
    }
}
