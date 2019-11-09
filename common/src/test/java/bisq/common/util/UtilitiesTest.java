/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.common.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UtilitiesTest {

    @Test
    public void testToStringList() {
        assertTrue(Utilities.commaSeparatedListToSet(null, false).isEmpty());
        assertTrue(Utilities.commaSeparatedListToSet(null, true).isEmpty());
        assertTrue(Utilities.commaSeparatedListToSet("", false).isEmpty());
        assertTrue(Utilities.commaSeparatedListToSet("", true).isEmpty());
        assertTrue(Utilities.commaSeparatedListToSet(" ", false).isEmpty());
        assertEquals(1, Utilities.commaSeparatedListToSet(" ", true).size());
        assertTrue(Utilities.commaSeparatedListToSet(",", false).isEmpty());
        assertTrue(Utilities.commaSeparatedListToSet(",", true).isEmpty());
        assertEquals(1, Utilities.commaSeparatedListToSet(",test1", false).size());
        assertEquals(1, Utilities.commaSeparatedListToSet(", , test1", false).size());
        assertEquals(2, Utilities.commaSeparatedListToSet(", , test1", true).size());
        assertEquals(1, Utilities.commaSeparatedListToSet("test1,", false).size());
        assertEquals(1, Utilities.commaSeparatedListToSet("test1, ,", false).size());
        assertEquals(1, Utilities.commaSeparatedListToSet("test1", false).size());
        assertEquals(2, Utilities.commaSeparatedListToSet("test1, test2", false).size());
    }

    @Test
    public void testIntegerToByteArray() {
        assertEquals("0000", Utilities.bytesAsHexString(Utilities.integerToByteArray(0, 2)));
        assertEquals("ffff", Utilities.bytesAsHexString(Utilities.integerToByteArray(65535, 2)));
        assertEquals("0011", Utilities.bytesAsHexString(Utilities.integerToByteArray(17, 2)));
        assertEquals("1100", Utilities.bytesAsHexString(Utilities.integerToByteArray(4352, 2)));
        assertEquals("dd22", Utilities.bytesAsHexString(Utilities.integerToByteArray(56610, 2)));
        assertEquals("7fffffff", Utilities.bytesAsHexString(Utilities.integerToByteArray(2147483647, 4))); // Integer.MAX_VALUE
        assertEquals("80000000", Utilities.bytesAsHexString(Utilities.integerToByteArray(-2147483648, 4))); // Integer.MIN_VALUE
        assertEquals("00110011", Utilities.bytesAsHexString(Utilities.integerToByteArray(1114129, 4)));
        assertEquals("ffeeffef", Utilities.bytesAsHexString(Utilities.integerToByteArray(-1114129, 4)));
    }

    @Test
    public void testByteArrayToInteger() {
        assertEquals(0, Utilities.byteArrayToInteger(Utilities.decodeFromHex("0000")));
        assertEquals(65535, Utilities.byteArrayToInteger(Utilities.decodeFromHex("ffff")));
        assertEquals(4352, Utilities.byteArrayToInteger(Utilities.decodeFromHex("1100")));
        assertEquals(17, Utilities.byteArrayToInteger(Utilities.decodeFromHex("0011")));
        assertEquals(56610, Utilities.byteArrayToInteger(Utilities.decodeFromHex("dd22")));
        assertEquals(2147483647, Utilities.byteArrayToInteger(Utilities.decodeFromHex("7fffffff")));
        assertEquals(-2147483648, Utilities.byteArrayToInteger(Utilities.decodeFromHex("80000000")));
        assertEquals(1114129, Utilities.byteArrayToInteger(Utilities.decodeFromHex("00110011")));
        assertEquals(-1114129, Utilities.byteArrayToInteger(Utilities.decodeFromHex("ffeeffef")));
    }
}
