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

package io.bitsquare.gui.util;

import com.google.bitcoin.core.Coin;

import java.util.Locale;

import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.BSFormatter.*;
import static org.junit.Assert.*;

public class BSFormatterTest {
    private static final Logger log = LoggerFactory.getLogger(BSFormatterTest.class);

    @Test
    public void testParseToBtc() {
        useMilliBitFormat(false);
        assertEquals(Coin.ZERO, parseToCoin("0"));
        assertEquals(Coin.COIN, parseToCoin("1"));
        assertEquals(Coin.SATOSHI, parseToCoin("0,00000001"));

        assertEquals(Coin.parseCoin("-1"), parseToCoin("-1"));
        assertEquals(Coin.parseCoin("1.1"), parseToCoin("1,1"));
        assertEquals(Coin.parseCoin("1.1"), parseToCoin("1.1"));
        assertEquals(Coin.parseCoin("0"), parseToCoin("1.123,45"));
        assertEquals(Coin.parseCoin("0"), parseToCoin("1,123.45"));

        assertEquals(Coin.parseCoin("1.1234"), parseToCoinWith4Decimals("1,12342"));
        assertEquals(Coin.parseCoin("1.1235"), parseToCoinWith4Decimals("1,12345"));
        assertEquals(Coin.parseCoin("1.1230"), parseToCoinWith4Decimals("1,123"));

        // change to mBTC
        useMilliBitFormat(true);
        assertEquals(Coin.parseCoin("1"), parseToCoin("1000"));
        assertEquals(Coin.parseCoin("0.123"), parseToCoin("123"));
        assertEquals(Coin.parseCoin("0.1234"), parseToCoin("123.4"));
        assertEquals(Coin.parseCoin("0.12345"), parseToCoin("123.45"));
        assertEquals(Coin.parseCoin("0.123456"), parseToCoin("123.456"));
        assertEquals(Coin.parseCoin("0"), parseToCoin("123,456.7"));

        assertEquals(Coin.parseCoin("0.001123"), parseToCoinWith4Decimals("1.123"));
        assertEquals(Coin.parseCoin("0.0011234"), parseToCoinWith4Decimals("1.1234"));
        assertEquals(Coin.parseCoin("0.0011234"), parseToCoinWith4Decimals("1.12342"));
        assertEquals(Coin.parseCoin("0.0011235"), parseToCoinWith4Decimals("1.12345"));
    }

    @Test
    public void testFormatCoin() {
        useMilliBitFormat(false);
        assertEquals("1.00", formatCoin(Coin.COIN));
        assertEquals("1.0120", formatCoin(Coin.parseCoin("1.012")));
        assertEquals("1012.30", formatCoin(Coin.parseCoin("1012.3")));
        assertEquals("1.0120", formatCoin(Coin.parseCoin("1.01200")));
        assertEquals("1.0123", formatCoin(Coin.parseCoin("1.01234")));

        assertEquals("1.2345", formatCoin(Coin.parseCoin("1.2345")));
        assertEquals("1.2346", formatCoin(Coin.parseCoin("1.23456")));
        assertEquals("1.2346", formatCoin(Coin.parseCoin("1.234567")));
        assertEquals("1.2345", formatCoin(Coin.parseCoin("1.23448")));

        assertEquals("1.00", formatCoin(Coin.COIN));
        assertEquals("1012.30", formatCoin(Coin.parseCoin("1012.3")));

        // change to mBTC
        useMilliBitFormat(true);
        assertEquals("1000.00", formatCoin(Coin.COIN));
        assertEquals("1.00", formatCoin(Coin.MILLICOIN));
        assertEquals("0.0010", formatCoin(Coin.MICROCOIN));
    }

