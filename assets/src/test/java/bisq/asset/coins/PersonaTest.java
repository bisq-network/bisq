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

package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class PersonaTest extends AbstractAssetTest {

    public PersonaTest() {
        super(new Persona());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("PV5PbsyhkM1RkN41QiSXy7cisbZ4kBzm51");
        assertValidAddress("PJACMZ2tMMZzQ8H9mWPHJcB7uYP47BM2zA");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("LJACMZ2tMMZzQ8H9mWPHJcB7uYP47BM2zA");
        assertInvalidAddress("TJACMZ2tMMZzQ8H9mWPHJcB7uYP47BM2zA");
        assertInvalidAddress("PJACMZ2tMMZzQ8H9mWPHJcB7uYP47BM2zAA");
        assertInvalidAddress("PlACMZ2tMMZzQ8H9mWPHJcB7uYP47BM2zA");
        assertInvalidAddress("PIACMZ2tMMZzQ8H9mWPHJcB7uYP47BM2zA");
        assertInvalidAddress("POACMZ2tMMZzQ8H9mWPHJcB7uYP47BM2zA");
        assertInvalidAddress("P0ACMZ2tMMZzQ8H9mWPHJcB7uYP47BM2zA");
    }
}


