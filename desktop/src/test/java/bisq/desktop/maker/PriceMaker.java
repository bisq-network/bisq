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

import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;

import org.bitcoinj.utils.Fiat;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Maker;
import com.natpryce.makeiteasy.Property;

import static com.natpryce.makeiteasy.MakeItEasy.a;

public class PriceMaker {

    public static final Property<Price, String> currencyCode = new Property<>();
    public static final Property<Price, String> priceString = new Property<>();

    public static final Instantiator<Price> FiatPrice = lookup ->
            new Price(Fiat.parseFiat(lookup.valueOf(currencyCode, "USD"), lookup.valueOf(priceString, "100")));

    public static final Instantiator<Price> AltcoinPrice = lookup ->
            new Price(Altcoin.parseAltcoin(lookup.valueOf(currencyCode, "LTC"), lookup.valueOf(priceString, "100")));

    public static final Maker<Price> usdPrice = a(FiatPrice);
    public static final Maker<Price> ltcPrice = a(AltcoinPrice);
}
