package io.bisq.api.api;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mike on 31/08/16.
 */
public class CurrencyList {
    public List<Currency> currencies = new ArrayList<>();

    public CurrencyList() {
        // Jackson deserialization
    }

    public void add(String symbol, String name, String type) {
        currencies.add(new Currency(symbol, name, type));
    }

    @JsonValue
    public List<Currency> getCurrencies() {
        return currencies;
    }
}

