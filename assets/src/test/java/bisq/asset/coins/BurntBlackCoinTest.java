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
import java.util.Collections;
import org.junit.Test;

public class BurntBlackCoinTest extends AbstractAssetTest {

    public BurntBlackCoinTest() {
        super(new BurntBlackCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("4b");
        assertValidAddress("536865206d616b657320796f75206275726e207769746820612077617665206f66206865722068616e64");
        String longAddress = String.join("", Collections.nCopies(2 * BurntBlackCoin.PAYLOAD_LIMIT, "af"));
        assertValidAddress(longAddress);
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("AF");
        assertInvalidAddress("afa");
        assertInvalidAddress("B4Wa1C8zFgkSY4daLg8jWnxuKpw7UmWFoo");
        String tooLongAddress = String.join("", Collections.nCopies(2 * BurntBlackCoin.PAYLOAD_LIMIT + 1, "af"));
        assertInvalidAddress(tooLongAddress);
    }
}
