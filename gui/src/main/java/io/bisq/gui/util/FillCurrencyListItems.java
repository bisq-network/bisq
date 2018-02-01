package io.bisq.gui.util;

import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.core.user.Preferences;
import javafx.collections.ObservableList;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

class FillCurrencyListItems {
    private final List<TradeCurrency> currencies;
    private final ObservableList<CurrencyListItem> currencyItems;

    @Nullable
    private final CurrencyListItem showAllCurrencyListItem;

    private final Preferences preferences;

    FillCurrencyListItems(List<TradeCurrency> currencies, ObservableList<CurrencyListItem> currencyItems,
                          @Nullable CurrencyListItem showAllCurrencyListItem, Preferences preferences) {
        this.currencies = currencies;
        this.currencyItems = currencyItems;
        this.showAllCurrencyListItem = showAllCurrencyListItem;
        this.preferences = preferences;
    }

    public void fillCurrencyListItems() {
        Map<TradeCurrency, Integer> tradesPerCurrency = getTradesPerCurrency();


        Comparator<CurrencyListItem> comparator = getComparator();

        List<CurrencyListItem> fiatCurrencies = tradesPerCurrency.keySet().stream()
                .filter(e -> CurrencyUtil.isFiatCurrency(e.getCode()))
                .map(e -> new CurrencyListItem(e, tradesPerCurrency.get(e)))
                .sorted(comparator)
                .collect(Collectors.toList());
        List<CurrencyListItem> cryptoCurrencies = tradesPerCurrency.keySet().stream()
                .filter(e -> CurrencyUtil.isCryptoCurrency(e.getCode()))
                .map(e -> new CurrencyListItem(e, tradesPerCurrency.get(e)))
                .sorted(comparator)
                .collect(Collectors.toList());

        fiatCurrencies.addAll(cryptoCurrencies);

        if (showAllCurrencyListItem != null) {
            fiatCurrencies.add(0, showAllCurrencyListItem);
        }

        currencyItems.setAll(fiatCurrencies);
    }

    private Comparator<CurrencyListItem> getComparator() {
        Comparator<CurrencyListItem> result;
        if (preferences.isSortMarketCurrenciesNumerically()) {
            Comparator<CurrencyListItem> byCount = Comparator.comparingInt(left -> left.numTrades);
            result = byCount.reversed();
        } else {
            result = Comparator.comparing(item -> item.tradeCurrency);
        }
        return result;
    }

    private Map<TradeCurrency, Integer> getTradesPerCurrency() {
        Map<TradeCurrency, Integer> result = new HashMap<>();

        BiFunction<TradeCurrency, Integer, Integer> incrementCurrentOrZero =
                (key, value) -> value == null ? 0 : value + 1;
        currencies.forEach(currency -> result.compute(currency, incrementCurrentOrZero));

        Set<TradeCurrency> preferred = new HashSet<>();
        preferred.addAll(preferences.getFiatCurrencies());
        preferred.addAll(preferences.getCryptoCurrencies());
        preferred.forEach(currency -> result.putIfAbsent(currency, 0));

        return result;
    }
}
