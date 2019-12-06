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

import bisq.core.app.BisqEnvironment;
import bisq.core.locale.Res;
import bisq.core.util.coin.ImmutableCoinFormatter;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.CoinMaker;

import java.util.Locale;

import org.junit.Before;
import org.junit.Test;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.bitcoinj.core.CoinMaker.oneBitcoin;
import static org.bitcoinj.core.CoinMaker.satoshis;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ImmutableCoinFormatterTest {

    private final CoinFormatter formatter = new ImmutableCoinFormatter(BisqEnvironment.getParameters().getMonetaryFormat());

    @Before
    public void setUp() {
        Locale.setDefault(new Locale("en", "US"));
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    @Test
    public void testFormatCoin() {
        assertEquals("1.00", formatter.formatCoin(oneBitcoin));
        assertEquals("1.0000", formatter.formatCoin(oneBitcoin, 4));
        assertEquals("1.00", formatter.formatCoin(oneBitcoin, 5));
        assertEquals("0.000001", formatter.formatCoin(make(a(CoinMaker.Coin).but(with(satoshis, 100L)))));
        assertEquals("0.00000001", formatter.formatCoin(make(a(CoinMaker.Coin).but(with(satoshis, 1L)))));
    }
}
