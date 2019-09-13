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

import org.junit.Test;

import bisq.asset.AbstractAssetTest;

public class VARIUSTest extends AbstractAssetTest {

    public VARIUSTest() {
        super(new VARIUS());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("VL85MGBCSfnSeiLxuQwXuvxHArzfr1574H");
        assertValidAddress("VBKxFQULC6bjzWdb2PhZyoRdePq8fs55fi");
        assertValidAddress("VXwmVvzX6KMqfkBJXRXu4VUbgzPhLKdBSq");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("xLfnSeiLxuQwXuvxHArzfr1574H");
        assertInvalidAddress("BBKzWdb2PhZyoRdePq8fs55fi");
        assertInvalidAddress("vXwmVvzX6KMqfkBJXRXu4VUbgzPhLKdBSq");
    }
}