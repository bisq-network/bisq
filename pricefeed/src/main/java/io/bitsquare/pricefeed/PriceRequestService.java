/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.pricefeed;

import io.bitsquare.common.util.Utilities;
import io.bitsquare.http.HttpException;
import io.bitsquare.pricefeed.providers.BtcAverageProvider;
import io.bitsquare.pricefeed.providers.CoinmarketcapProvider;
import io.bitsquare.pricefeed.providers.PoloniexProvider;
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

class PriceRequestService {
    private static final Logger log = LoggerFactory.getLogger(PriceRequestService.class);

    private static final long INTERVAL_BTC_AV_LOCAL_MS = 60_000;      // 60 sec 
    private static final long INTERVAL_BTC_AV_GLOBAL_MS = 150_000;    // 2.5 min 
    private static final long INTERVAL_POLONIEX_MS = 60_000;          // 1 min
    private static final long INTERVAL_COIN_MARKET_CAP_MS = 300_000;  // 5 min

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
                } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                    log.error(e.toString());
                    e.printStackTrace();
                } catch (HttpException | IOException e) {
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
                } catch (HttpException | IOException e) {
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
                } catch (IOException | HttpException e) {
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
                } catch (IOException | HttpException e) {
                    log.warn(e.toString());
                    e.printStackTrace();
                }
            }
        }, INTERVAL_COIN_MARKET_CAP_MS, INTERVAL_COIN_MARKET_CAP_MS);

        try {
            requestBtcAverageLocalPrices();
            requestBtcAverageGlobalPrices();
            requestPoloniexPrices();
            requestCoinmarketcapPrices();
        } catch (HttpException e) {
            log.warn(e.toString());
            e.printStackTrace();
        }
    }


    private void requestCoinmarketcapPrices() throws IOException, HttpException {
        Map<String, PriceData> map = coinmarketcapProvider.request();
        // we don't replace prices which we got form the Poloniex request, just in case the Coinmarketcap data are 
        // received earlier at startup we allow them but Poloniex will overwrite them.
        map.entrySet().stream()
                .filter(e -> poloniexMap == null || !poloniexMap.containsKey(e.getKey()))
                .forEach(e -> allPricesMap.put(e.getKey(), e.getValue()));
        coinmarketcapTs = Instant.now().getEpochSecond();
        log.info("Coinmarketcap LTC (last): " + map.get("LTC").l);
        writeToJson();
    }


    private void requestPoloniexPrices() throws IOException, HttpException {
        poloniexMap = poloniexProvider.request();
        allPricesMap.putAll(poloniexMap);
        poloniexTs = Instant.now().getEpochSecond();
        log.info("Poloniex LTC (last): " + poloniexMap.get("LTC").l);
        writeToJson();
    }

    private void requestBtcAverageLocalPrices() throws NoSuchAlgorithmException, InvalidKeyException, IOException, HttpException {
        btcAverageLocalMap = btcAverageProvider.getLocal();
        log.info("BTCAverage local USD (last):" + btcAverageLocalMap.get("USD").l);
        allPricesMap.putAll(btcAverageLocalMap);
        btcAverageTs = Instant.now().getEpochSecond();
        writeToJson();
    }

    private void requestBtcAverageGlobalPrices() throws NoSuchAlgorithmException, InvalidKeyException, IOException, HttpException {
        Map<String, PriceData> map = btcAverageProvider.getGlobal();
        log.info("BTCAverage global USD (last):" + map.get("USD").l);
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
}
