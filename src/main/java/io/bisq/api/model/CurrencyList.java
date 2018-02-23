package io.bisq.api.model;

import java.util.ArrayList;
import java.util.List;

public class CurrencyList {

    public List<Currency> currencies = new ArrayList<>();

    public void add(String symbol, String name, String type) {
        currencies.add(new Currency(symbol, name, type));
    }

}

