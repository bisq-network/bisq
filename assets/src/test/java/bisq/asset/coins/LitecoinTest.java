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

public class LitecoinTest extends AbstractAssetTest {

    public LitecoinTest() {
        super(new Litecoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("Lg3PX8wRWmApFCoCMAsPF5P9dPHYQHEWKW");
        assertValidAddress("LTuoeY6RBHV3n3cfhXVVTbJbxzxnXs9ofm");
        assertValidAddress("LgfapHEPhZbRF9pMd5WPT35hFXcZS1USrW");
        assertValidAddress("ltc1qxtm55gultqzhqzl2p3ks50hg2478y3hehuj6dz");
        assertValidAddress("MGEW4aba3tnrVtVhGcmoqqHaLt5ymPSLPi");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1LgfapHEPhZbRF9pMd5WPT35hFXcZS1USrW");
        assertInvalidAddress("LgfapHEPhZbdRF9pMd5WPT35hFXcZS1USrW");
        assertInvalidAddress("LgfapHEPhZbRF9pMd5WPT35hFXcZS1USrW#");
        assertInvalidAddress("bc1qxtm55gultqzhqzl2p3ks50hg2478y3hehuj6dz");
        assertInvalidAddress("MGEW4aba3tnrVtVhGcmoqqHaLt5ymPSLPW");
    }
}
