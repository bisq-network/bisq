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

import bisq.core.locale.Res;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.util.BSFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.CoinMaker;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import static bisq.desktop.maker.OfferMaker.btcUsdOffer;
import static bisq.desktop.maker.PriceMaker.priceString;
import static bisq.desktop.maker.PriceMaker.usdPrice;
import static bisq.desktop.maker.VolumeMaker.usdVolume;
import static bisq.desktop.maker.VolumeMaker.volumeString;
import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.bitcoinj.core.CoinMaker.oneBitcoin;
import static org.bitcoinj.core.CoinMaker.satoshis;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BSFormatterTest {

    private BSFormatter formatter;

    @Before
    public void setUp() {
        Locale.setDefault(new Locale("en", "US"));
        formatter = new BSFormatter();
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    @Test
    public void testFormatDurationAsWords() {
        long oneDay = TimeUnit.DAYS.toMillis(1);
        long oneHour = TimeUnit.HOURS.toMillis(1);
        long oneMinute = TimeUnit.MINUTES.toMillis(1);
        long oneSecond = TimeUnit.SECONDS.toMillis(1);

        assertEquals("1 hour, 0 minutes", BSFormatter.formatDurationAsWords(oneHour));
        assertEquals("1 day, 0 hours, 0 minutes", BSFormatter.formatDurationAsWords(oneDay));
        assertEquals("2 days, 0 hours, 1 minute", BSFormatter.formatDurationAsWords(oneDay * 2 + oneMinute));
        assertEquals("2 days, 0 hours, 2 minutes", BSFormatter.formatDurationAsWords(oneDay * 2 + oneMinute * 2));
        assertEquals("1 hour, 0 minutes, 0 seconds", BSFormatter.formatDurationAsWords(oneHour, true, true));
        assertEquals("1 hour, 0 minutes, 1 second", BSFormatter.formatDurationAsWords(oneHour + oneSecond, true, true));
        assertEquals("1 hour, 0 minutes, 2 seconds", BSFormatter.formatDurationAsWords(oneHour + oneSecond * 2, true, true));
        assertEquals("2 days, 21 hours, 28 minutes", BSFormatter.formatDurationAsWords(oneDay * 2 + oneHour * 21 + oneMinute * 28));
        assertEquals("110 days", BSFormatter.formatDurationAsWords(oneDay * 110, false, false));
        assertEquals("10 days, 10 hours, 10 minutes, 10 seconds", BSFormatter.formatDurationAsWords(oneDay * 10 + oneHour * 10 + oneMinute * 10 + oneSecond * 10, true, false));
        assertEquals("1 hour, 2 seconds", BSFormatter.formatDurationAsWords(oneHour + oneSecond * 2, true, false));
        assertEquals("1 hour", BSFormatter.formatDurationAsWords(oneHour + oneSecond * 2, false, false));
        assertEquals("0 hours, 0 minutes, 1 second", BSFormatter.formatDurationAsWords(oneSecond, true, true));
        assertEquals("1 second", BSFormatter.formatDurationAsWords(oneSecond, true, false));
        assertEquals("0 hours", BSFormatter.formatDurationAsWords(oneSecond, false, false));
        assertEquals("", BSFormatter.formatDurationAsWords(0));
        assertTrue(BSFormatter.formatDurationAsWords(0).isEmpty());
    }

    @Test
    public void testFormatPrice() {
        assertEquals("100.0000", BSFormatter.formatPrice(make(usdPrice)));
        assertEquals("7098.4700", BSFormatter.formatPrice(make(usdPrice.but(with(priceString, "7098.4700")))));
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
