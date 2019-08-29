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

public class VertcoinTest extends AbstractAssetTest {

    public VertcoinTest() {
        super(new Vertcoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("VmVwB5dxph84tbi5XqRUtfX1MfmP8WpWWL");
        assertValidAddress("Vt85c1QcQYE318zXqZUnjUB6fwjTrf1Xkb");
        assertValidAddress("33ny4vAPJHFu5Nic7uMHQrvCACYTKPFJ5p");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("VmVwB5dxph84tb15XqRUtfX1MfmP8WpWWW");
        assertInvalidAddress("Vt85555555555555c1QcQYE318zXqZUnjUB6fwjTrf1Xkb");
        assertInvalidAddress("33ny4vAPJHFu5Nic7uMHQrvCACYTKPFJ6r#");
    }
}
