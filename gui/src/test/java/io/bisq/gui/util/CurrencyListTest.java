package io.bisq.gui.util;

import com.google.common.collect.Lists;
import io.bisq.common.locale.CryptoCurrency;
import io.bisq.common.locale.FiatCurrency;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.core.user.Preferences;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Preferences.class)
public class CurrencyListTest {
    private static final TradeCurrency USD = new FiatCurrency("USD");
    private static final TradeCurrency RUR = new FiatCurrency("RUR");
    private static final TradeCurrency BTC = new CryptoCurrency("BTC", "Bitcoin");
    private static final TradeCurrency ETH = new CryptoCurrency("ETH", "Ether");
    private static final TradeCurrency BSQ = new CryptoCurrency("BSQ", "Bisq Token");

    private CurrencyPredicates predicates;

    @Before
    public void setUp() {
        predicates = mock(CurrencyPredicates.class);
        when(predicates.isCryptoCurrency(USD)).thenReturn(false);
        when(predicates.isCryptoCurrency(RUR)).thenReturn(false);
        when(predicates.isCryptoCurrency(BTC)).thenReturn(true);
        when(predicates.isCryptoCurrency(ETH)).thenReturn(true);

        when(predicates.isFiatCurrency(USD)).thenReturn(true);
        when(predicates.isFiatCurrency(RUR)).thenReturn(true);
        when(predicates.isFiatCurrency(BTC)).thenReturn(false);
        when(predicates.isFiatCurrency(ETH)).thenReturn(false);
    }

    @Test
    public void testUpdateWhenSortNumerically() {
        ObservableList<CurrencyListItem> delegate = FXCollections.observableArrayList();

        Preferences preferences = mock(Preferences.class);
        when(preferences.isSortMarketCurrenciesNumerically()).thenReturn(true);

        CurrencyList testedEntity = new CurrencyList(delegate, preferences, predicates);

        List<TradeCurrency> currencies = Lists.newArrayList(USD, RUR, USD, ETH, ETH, BTC);
        testedEntity.updateWithCurrencies(currencies, null);

        List<CurrencyListItem> expected = Lists.newArrayList(
                new CurrencyListItem(USD, 2),
                new CurrencyListItem(RUR, 1),
                new CurrencyListItem(ETH, 2),
                new CurrencyListItem(BTC, 1));

        assertEquals(expected, delegate);
    }

    @Test
    public void testUpdateWhenNotSortNumerically() {
        ObservableList<CurrencyListItem> delegate = FXCollections.observableArrayList();

        Preferences preferences = mock(Preferences.class);
        when(preferences.isSortMarketCurrenciesNumerically()).thenReturn(false);

        CurrencyList testedEntity = new CurrencyList(delegate, preferences, predicates);

        List<TradeCurrency> currencies = Lists.newArrayList(USD, RUR, USD, ETH, ETH, BTC);
        testedEntity.updateWithCurrencies(currencies, null);

        List<CurrencyListItem> expected = Lists.newArrayList(
                new CurrencyListItem(RUR, 1),
                new CurrencyListItem(USD, 2),
                new CurrencyListItem(BTC, 1),
                new CurrencyListItem(ETH, 2));

        assertEquals(expected, delegate);
    }

    @Test
    public void testUpdateWhenSortNumericallyAndFirstSpecified() {
        ObservableList<CurrencyListItem> delegate = FXCollections.observableArrayList();

        Preferences preferences = mock(Preferences.class);
        when(preferences.isSortMarketCurrenciesNumerically()).thenReturn(true);

        CurrencyList testedEntity = new CurrencyList(delegate, preferences, predicates);

        List<TradeCurrency> currencies = Lists.newArrayList(USD, RUR, USD, ETH, ETH, BTC);
        CurrencyListItem first = new CurrencyListItem(BSQ, 5);
        testedEntity.updateWithCurrencies(currencies, first);

        List<CurrencyListItem> expected = Lists.newArrayList(
                first,
                new CurrencyListItem(USD, 2),
                new CurrencyListItem(RUR, 1),
                new CurrencyListItem(ETH, 2),
                new CurrencyListItem(BTC, 1));

        assertEquals(expected, delegate);
    }

}
