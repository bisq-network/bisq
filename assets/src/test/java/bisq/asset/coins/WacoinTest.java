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

public class WacoinTest extends AbstractAssetTest {

    public WacoinTest() {
        super(new Wacoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("WfEnB3VGrBqW7uamJMymymEwxMBYQKELKY");
        assertValidAddress("WTLWtNN5iJJQyTeMfZMMrfrDvdGZrYGP5U");
        assertValidAddress("WemK3MgwREsEaF4vdtYLxmMqAXp49C2LYQ");
        assertValidAddress("WZggcFY5cJdAxx9unBW5CVPAH8VLTxZ6Ym");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("abcde");
        assertInvalidAddress("mWvZ7nZAUzpRMFp2Bfjxz27Va47nUfB79E");
        assertInvalidAddress("WemK3MgwREsE23fgsadtYLxmMqAX9C2LYQ");
    }
}
