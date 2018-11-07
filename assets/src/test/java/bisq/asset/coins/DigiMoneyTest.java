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

public class DigiMoneyTest extends AbstractAssetTest {

    public DigiMoneyTest() {
        super(new DigiMoney());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("DvaAgcLKrno2AC7kYhHVDCrkhx2xHFpXUf");
        assertValidAddress("E9p49poRmnuLdnu55bzKe7t48xtYv2bRES");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("0xmnuL9poRmnuLd55bzKe7t48xtYv2bRES");
        assertInvalidAddress("DvaAgcLKrno2AC7kYhHVDC");
        assertInvalidAddress("19p49poRmnuLdnu55bzKe7t48xtYv2bRES");
    }
}
