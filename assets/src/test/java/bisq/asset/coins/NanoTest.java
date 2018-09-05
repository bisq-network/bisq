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

public class NanoTest extends AbstractAssetTest {

    public NanoTest() {
        super(new Nano());
    }

    @Override
    public void testValidAddresses() {
        assertValidAddress("xrb_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3");
        assertValidAddress("xrb_35jjmmmh81kydepzeuf9oec8hzkay7msr6yxagzxpcht7thwa5bus5tomgz9");
        assertValidAddress("xrb_1111111111111111111111111111111111111111111111111111hifc8npp");
        assertValidAddress("nano_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3");
        assertValidAddress("nano_35jjmmmh81kydepzeuf9oec8hzkay7msr6yxagzxpcht7thwa5bus5tomgz9");
        assertValidAddress("nano_1111111111111111111111111111111111111111111111111111hifc8npp");
    }

    @Override
    public void testInvalidAddresses() {
        assertInvalidAddress("abc_1111111111111111111111111111111111111111111111111111hifc8npp"); // invalid prefix
        assertInvalidAddress("xrb_1211111111111111111111111111111111111111111111111111hifc8npp"); // invalid character
        assertInvalidAddress("XRB_1111111111111111111111111111111111111111111111111111HIFC8NPX"); // not lowercase
        assertInvalidAddress("xrb_1111111111111111111111111111111111111111111111111111111hifc8npp"); // too long
        assertInvalidAddress("xrb_11111111111111111111111111111111111111111111111hifc8npp"); // too short
        assertInvalidAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"); // not a nano address
        assertInvalidAddress("just completely wrong"); // not an address
        // checksum tests
        // assertInvalidAddress("xrb_1111111111111111111111111111111111111111111111111111hifc8npx"); // invalid checksum
        // assertInvalidAddress("xrb_1311111111111111111111111111111111111111111111111111hifc8npp"); // invalid checksum
    }
}
