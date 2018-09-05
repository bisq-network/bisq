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

public class CassubianDetkTest extends AbstractAssetTest {

    public CassubianDetkTest() {
        super(new CassubianDetk());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("DM7BjopQ3bGYxSPZ4yhfttxqnDrEkyc3sw");
        assertValidAddress("DB4CaJ81SiT3VtpGC8K1RMirPJZjsmKiZd");
        assertValidAddress("DE7uB1mws1RwYNDPpfEnQ7a4i9tdqqV1Lf");
        assertValidAddress("DJ8FnzVCa8AXEBt5aqPcJubKRzanQKvxkY");
        assertValidAddress("D5QmzfBjwrUyAKtvectJL7kBawfWtwdJqz");
        assertValidAddress("DDRJemKbVtTVV8r2jSG9wevv3JeUj2edAr");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq");
        assertInvalidAddress("0x2a65Aca4D5fC5B5C859090a6c34d1641353982266");
        assertInvalidAddress("SSnwqFBiyqK1n4BV7kPX86iesev2NobhEo");
    }
}
