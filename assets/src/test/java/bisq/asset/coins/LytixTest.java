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

public class LytixTest extends AbstractAssetTest {

    public LytixTest() {
        super(new Lytix());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("8hTBZgtuexiUVMxCPcrPgMem7jurB2YJds");
        assertValidAddress("8hqgpDE5uSuyRrDMXo1w3y59SCxfv8sSsf");
        assertValidAddress("8wtCT2JHu4wd4JqCwxnWFQXhmggnwdjpSn");
        assertValidAddress("8pYVReruVqYdp6LRhsy63nuVgsg9Rx7FJT");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("6pYVReruVqYdp6LRhsy63nuVgsg9Rx7FJT");
        assertInvalidAddress("8mgfRRiHVxf4JZH3pvffuY6NrKhffh13Q");
        assertInvalidAddress("8j75cPWABNXdZ62u6ZfF4tDQ1tVdvJx2oh7");
        assertInvalidAddress("FryiHzNPFatNV15hTurq9iFWeHTrQhUhG6");
        assertInvalidAddress("8ryiHzNPFatNV15hTurq9iFWefffQhUhG6");
        assertInvalidAddress("8ryigz2PFatNV15hTurq9iFWeHTrQhUhG1");
    }
}
