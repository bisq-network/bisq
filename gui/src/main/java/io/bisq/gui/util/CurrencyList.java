package io.bisq.gui.util;

import com.google.common.collect.Lists;
import com.sun.javafx.collections.ObservableListWrapper;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.core.user.Preferences;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;

public class CurrencyList extends ObservableListWrapper<CurrencyListItem>{
    private final CurrencyPredicates predicates;
    private final Preferences preferences;

    public CurrencyList(Preferences preferences) {
        this(new ArrayList<>(), preferences, new CurrencyPredicates());
    }

    CurrencyList(List<CurrencyListItem> delegate, Preferences preferences, CurrencyPredicates predicates) {
        super(delegate);
        this.predicates = predicates;
        this.preferences = preferences;
    }

    public void updateWithCurrencies(List<TradeCurrency> currencies, @Nullable CurrencyListItem first) {
        List<CurrencyListItem> result = Lists.newLinkedList();
        Optional.ofNullable(first).ifPresent(result::add);
        result.addAll(getPartitionedSortedItems(currencies));
        setAll(result);
    }

    private List<CurrencyListItem> getPartitionedSortedItems(List<TradeCurrency> currencies) {
        Map<TradeCurrency, Integer> tradesPerCurrency = countTrades(currencies);

        Comparator<CurrencyListItem> comparator = getComparator();
        Queue<CurrencyListItem> fiatCurrencies = new PriorityQueue<>(comparator);
        Queue<CurrencyListItem> cryptoCurrencies = new PriorityQueue<>(comparator);

        for (Map.Entry<TradeCurrency, Integer> entry : tradesPerCurrency.entrySet()) {
            TradeCurrency currency = entry.getKey();
            Integer count = entry.getValue();
            CurrencyListItem item = new CurrencyListItem(currency, count);

            if (predicates.isFiatCurrency(currency)) {
                fiatCurrencies.add(item);
            }

            if (predicates.isCryptoCurrency(currency)) {
                cryptoCurrencies.add(item);
            }
        }

        List<CurrencyListItem> result = Lists.newLinkedList();
        result.addAll(fiatCurrencies);
        result.addAll(cryptoCurrencies);

        return result;
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

    private Map<TradeCurrency, Integer> countTrades(List<TradeCurrency> currencies) {
        Map<TradeCurrency, Integer> result = new HashMap<>();

        BiFunction<TradeCurrency, Integer, Integer> incrementCurrentOrOne =
                (key, value) -> value == null ? 1 : value + 1;
        currencies.forEach(currency -> result.compute(currency, incrementCurrentOrOne));

        Set<TradeCurrency> preferred = new HashSet<>();
        preferred.addAll(preferences.getFiatCurrencies());
        preferred.addAll(preferences.getCryptoCurrencies());
        preferred.forEach(currency -> result.putIfAbsent(currency, 0));

        return result;
    }
}