    @Test
    public void testFormatCoinWithCode() {
        useMilliBitFormat(false);
        assertEquals("1.00 BTC", formatCoinWithCode(Coin.COIN));
        assertEquals("1.01 BTC", formatCoinWithCode(Coin.parseCoin("1.01")));
        assertEquals("1.0120 BTC", formatCoinWithCode(Coin.parseCoin("1.012")));
        assertEquals("1012.30 BTC", formatCoinWithCode(Coin.parseCoin("1012.3")));
        assertEquals("1.0120 BTC", formatCoinWithCode(Coin.parseCoin("1.01200")));
        assertEquals("1.0123 BTC", formatCoinWithCode(Coin.parseCoin("1.01234")));

        assertEquals("1.2345 BTC", formatCoinWithCode(Coin.parseCoin("1.2345")));
        assertEquals("1.2346 BTC", formatCoinWithCode(Coin.parseCoin("1.23456")));
        assertEquals("1.2346 BTC", formatCoinWithCode(Coin.parseCoin("1.234567")));
        assertEquals("1.2345 BTC", formatCoinWithCode(Coin.parseCoin("1.23448")));

        assertEquals("1.00 BTC", formatCoinWithCode(Coin.COIN));
        assertEquals("1012.30 BTC", formatCoinWithCode(Coin.parseCoin("1012.3")));

        // change to mBTC
        useMilliBitFormat(true);
        assertEquals("1000.00 mBTC", formatCoinWithCode(Coin.COIN));
        assertEquals("1.00 mBTC", formatCoinWithCode(Coin.MILLICOIN));
        assertEquals("0.0010 mBTC", formatCoinWithCode(Coin.MICROCOIN));
    }


    @Test
    public void testParseToBtcWith4Decimals() {
        useMilliBitFormat(false);
        assertEquals(Coin.parseCoin("0"), parseToCoinWith4Decimals("0"));
        assertEquals(Coin.parseCoin("0"), parseToCoinWith4Decimals(null));
        assertEquals(Coin.parseCoin("0"), parseToCoinWith4Decimals("s"));
        assertEquals(Coin.parseCoin("0.0012"), parseToCoinWith4Decimals("0,00123"));
        assertEquals(Coin.parseCoin("0.0013"), parseToCoinWith4Decimals("0,00125"));
    }

    @Test
    public void testHasBtcValidDecimals() {
        useMilliBitFormat(false);
        setLocale(Locale.GERMAN);
        assertTrue(hasBtcValidDecimals(null));
        assertTrue(hasBtcValidDecimals("0"));
        assertTrue(hasBtcValidDecimals("0,0001"));
        assertTrue(hasBtcValidDecimals("0.0001"));
        assertTrue(hasBtcValidDecimals("0.0009"));
        assertTrue(hasBtcValidDecimals("20000000.0001"));
        assertFalse(hasBtcValidDecimals("20000000.000123"));
        assertFalse(hasBtcValidDecimals("0.00012"));
        assertFalse(hasBtcValidDecimals("0.0001222312312312313"));
    }

    @Test
    public void testParseToFiatWith2Decimals() {
        useMilliBitFormat(false);
        setLocale(Locale.GERMAN);
        assertEquals("0", parseToFiatWith2Decimals("0").toPlainString());
        assertEquals("0", parseToFiatWith2Decimals(null).toPlainString());
        assertEquals("0", parseToFiatWith2Decimals("s").toPlainString());
        assertEquals("0.12", parseToFiatWith2Decimals("0.123").toPlainString());
        assertEquals("0.13", parseToFiatWith2Decimals("0.125").toPlainString());
        assertEquals("0.13", parseToFiatWith2Decimals("0,125").toPlainString());
    }

    @Test
    public void testHasFiatValidDecimals() {
        useMilliBitFormat(false);
        setLocale(Locale.GERMAN);
        assertTrue(hasFiatValidDecimals(null));
        assertTrue(hasFiatValidDecimals("0"));
        assertTrue(hasFiatValidDecimals("0,01"));
        assertTrue(hasFiatValidDecimals("0.01"));
        assertTrue(hasFiatValidDecimals("0.09"));
        assertTrue(hasFiatValidDecimals("20000000.01"));
        assertFalse(hasFiatValidDecimals("20000000.0123"));
        assertFalse(hasFiatValidDecimals("0.012"));
        assertFalse(hasFiatValidDecimals("0.01222312312312313"));
    }

}
