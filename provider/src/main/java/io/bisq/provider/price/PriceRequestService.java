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

package io.bisq.provider.price;

import io.bisq.common.util.Utilities;
import io.bisq.provider.price.providers.BtcAverageProvider;
import io.bisq.provider.price.providers.CoinmarketcapProvider;
import io.bisq.provider.price.providers.PoloniexProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PriceRequestService {
    private static final Logger log = LoggerFactory.getLogger(PriceRequestService.class);

    // We adjust request time to fit into BitcoinAverage developer plan (45k request per month).
    // We get 42514 (29760+12754) request with below numbers.
    private static final long INTERVAL_BTC_AV_LOCAL_MS = 90_000;      // 90 sec; 29760 requests for 31 days
    private static final long INTERVAL_BTC_AV_GLOBAL_MS = 210_000;    // 3.5 min; 12754 requests for 31 days

    private static final long INTERVAL_POLONIEX_MS = 60_000;          // 1 min
    private static final long INTERVAL_COIN_MARKET_CAP_MS = 300_000;  // 5 min that data structure is quite heavy so we don't request too often.
    private static final long MARKET_PRICE_TTL_SEC = 1800;            // 30 min

    private final Timer timerBtcAverageLocal = new Timer();
    private final Timer timerBtcAverageGlobal = new Timer();
    private final Timer timerPoloniex = new Timer();
    private final Timer timerCoinmarketcap = new Timer();

    private final BtcAverageProvider btcAverageProvider;
    private final PoloniexProvider poloniexProvider;
    private final CoinmarketcapProvider coinmarketcapProvider;

    private final Map<String, PriceData> allPricesMap = new ConcurrentHashMap<>();
    private Map<String, PriceData> btcAverageLocalMap;
    private Map<String, PriceData> poloniexMap;

    private long btcAverageTs;
    private long poloniexTs;
    private long coinmarketcapTs;

    private String json;

    public PriceRequestService(String bitcoinAveragePrivKey, String bitcoinAveragePubKey) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        btcAverageProvider = new BtcAverageProvider(bitcoinAveragePrivKey, bitcoinAveragePubKey);
        poloniexProvider = new PoloniexProvider();
        coinmarketcapProvider = new CoinmarketcapProvider();

        startRequests();
    }

    public String getJson() {
        return json;
    }

    private void startRequests() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        timerBtcAverageLocal.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    requestBtcAverageLocalPrices();
                } catch (Throwable e) {
                    log.warn(e.toString());
                    e.printStackTrace();
                }
            }
        }, INTERVAL_BTC_AV_LOCAL_MS, INTERVAL_BTC_AV_LOCAL_MS);

        timerBtcAverageGlobal.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    requestBtcAverageGlobalPrices();
                } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                    log.error(e.toString());
                    e.printStackTrace();
                } catch (IOException e) {
                    log.warn(e.toString());
                    e.printStackTrace();
                }
            }
        }, INTERVAL_BTC_AV_GLOBAL_MS, INTERVAL_BTC_AV_GLOBAL_MS);

        timerPoloniex.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    requestPoloniexPrices();
                } catch (IOException e) {
                    log.warn(e.toString());
                    e.printStackTrace();
                }
            }
        }, INTERVAL_POLONIEX_MS, INTERVAL_POLONIEX_MS);

        timerCoinmarketcap.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    requestCoinmarketcapPrices();
                } catch (IOException e) {
                    log.warn(e.toString());
                    e.printStackTrace();
                }
            }
        }, INTERVAL_COIN_MARKET_CAP_MS, INTERVAL_COIN_MARKET_CAP_MS);

        requestBtcAverageLocalPrices();
        requestBtcAverageGlobalPrices();
        requestPoloniexPrices();
        requestCoinmarketcapPrices();
    }


    private void requestCoinmarketcapPrices() throws IOException {
        long ts = System.currentTimeMillis();
        Map<String, PriceData> map = coinmarketcapProvider.request();
        log.info("requestCoinmarketcapPrices took {} ms.", (System.currentTimeMillis() - ts));
        removeOutdatedPrices(poloniexMap);
        removeOutdatedPrices(allPricesMap);
        // we don't replace prices which we got form the Poloniex request, just in case the Coinmarketcap data are
        // received earlier at startup we allow them but Poloniex will overwrite them.
        map.entrySet().stream()
                .filter(e -> poloniexMap == null || !poloniexMap.containsKey(e.getKey()))
                .forEach(e -> allPricesMap.put(e.getKey(), e.getValue()));
        coinmarketcapTs = Instant.now().getEpochSecond();

        if (map.get("LTC") != null)
            log.info("Coinmarketcap LTC (last): " + map.get("LTC").getPrice());

        writeToJson();
    }


    private void requestPoloniexPrices() throws IOException {
        long ts = System.currentTimeMillis();
        poloniexMap = poloniexProvider.request();
        log.info("requestPoloniexPrices took {} ms.", (System.currentTimeMillis() - ts));
        removeOutdatedPrices(allPricesMap);
        allPricesMap.putAll(poloniexMap);
        poloniexTs = Instant.now().getEpochSecond();

        if (poloniexMap.get("LTC") != null)
            log.info("Poloniex LTC (last): " + poloniexMap.get("LTC").getPrice());

        writeToJson();
    }

    private void requestBtcAverageLocalPrices() throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        long ts = System.currentTimeMillis();
        btcAverageLocalMap = btcAverageProvider.getLocal();

        if (btcAverageLocalMap.get("USD") != null)
            log.info("BTCAverage local USD (last):" + btcAverageLocalMap.get("USD").getPrice());
        log.info("requestBtcAverageLocalPrices took {} ms.", (System.currentTimeMillis() - ts));

        removeOutdatedPrices(allPricesMap);
        allPricesMap.putAll(btcAverageLocalMap);
        btcAverageTs = Instant.now().getEpochSecond();
        writeToJson();
    }

    private void requestBtcAverageGlobalPrices() throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        long ts = System.currentTimeMillis();
        Map<String, PriceData> map = btcAverageProvider.getGlobal();

        if (map.get("USD") != null)
            log.info("BTCAverage global USD (last):" + map.get("USD").getPrice());
        log.info("requestBtcAverageGlobalPrices took {} ms.", (System.currentTimeMillis() - ts));

        removeOutdatedPrices(btcAverageLocalMap);
        removeOutdatedPrices(allPricesMap);
        // we don't replace prices which we got form the local request, just in case the global data are received
        // earlier at startup we allow them but the local request will overwrite them.
        map.entrySet().stream()
                .filter(e -> btcAverageLocalMap == null || !btcAverageLocalMap.containsKey(e.getKey()))
                .forEach(e -> allPricesMap.put(e.getKey(), e.getValue()));
        btcAverageTs = Instant.now().getEpochSecond();
        writeToJson();
    }

    private void writeToJson() {
        Map<String, Object> map = new HashMap<>();
        map.put("btcAverageTs", btcAverageTs);
        map.put("poloniexTs", poloniexTs);
        map.put("coinmarketcapTs", coinmarketcapTs);
        map.put("data", allPricesMap.values().toArray());
        json = Utilities.objectToJson(map);
    }

    private void removeOutdatedPrices(Map<String, PriceData> map) {
        long now = Instant.now().getEpochSecond();
        long limit = now - MARKET_PRICE_TTL_SEC;
        Map<String, PriceData> filtered = map.entrySet().stream()
                .filter(e -> e.getValue().getTimestampSec() > limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        map.clear();
        map.putAll(filtered);
    }
}
