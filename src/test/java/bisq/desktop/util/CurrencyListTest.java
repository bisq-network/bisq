/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.util;

import bisq.core.user.Preferences;

import bisq.common.locale.CryptoCurrency;
import bisq.common.locale.FiatCurrency;
import bisq.common.locale.TradeCurrency;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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

    private Preferences preferences;
    private List<CurrencyListItem> delegate;
    private CurrencyList testedEntity;

    @Before
    public void setUp() {
        Locale.setDefault(new Locale("en", "US"));

        CurrencyPredicates predicates = mock(CurrencyPredicates.class);
        when(predicates.isCryptoCurrency(USD)).thenReturn(false);
        when(predicates.isCryptoCurrency(RUR)).thenReturn(false);
        when(predicates.isCryptoCurrency(BTC)).thenReturn(true);
        when(predicates.isCryptoCurrency(ETH)).thenReturn(true);

        when(predicates.isFiatCurrency(USD)).thenReturn(true);
        when(predicates.isFiatCurrency(RUR)).thenReturn(true);
        when(predicates.isFiatCurrency(BTC)).thenReturn(false);
        when(predicates.isFiatCurrency(ETH)).thenReturn(false);

        this.preferences = mock(Preferences.class);
        this.delegate = new ArrayList<>();
        this.testedEntity = new CurrencyList(delegate, preferences, predicates);
    }

    @Test
    public void testUpdateWhenSortNumerically() {
        when(preferences.isSortMarketCurrenciesNumerically()).thenReturn(true);

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
        when(preferences.isSortMarketCurrenciesNumerically()).thenReturn(false);

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
        when(preferences.isSortMarketCurrenciesNumerically()).thenReturn(true);

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
