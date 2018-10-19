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

public class OdinTest extends AbstractAssetTest {

    public OdinTest() {
        super(new Odin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("oLSvH9Vx9NsGj27dkEJ2UTnq8HaCpx36V8");
        assertValidAddress("oNsyRf7uuVjy1n5LtSSeff6TjrkH5frqfQ");
        assertValidAddress("oNENRRLSULi6bk2njMnAF8JQ54E1nRaUsh");
        assertValidAddress("oH3ZzfWi8zwiQza1V5eaKZimQUGJMzoUsM");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("PJCKDPyvfbf1yV7mYNeJ8Zb47hKRwVPYDj");
        assertInvalidAddress("XEfyuzk8yTp5eA9eVUeCW2PFbCFtNb6Jgv");
        assertInvalidAddress("PZdYWHgyhuG7NHVCzEkkx3dcLKurTpvmo6");
        assertInvalidAddress("PS6yeJnJUD2pe9fpDQvtm4KkLDwCWpa8ub");
        assertInvalidAddress("DFJku78A14HYwPSzC5PtUmda7jMr5pbD2B");
    }
}
