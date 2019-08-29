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

public class KoreTest extends AbstractAssetTest {

    public KoreTest() {
        super(new Kore());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("KViqqCDcdZn3DKJWGvmdUtmoDsxuGswzwU");
        assertValidAddress("KNnThWKeyJ5ibYL3JhuBacyjJxKXs2cXgv");
        assertValidAddress("bGcebbVyKD4PEBHeKRGX7cTydu1xRm63r4");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("KmVwB5dxph84tb15XqRUtfX1MfmP8WpWWW");
        assertInvalidAddress("Kt85555555555555c1QcQYE318zXqZUnjUB6fwjTrf1Xkb");
        assertInvalidAddress("33ny4vAPJHFu5Nic7uMHQrvCACYTKPFJ6r#");
    }
}
