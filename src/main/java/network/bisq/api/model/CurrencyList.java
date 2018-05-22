package network.bisq.api.model;

import java.util.ArrayList;
import java.util.List;

public class CurrencyList {

    public List<Currency> currencies = new ArrayList<>();
    public int total;

    public void add(String code, String name, String type) {
        currencies.add(new Currency(code, name, type));
        total = currencies.size();
    }

}

