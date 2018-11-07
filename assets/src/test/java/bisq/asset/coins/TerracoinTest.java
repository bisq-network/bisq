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

public class TerracoinTest extends AbstractAssetTest {

    public TerracoinTest() {
        super(new Terracoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("1Bys8pZaKo4GTWcpArMg92cBgYqij8mKXt");
        assertValidAddress("12Ycuof6g5GRyWy56eQ3NvJpwAM8z9pb4g");
        assertValidAddress("1DEBTTVCn1h9bQS9scVP6UjoSsjbtJBvXF");
        assertValidAddress("18s142HdWDfDQXYBpuyMvsU3KHwryLxnCr");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("18s142HdWDfDQXYBpyuMvsU3KHwryLxnCr");
        assertInvalidAddress("18s142HdWDfDQXYBpuyMvsU3KHwryLxnC");
        assertInvalidAddress("8s142HdWDfDQXYBpuyMvsU3KHwryLxnCr");
        assertInvalidAddress("18s142HdWDfDQXYBuyMvsU3KHwryLxnCr");
        assertInvalidAddress("1asdasd");
        assertInvalidAddress("asdasd");
    }
}
