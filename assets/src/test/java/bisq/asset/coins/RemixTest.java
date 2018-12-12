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

public class RemixTest extends AbstractAssetTest {

    public RemixTest() {
        super(new Remix());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("REMXisBbsyWKYdENidNhiP3bGaVwVgtescK2ZuJMtxed4TqJGH8VX57gMSTyfC43FULSM4XXzmj727SGjDNak16mGaYdban4o4m");
        assertValidAddress("REMXiqQhgfqWtZ1gfxP4iDbXEV4f8cUDFAp2Bz43PztJSJvv2mUqG4Z2YFBMauJV74YCDcJLyqkbCfsC55LNJhQfZxdiE5tGxKq");
        assertValidAddress("SubRM7BgZyGiccN3pKuRPrN52FraE9j7miu17MDwx6wWb7J6XWeDykk48JBZ3QVSXR7GJWr2RdpjK3YCRAUdTbfRL4wGAn7oggi");
        assertValidAddress("SubRM9N9dmoeawsXqNt94jVn6vSurYxxU3E6mEoMnzWvAMB7QjL3Zc9dmKTD64wE5ePFfACVLVLTZZa6GKVp6FuZ7Z9dJheMoJb");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("REMXiqQhgfqWtZ1gfxP4iDbXEV4f8cUDFAp2Bz43PztJSJvv2mUqG4Z2YFBMauJV74YCDcJLyqkbCfsC55LNJhQ");
        assertInvalidAddress("REMXIqQhgfqWtZ1gfxP4iDbXEV4f8cUDFApdfgdfgdfgdfgr4453453453444JV74YCDcJLyqkbCfsC55LNJhQfZxdiE5tGxKq");
        assertInvalidAddress("REMXiqQhgfqWtZ1gfxP4iDbXEV4f8cUDFAp2Bz43PztJS4dssdffffsdfsdfffffdfgdfgsaqkbCfsC4iDbXEV4f8cUDFAp2Bz");
        assertInvalidAddress("SubRM9N9dmoeawsXqNt94jVn6vSurYxxU3E6mEoMnzWvAMB7QL3Zc9dmKTD64wE5ePFfACVLVLTZZa6GKVp6FuZ7Z9dJheMo69");
        assertInvalidAddress("SubRM9N9dmoeawsXqNt94jdfsdfsdfsdfsdfsdfJb");
        assertInvalidAddress("SubrM9N9dmoeawsXqNt94jVn6vSfeet");
    }
}
