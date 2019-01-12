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

public class IridiumTest extends AbstractAssetTest {
    public IridiumTest() {
        super(new Iridium());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("ir2oHYW7MbBQuMzTELg5o6FRqXNwWCU1wNzFsJG3VUCT9qMwayNsdwaQ85NHC3vLFSQ1eWtAPsYpvV4tXpnXKM9M377BW5KQ4");
        assertValidAddress("ir2PK6y3hjq9wLqdTQnPQ2FXhCJqJ1pKXNXezZUqeUWbTb3T74Xqiy1Yqwtkgri934C1E9Ba2quJDDh75nxDqEQj1K8i9DQXf");
        assertValidAddress("ir3steHWr1FRbtpjWWCAaxhzNggzJK6tqBy3qFw32YGV4CJdRsgYrpLifA7ivGdgZGNRKbRtYUp9GKvxnFSRFWTt2XuWunRYb");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("ir2oHYW7MbBQuMzTELg5o6FRqXNwWCU1wNzFsJG3VUCT9qMwayNsdwaQ85NHC3vLFSQ1eWtAPsYpvV4tXpnXKM9M377BW5KQ4t");
        assertInvalidAddress("ir2PK6y3hjq9wLqdTQnPQ2FXhCJqJ1pKXNXezZUqeUWb#Tb3T74Xqiy1Yqwtkgri934C1E9Ba2quJDDh75nxDqEQj1K8i9DQXf");
        assertInvalidAddress("");
    }
}
