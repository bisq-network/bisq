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

package org.bitcoinj.core;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Property;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static org.bitcoinj.core.Coin.valueOf;

public class CoinMaker {

    public static final Property<Coin, Long> satoshis = new Property<>();

    public static final Instantiator<Coin> Coin = lookup ->
            valueOf(lookup.valueOf(satoshis, 100000000L));

    public static final Coin oneBitcoin = make(a(Coin));
}
