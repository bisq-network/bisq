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
import io.bisq.common.monetary.Volume;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferPayload;
import org.bitcoinj.core.Coin;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Offer.class,OfferPayload.class})
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
    public void testFormatSameVolume() {
        Offer offer = mock(Offer.class);
        Volume btc = Volume.parse("0.10", "BTC");
        when(offer.getMinVolume()).thenReturn(btc);
        when(offer.getVolume()).thenReturn(btc);

        assertEquals("0.10000000", formatter.formatMinVolumeAndVolume(offer));
    }

    @Test
    public void testFormatDifferentVolume() {
        Offer offer = mock(Offer.class);
        Volume btcMin = Volume.parse("0.10", "BTC");
        Volume btcMax = Volume.parse("0.25", "BTC");
        when(offer.isRange()).thenReturn(true);
        when(offer.getMinVolume()).thenReturn(btcMin);
        when(offer.getVolume()).thenReturn(btcMax);

        assertEquals("0.10000000 - 0.25000000", formatter.formatMinVolumeAndVolume(offer));
    }

    @Test
    public void testFormatNullVolume() {
        Offer offer = mock(Offer.class);
        when(offer.getMinVolume()).thenReturn(null);
        when(offer.getVolume()).thenReturn(null);

        assertEquals("", formatter.formatMinVolumeAndVolume(offer));
    }

    @Test
    public void testFormatSameAmount() {
        Offer offer = mock(Offer.class);
        when(offer.getMinAmount()).thenReturn(Coin.valueOf(10000000));
        when(offer.getAmount()).thenReturn(Coin.valueOf(10000000));

        assertEquals("0.10", formatter.formatAmountWithMinAmount(offer));
    }

    @Test
    public void testFormatDifferentAmount() {
        OfferPayload offerPayload = mock(OfferPayload.class);
        Offer offer = new Offer(offerPayload);
        when(offerPayload.getMinAmount()).thenReturn(10000000L);
        when(offerPayload.getAmount()).thenReturn(20000000L);

        assertEquals("0.10 - 0.20", formatter.formatAmountWithMinAmount(offer));
    }

    @Test
    public void testFormatNullAmount() {
        Offer offer = mock(Offer.class);
        when(offer.getMinAmount()).thenReturn(null);
        when(offer.getAmount()).thenReturn(null);

        assertEquals("", formatter.formatAmountWithMinAmount(offer));
    }
}
