package bisq.price.util.coingecko;


import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoinGeckoMarketData {

    private Map<String, CoinGeckoTicker> rates;

    public void setRates(Map<String, CoinGeckoTicker> rates) {
        // Convert keys to uppercase ("usd" -> "USD") when deserializing API response
        this.rates = rates.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toUpperCase(), entry -> entry.getValue()));
    }

}
