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

package io.bisq.gui.util;

import io.bisq.common.locale.Res;
import io.bisq.common.monetary.VolumeMaker;
import org.bitcoinj.core.CoinMaker;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static io.bisq.common.monetary.PriceMaker.ltcPrice;
import static io.bisq.common.monetary.PriceMaker.priceString;
import static io.bisq.common.monetary.PriceMaker.usdPrice;
import static io.bisq.common.monetary.VolumeMaker.usdVolume;
import static io.bisq.common.monetary.VolumeMaker.volumeString;
import static org.bitcoinj.core.CoinMaker.Coin;
import static org.bitcoinj.core.CoinMaker.oneBitcoin;
import static org.bitcoinj.core.CoinMaker.satoshis;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BSFormatterTest {

    private BSFormatter formatter;

    @Before
    public void setup() {
        Locale.setDefault(new Locale("en", "US"));
        formatter = new BSFormatter();
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    @Test
    public void testIsValid() {
        assertEquals("0 days", formatter.formatAccountAge(TimeUnit.HOURS.toMillis(23)));
        assertEquals("0 days", formatter.formatAccountAge(0));
        assertEquals("0 days", formatter.formatAccountAge(-1));
        assertEquals("1 day", formatter.formatAccountAge(TimeUnit.DAYS.toMillis(1)));
        assertEquals("2 days", formatter.formatAccountAge(TimeUnit.DAYS.toMillis(2)));
        assertEquals("30 days", formatter.formatAccountAge(TimeUnit.DAYS.toMillis(30)));
        assertEquals("60 days", formatter.formatAccountAge(TimeUnit.DAYS.toMillis(60)));
    }

    @Test
    public void testFormatDurationAsWords() {
        long oneDay = TimeUnit.DAYS.toMillis(1);
        long oneHour = TimeUnit.HOURS.toMillis(1);
        long oneMinute = TimeUnit.MINUTES.toMillis(1);
        long oneSecond = TimeUnit.SECONDS.toMillis(1);

        assertEquals("1 hour, 0 minutes", formatter.formatDurationAsWords(oneHour));
        assertEquals("1 day, 0 hours, 0 minutes", formatter.formatDurationAsWords(oneDay));
        assertEquals("2 days, 0 hours, 1 minute", formatter.formatDurationAsWords(oneDay * 2 + oneMinute));
        assertEquals("2 days, 0 hours, 2 minutes", formatter.formatDurationAsWords(oneDay * 2 + oneMinute * 2));
        assertEquals("1 hour, 0 minutes, 0 seconds", formatter.formatDurationAsWords(oneHour, true));
        assertEquals("1 hour, 0 minutes, 1 second", formatter.formatDurationAsWords(oneHour + oneSecond, true));
        assertEquals("1 hour, 0 minutes, 2 seconds", formatter.formatDurationAsWords(oneHour + oneSecond * 2, true));
        assertEquals("", formatter.formatDurationAsWords(0));
        assertTrue(formatter.formatDurationAsWords(0).isEmpty());
    }

    @Test
    public void testFormatPrice() {
        assertEquals("100.0000", formatter.formatPrice(make(usdPrice)));
        assertEquals("  100.0000", formatter.formatPrice(make(usdPrice), true));
        assertEquals("7098.4700", formatter.formatPrice(make(usdPrice.but(with(priceString, "7098.4700")))));
    }

    @Test
    public void testFormatCoin() {
        assertEquals("1.0000", formatter.formatCoin(oneBitcoin));
        assertEquals("0.000001", formatter.formatCoin(make(a(Coin).but(with(satoshis, 100L)))));
        assertEquals("0.00000001", formatter.formatCoin(make(a(Coin).but(with(satoshis, 1L)))));
    }

    @Test
    public void testFormatVolume() {
        assertEquals("  100.00", formatter.formatVolume(make(usdVolume), true));
        assertEquals("100.00", formatter.formatVolume(make(usdVolume)));
        assertEquals("1774.62", formatter.formatVolume(make(usdVolume.but(with(volumeString, "1774.62")))));
    }
}
