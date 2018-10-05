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

package bisq.price.util;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.TradeCurrency;

import java.util.Set;
import java.util.stream.Collectors;

public abstract class Altcoins {

    public static final Set<String> ALL_SUPPORTED =
        CurrencyUtil.getAllSortedCryptoCurrencies().stream()
            .map(TradeCurrency::getCode)
            .collect(Collectors.toSet());
}
