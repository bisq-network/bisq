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
import bisq.core.monetary.Volume;

import org.bitcoinj.utils.Fiat;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Maker;
import com.natpryce.makeiteasy.Property;

import static com.natpryce.makeiteasy.MakeItEasy.a;

public class VolumeMaker {

    public static final Property<Volume, String> currencyCode = new Property<>();
    public static final Property<Volume, String> volumeString = new Property<>();

    public static final Instantiator<Volume> FiatVolume = lookup ->
            new Volume(Fiat.parseFiat(lookup.valueOf(currencyCode, "USD"), lookup.valueOf(volumeString, "100")));

    public static final Instantiator<Volume> AltcoinVolume = lookup ->
            new Volume(Altcoin.parseAltcoin(lookup.valueOf(currencyCode, "LTC"), lookup.valueOf(volumeString, "100")));

    public static final Maker<Volume> usdVolume = a(FiatVolume);
    public static final Maker<Volume> ltcVolume = a(AltcoinVolume);
}
