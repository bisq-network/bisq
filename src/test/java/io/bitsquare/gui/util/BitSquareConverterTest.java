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

import static org.junit.Assert.*;

public class BitSquareConverterTest {

    @Test
    public void testStringToDouble() {

        assertEquals(1, BSFormatter.parseToDouble("1"), 0);
        assertEquals(0.1, BSFormatter.parseToDouble("0.1"), 0);
        assertEquals(0.1, BSFormatter.parseToDouble("0,1"), 0);
        assertEquals(1, BSFormatter.parseToDouble("1.0"), 0);
        assertEquals(1, BSFormatter.parseToDouble("1,0"), 0);

        assertEquals(0, BSFormatter.parseToDouble("1,000.2"), 0);
        assertEquals(0, BSFormatter.parseToDouble("1,000.2"), 0);
        assertEquals(0, BSFormatter.parseToDouble(null), 0);
        assertEquals(0, BSFormatter.parseToDouble(""), 0);
        assertEquals(0, BSFormatter.parseToDouble(""), 0);
        assertEquals(0, BSFormatter.parseToDouble("."), 0);
        assertEquals(0, BSFormatter.parseToDouble(","), 0);
        assertEquals(0, BSFormatter.parseToDouble("a"), 0);
    }
}
