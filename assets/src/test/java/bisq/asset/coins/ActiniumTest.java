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

public class ActiniumTest extends AbstractAssetTest {

    public ActiniumTest() {
        super(new Actinium());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("NLzB9iUGJ8GaKSn9GfVKfd55QVRdNdz9FK");
        assertValidAddress("NSz7PKmo1sLQYtFuZjTQ1zZXhPQtHLScKT");
        assertValidAddress("NTFtsh4Ff2ijPNsnQAUf5fKTp7DJaGxSZK");
        assertValidAddress("PLRiNpnTzWqufAoRFN1u9zBstHqjyM2qgB");
        assertValidAddress("PMFpWHR2AbBwaR4G2rA5nWB1F7cbZWua5Z");
        assertValidAddress("P9XE6tupGocWnsNgoUxRPzASYAPVAyu2T8");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("MgTFtsh4Ff2ijPNsnQAUf5fKTp7DJaGxSZK");
        assertInvalidAddress("F9z7PKmo1sLQYtFuZjTQ1zZXhPQtHLScKT");
        assertInvalidAddress("16Ftsh4Ff2ijPNsnQAUf5fKTp7DJaGxSZK");
        assertInvalidAddress("Z6Ftsh7LfGijPVzmQAUf5fKTp7DJaGxSZK");
        assertInvalidAddress("G5Fmxy4Ff2ijLjsnQAUf5fKTp7DJaGxACV");
        assertInvalidAddress("D4Hmqy4Ff2ijXYsnQAUf5fKTp7DJaGxBhJ");
    }
}
