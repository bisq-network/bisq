package io.bisq.provider.price.providers;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.network.http.HttpClient;
import io.bisq.provider.price.PriceData;
import io.bisq.provider.price.PriceRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Double.parseDouble;

public class PoloniexProvider {
    private static final Logger log = LoggerFactory.getLogger(PoloniexProvider.class);

    private final Set<String> supportedAltcoins;
    private final HttpClient httpClient;

    public PoloniexProvider() {
        this.httpClient = new HttpClient("https://poloniex.com/public");

        supportedAltcoins = CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                .map(TradeCurrency::getCode)
                .collect(Collectors.toSet());
    }

    public Map<String, PriceData> request() throws IOException {
        Map<String, PriceData> marketPriceMap = new HashMap<>();
        String response = httpClient.requestWithGET("?command=returnTicker", "User-Agent", "");
        //noinspection unchecked
        LinkedTreeMap<String, Object> treeMap = new Gson().fromJson(response, LinkedTreeMap.class);
        long ts = Instant.now().getEpochSecond();
        treeMap.entrySet().stream().forEach(e -> {
            Object value = e.getValue();
            String invertedCurrencyPair = e.getKey();
            String altcoinCurrency = null;
            if (invertedCurrencyPair.startsWith("BTC")) {
                String[] tokens = invertedCurrencyPair.split("_");
                if (tokens.length == 2) {
                    altcoinCurrency = tokens[1];
                    if (supportedAltcoins.contains(altcoinCurrency)) {
                        if (value instanceof LinkedTreeMap) {
                            //noinspection unchecked
                            LinkedTreeMap<String, Object> data = (LinkedTreeMap) value;
                            marketPriceMap.put(altcoinCurrency,
                                    new PriceData(altcoinCurrency,
                                            parseDouble((String) data.get("last")),
                                            ts,
                                            PriceRequestService.POLO_PROVIDER)
                            );
                        }
                    }
                } else {
                    log.error("invertedCurrencyPair has invalid format: invertedCurrencyPair=" + invertedCurrencyPair);
                }
            }
        });
        return marketPriceMap;
    }
}
