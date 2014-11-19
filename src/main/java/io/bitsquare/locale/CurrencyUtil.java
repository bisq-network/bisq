/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.locale;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Set;

public class CurrencyUtil {

    public static List<Currency> getAllCurrencies() {
        final ArrayList<Currency> mainCurrencies = new ArrayList<>();
        mainCurrencies.add(Currency.getInstance("EUR"));
        mainCurrencies.add(Currency.getInstance("USD"));
        mainCurrencies.add(Currency.getInstance("CNY"));
        mainCurrencies.add(Currency.getInstance("RUB"));
        mainCurrencies.add(Currency.getInstance("JPY"));
        mainCurrencies.add(Currency.getInstance("GBP"));
        mainCurrencies.add(Currency.getInstance("CAD"));
        mainCurrencies.add(Currency.getInstance("AUD"));
        mainCurrencies.add(Currency.getInstance("CHF"));
        mainCurrencies.add(Currency.getInstance("CNY"));

        Set<Currency> allCurrenciesSet = Currency.getAvailableCurrencies();

        allCurrenciesSet.removeAll(mainCurrencies);
        final List<Currency> allCurrenciesList = new ArrayList<>(allCurrenciesSet);
        allCurrenciesList.sort((a, b) -> a.getCurrencyCode().compareTo(b.getCurrencyCode()));

        final List<Currency> resultList = new ArrayList<>(mainCurrencies);
        resultList.addAll(allCurrenciesList);

        resultList.remove(getDefaultCurrency());
        resultList.add(0, getDefaultCurrency());

        return resultList;
    }

    public static Currency getDefaultCurrency() {
        // TODO Only display EUR for the moment
        return Currency.getInstance("EUR");
        // return Currency.getInstance(Locale.getDefault());
    }
}
