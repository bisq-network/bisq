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

public class KnowYourDeveloperTest extends AbstractAssetTest {

    public KnowYourDeveloperTest() {
        super(new KnowYourDeveloper());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("Yezk3yX7A8sMsgiLN1DKBzhBNuosZLxyxv");
        assertValidAddress("YY9YLd5RzEVZZjkm2fsaWmQ2QP9aHcnCu9");
        assertValidAddress("YeJowNuWXx2ZVthswT5cLMQtMapfr7L9ch");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("yezk3yX7A8sMsgiLN1DKBzhBNuosZLxyxv");
        assertInvalidAddress("yY9YLd5RzEVZZjkm2fsaWmQ2QP9aHcnCu9");
        assertInvalidAddress("yeJowNuWXx2ZVthswT5cLMQtMapfr7L9ch");
    }
}
