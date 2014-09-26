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

import io.bitsquare.user.User;

import com.google.bitcoin.core.Coin;

import java.util.Locale;

import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class BSFormatterTest {
    private static final Logger log = LoggerFactory.getLogger(BSFormatterTest.class);

    @Test
    public void testParseToBtc() {
        BSFormatter formatter = new BSFormatter(new User());
        formatter.useMilliBitFormat(false);
        assertEquals(Coin.ZERO, formatter.parseToCoin("0"));
        assertEquals(Coin.COIN, formatter.parseToCoin("1"));
        assertEquals(Coin.SATOSHI, formatter.parseToCoin("0,00000001"));

        assertEquals(Coin.parseCoin("-1"), formatter.parseToCoin("-1"));
        assertEquals(Coin.parseCoin("1.1"), formatter.parseToCoin("1,1"));
        assertEquals(Coin.parseCoin("1.1"), formatter.parseToCoin("1.1"));
        assertEquals(Coin.parseCoin("0"), formatter.parseToCoin("1.123,45"));
        assertEquals(Coin.parseCoin("0"), formatter.parseToCoin("1,123.45"));

        assertEquals(Coin.parseCoin("1.1234"), formatter.parseToCoinWith4Decimals("1,12342"));
        assertEquals(Coin.parseCoin("1.1235"), formatter.parseToCoinWith4Decimals("1,12345"));
        assertEquals(Coin.parseCoin("1.1230"), formatter.parseToCoinWith4Decimals("1,123"));

        // change to mBTC
        formatter.useMilliBitFormat(true);
        assertEquals(Coin.parseCoin("1"), formatter.parseToCoin("1000"));
        assertEquals(Coin.parseCoin("0.123"), formatter.parseToCoin("123"));
        assertEquals(Coin.parseCoin("0.1234"), formatter.parseToCoin("123.4"));
        assertEquals(Coin.parseCoin("0.12345"), formatter.parseToCoin("123.45"));
        assertEquals(Coin.parseCoin("0.123456"), formatter.parseToCoin("123.456"));
        assertEquals(Coin.parseCoin("0"), formatter.parseToCoin("123,456.7"));

        assertEquals(Coin.parseCoin("0.001123"), formatter.parseToCoinWith4Decimals("1.123"));
        assertEquals(Coin.parseCoin("0.0011234"), formatter.parseToCoinWith4Decimals("1.1234"));
        assertEquals(Coin.parseCoin("0.0011234"), formatter.parseToCoinWith4Decimals("1.12342"));
        assertEquals(Coin.parseCoin("0.0011235"), formatter.parseToCoinWith4Decimals("1.12345"));
    }

    @Test
    public void testFormatCoin() {
        BSFormatter formatter = new BSFormatter(new User());
        formatter.useMilliBitFormat(false);
        assertEquals("1.00", formatter.formatCoin(Coin.COIN));
        assertEquals("1.0120", formatter.formatCoin(Coin.parseCoin("1.012")));
        assertEquals("1012.30", formatter.formatCoin(Coin.parseCoin("1012.3")));
        assertEquals("1.0120", formatter.formatCoin(Coin.parseCoin("1.01200")));
        assertEquals("1.0123", formatter.formatCoin(Coin.parseCoin("1.01234")));

        assertEquals("1.2345", formatter.formatCoin(Coin.parseCoin("1.2345")));
        assertEquals("1.2346", formatter.formatCoin(Coin.parseCoin("1.23456")));
        assertEquals("1.2346", formatter.formatCoin(Coin.parseCoin("1.234567")));
        assertEquals("1.2345", formatter.formatCoin(Coin.parseCoin("1.23448")));

        assertEquals("1.00", formatter.formatCoin(Coin.COIN));
        assertEquals("1012.30", formatter.formatCoin(Coin.parseCoin("1012.3")));

        // change to mBTC
        formatter.useMilliBitFormat(true);
        assertEquals("1000.00", formatter.formatCoin(Coin.COIN));
        assertEquals("1.00", formatter.formatCoin(Coin.MILLICOIN));
        assertEquals("0.0010", formatter.formatCoin(Coin.MICROCOIN));
    }

    @Test
    public void testFormatCoinWithCode() {
        BSFormatter formatter = new BSFormatter(new User());
        formatter.useMilliBitFormat(false);
        assertEquals("1.00 BTC", formatter.formatCoinWithCode(Coin.COIN));
        assertEquals("1.01 BTC", formatter.formatCoinWithCode(Coin.parseCoin("1.01")));
        assertEquals("1.0120 BTC", formatter.formatCoinWithCode(Coin.parseCoin("1.012")));
        assertEquals("1012.30 BTC", formatter.formatCoinWithCode(Coin.parseCoin("1012.3")));
        assertEquals("1.0120 BTC", formatter.formatCoinWithCode(Coin.parseCoin("1.01200")));
        assertEquals("1.0123 BTC", formatter.formatCoinWithCode(Coin.parseCoin("1.01234")));

        assertEquals("1.2345 BTC", formatter.formatCoinWithCode(Coin.parseCoin("1.2345")));
        assertEquals("1.2346 BTC", formatter.formatCoinWithCode(Coin.parseCoin("1.23456")));
        assertEquals("1.2346 BTC", formatter.formatCoinWithCode(Coin.parseCoin("1.234567")));
        assertEquals("1.2345 BTC", formatter.formatCoinWithCode(Coin.parseCoin("1.23448")));

        assertEquals("1.00 BTC", formatter.formatCoinWithCode(Coin.COIN));
        assertEquals("1012.30 BTC", formatter.formatCoinWithCode(Coin.parseCoin("1012.3")));

        // change to mBTC
        formatter.useMilliBitFormat(true);
        assertEquals("1000.00 mBTC", formatter.formatCoinWithCode(Coin.COIN));
        assertEquals("1.00 mBTC", formatter.formatCoinWithCode(Coin.MILLICOIN));
        assertEquals("0.0010 mBTC", formatter.formatCoinWithCode(Coin.MICROCOIN));
    }


    @Test
    public void testParseToBtcWith4Decimals() {
        BSFormatter formatter = new BSFormatter(new User());
        formatter.useMilliBitFormat(false);
        assertEquals(Coin.parseCoin("0"), formatter.parseToCoinWith4Decimals("0"));
        assertEquals(Coin.parseCoin("0"), formatter.parseToCoinWith4Decimals(null));
        assertEquals(Coin.parseCoin("0"), formatter.parseToCoinWith4Decimals("s"));
        assertEquals(Coin.parseCoin("0.0012"), formatter.parseToCoinWith4Decimals("0,00123"));
        assertEquals(Coin.parseCoin("0.0013"), formatter.parseToCoinWith4Decimals("0,00125"));
    }

    @Test
    public void testHasBtcValidDecimals() {
        BSFormatter formatter = new BSFormatter(new User());
        formatter.useMilliBitFormat(false);
        formatter.setLocale(Locale.GERMAN);
        assertTrue(formatter.hasBtcValidDecimals(null));
        assertTrue(formatter.hasBtcValidDecimals("0"));
        assertTrue(formatter.hasBtcValidDecimals("0,0001"));
        assertTrue(formatter.hasBtcValidDecimals("0.0001"));
        assertTrue(formatter.hasBtcValidDecimals("0.0009"));
        assertTrue(formatter.hasBtcValidDecimals("20000000.0001"));
        assertFalse(formatter.hasBtcValidDecimals("20000000.000123"));
        assertFalse(formatter.hasBtcValidDecimals("0.00012"));
        assertFalse(formatter.hasBtcValidDecimals("0.0001222312312312313"));
    }

    @Test
    public void testParseToFiatWith2Decimals() {
        BSFormatter formatter = new BSFormatter(new User());
        formatter.useMilliBitFormat(false);
        formatter.setLocale(Locale.GERMAN);
        assertEquals("0", formatter.parseToFiatWith2Decimals("0").toPlainString());
        assertEquals("0", formatter.parseToFiatWith2Decimals(null).toPlainString());
        assertEquals("0", formatter.parseToFiatWith2Decimals("s").toPlainString());
        assertEquals("0.12", formatter.parseToFiatWith2Decimals("0.123").toPlainString());
        assertEquals("0.13", formatter.parseToFiatWith2Decimals("0.125").toPlainString());
        assertEquals("0.13", formatter.parseToFiatWith2Decimals("0,125").toPlainString());
    }

    @Test
    public void testHasFiatValidDecimals() {
        BSFormatter formatter = new BSFormatter(new User());
        formatter.useMilliBitFormat(false);
        formatter.setLocale(Locale.GERMAN);
        assertTrue(formatter.hasFiatValidDecimals(null));
        assertTrue(formatter.hasFiatValidDecimals("0"));
        assertTrue(formatter.hasFiatValidDecimals("0,01"));
        assertTrue(formatter.hasFiatValidDecimals("0.01"));
        assertTrue(formatter.hasFiatValidDecimals("0.09"));
        assertTrue(formatter.hasFiatValidDecimals("20000000.01"));
        assertFalse(formatter.hasFiatValidDecimals("20000000.0123"));
        assertFalse(formatter.hasFiatValidDecimals("0.012"));
        assertFalse(formatter.hasFiatValidDecimals("0.01222312312312313"));
    }

}
