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

public class YentenTest extends AbstractAssetTest {

    public YentenTest() {
        super(new Yenten());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("YTgSv7bk5x5p6te3uf3HbUwgnf7zEJM4Jn");
        assertValidAddress("YVz19KtQUfyTP4AJS8sbRBqi7dkGTL2ovd");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("YiTwGuv3opowtPF5w8LUWBXFmaxc9S68ha");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq");
        assertInvalidAddress("YVZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO");
        assertInvalidAddress("YiTwGuv3opowtPF5w8LUWBlFmaxc9S68hz");
        assertInvalidAddress("YiTwGuv3opowtPF5w8LUWB0Fmaxc9S68hz");
        assertInvalidAddress("YiTwGuv3opowtPF5w8LUWBIFmaxc9S68hz");
    }
}
