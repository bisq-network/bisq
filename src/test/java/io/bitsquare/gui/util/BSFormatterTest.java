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

import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.BSFormatter.*;
import static org.junit.Assert.*;

public class BSFormatterTest {
    private static final Logger log = LoggerFactory.getLogger(BSFormatterTest.class);

    @Test
    public void testParseToBtcWith4Decimals() {

        assertEquals("0", parseToBtcWith4Decimals("0").toPlainString());
        assertEquals("0", parseToBtcWith4Decimals(null).toPlainString());
        assertEquals("0", parseToBtcWith4Decimals("s").toPlainString());
        assertEquals("0.0012", parseToBtcWith4Decimals("0.00123").toPlainString());
        assertEquals("0.0013", parseToBtcWith4Decimals("0.00125").toPlainString());
        assertEquals("0.0013", parseToBtcWith4Decimals("0,00125").toPlainString());
    }

    @Test
    public void testHasBtcValidDecimals() {
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

        assertEquals("0", parseToFiatWith2Decimals("0").toPlainString());
        assertEquals("0", parseToFiatWith2Decimals(null).toPlainString());
        assertEquals("0", parseToFiatWith2Decimals("s").toPlainString());
        assertEquals("0.12", parseToFiatWith2Decimals("0.123").toPlainString());
        assertEquals("0.13", parseToFiatWith2Decimals("0.125").toPlainString());
        assertEquals("0.13", parseToFiatWith2Decimals("0,125").toPlainString());
    }

    @Test
    public void testHasFiatValidDecimals() {
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
