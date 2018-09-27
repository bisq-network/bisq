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

package bisq.desktop.maker;

import bisq.desktop.util.CurrencyListItem;

import bisq.core.locale.TradeCurrency;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Maker;
import com.natpryce.makeiteasy.Property;

import static bisq.desktop.maker.TradeCurrencyMakers.bitcoin;
import static bisq.desktop.maker.TradeCurrencyMakers.euro;
import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.with;

public class CurrencyListItemMakers {

    public static final Property<bisq.desktop.util.CurrencyListItem, TradeCurrency> tradeCurrency = new Property<>();
    public static final Property<CurrencyListItem, Integer> numberOfTrades = new Property<>();

    public static final Instantiator<CurrencyListItem> CurrencyListItem = lookup ->
            new CurrencyListItem(lookup.valueOf(tradeCurrency, bitcoin), lookup.valueOf(numberOfTrades, 0));

    public static final Maker<CurrencyListItem> bitcoinItem = a(CurrencyListItem);
    public static final Maker<CurrencyListItem> euroItem = a(CurrencyListItem, with(tradeCurrency, euro));
}
