package io.bisq.gui.util;

import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.core.user.Preferences;
import javafx.collections.ObservableList;

import javax.annotation.Nullable;
import java.util.*;
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
        Map<TradeCurrency, Integer> tradesPerCurrencyMap = getTradeCurrencyIntegerMap(currencies, preferences);

        List<CurrencyListItem> list = tradesPerCurrencyMap.keySet().stream()
                .filter(e -> CurrencyUtil.isFiatCurrency(e.getCode()))
                .map(e -> new CurrencyListItem(e, tradesPerCurrencyMap.get(e)))
                .collect(Collectors.toList());
        List<CurrencyListItem> cryptoList = tradesPerCurrencyMap.keySet().stream()
                .filter(e -> CurrencyUtil.isCryptoCurrency(e.getCode()))
                .map(e -> new CurrencyListItem(e, tradesPerCurrencyMap.get(e)))
                .collect(Collectors.toList());

        if (preferences.isSortMarketCurrenciesNumerically()) {
            list.sort((left, right) -> Integer.compare(right.numTrades, left.numTrades));
            cryptoList.sort((left, right) -> Integer.compare(right.numTrades, left.numTrades));
        } else {
            list.sort(Comparator.comparing(item -> item.tradeCurrency));
            cryptoList.sort(Comparator.comparing(item -> item.tradeCurrency));
        }

        list.addAll(cryptoList);

        if (showAllCurrencyListItem != null) {
            list.add(0, showAllCurrencyListItem);
        }

        currencyItems.setAll(list);
    }

    private Map<TradeCurrency, Integer> getTradeCurrencyIntegerMap(List<TradeCurrency> currencies, Preferences preferences) {
        Map<TradeCurrency, Integer> tradesPerCurrencyMap = new HashMap<>();

        // We get the list of all offers or trades. We want to find out how many items at each currency we have.
        currencies.forEach(tradeCurrency -> {
            if (tradesPerCurrencyMap.containsKey(tradeCurrency)) {
                tradesPerCurrencyMap.put(tradeCurrency, tradesPerCurrencyMap.get(tradeCurrency) + 1);
            } else {
                tradesPerCurrencyMap.put(tradeCurrency, 1);
            }
        });

        Set<TradeCurrency> userSet = new HashSet<>(preferences.getFiatCurrencies());
        userSet.addAll(preferences.getCryptoCurrencies());
        // Now all those items which are not in the offers or trades list but comes from the user preferred currency list
        // will get set to 0
        userSet.forEach(tradeCurrency -> {
            if (!tradesPerCurrencyMap.containsKey(tradeCurrency)) {
                tradesPerCurrencyMap.put(tradeCurrency, 0);
            }
        });

        return tradesPerCurrencyMap;
    }
}
