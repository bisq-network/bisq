package io.bitsquare.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.hibernate.validator.constraints.Length;

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

class Currency {
    @JsonProperty
    String symbol;
    @JsonProperty
    String name;
    @JsonProperty
    String type;

    Currency(String symbol, String name, String type) {
        this.symbol = symbol;
        this.name = name;
        this.type = type;
    }
}