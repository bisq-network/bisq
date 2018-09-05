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

public class MyriadcoinTest extends AbstractAssetTest {

    public MyriadcoinTest() {
        super(new Myriadcoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("MHadFuyW1tp8SJ7fCxbFnxC2THXkuwE7uu");
        assertValidAddress("MCg3yZ15wnLQDSFguQLd2mQrCC1jL1MJU5");
        assertValidAddress("MCsTYDJNfXkafidZ7Nycw9wvV51vPGka9K");
        assertValidAddress("MQv8TxZvTUbN9d1KmDNrTku91Y5qhqbyfL");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1Qv8TxZvTUbN9d1KmDNrTku91Y5qhqbyfL");
        assertInvalidAddress("bMQv8TxZvTUbN9d1KmDNrTku91Y5qhqbyfL");
        assertInvalidAddress("ms142HdWDfDQXYBpuyMvsU3KHwryLxnCr");
        assertInvalidAddress("3ASDA8s142HdWDfDQXYBuyMvsU3KHwryLxnCr");
        assertInvalidAddress("1ddhisads");
        assertInvalidAddress("Mu9hdahudadad");
    }
}
