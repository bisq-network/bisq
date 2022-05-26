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

package knaccc.monero.address;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WalletAddressTest {
    @Test
    public void testWalletAddress() throws WalletAddress.InvalidWalletAddressException {
        String mainAddress = "43amHgM9cDHhJY8tAujYi4MisCx4dvNQB5xVYbRLqPYLbVmH5qHcUgsjgsdoSdLK3TgRaBd68bCLaRcK8VakCUAJLGjz42G";
        WalletAddress walletAddress = new WalletAddress(mainAddress);

        String privateViewKeyHex = "7b37d8922245a07244fd31855d1e705a590a9bd2881825f0542ad99cdaba090a";

        System.out.println("subaddress for account index 0, subaddress index 1: "
                + walletAddress.getSubaddressBase58(privateViewKeyHex, 0, 1));


        // tests
        String addr00 = "43amHgM9cDHhJY8tAujYi4MisCx4dvNQB5xVYbRLqPYLbVmH5qHcUgsjgsdoSdLK3TgRaBd68bCLaRcK8VakCUAJLGjz42G";
        String addr01 = "8B3QYUXKj8ySWiCaF79NyS6RJBkkRmNpQiCMKHkHhE7J67joNdt1Wf7gxFKw8EnXxofpVhdSsg61JQnR2jbeEyW2CM5sqvY";
        String addr10 = "83YULqcGNVzMA4ehBN8uwP4tiJYGBw3Zo8LAEod1rtvd4WfATg9LHZbd8tbnNrosb3Fri7HdXSPyF2hPBQend6A3LQWymPt";
        String addr11 = "8AZFX2Ledf8hhb5RTt9vsbGfc6CJW4SviWMgpFy9LCmKJzg6ZCyKR2nEBtiz8v8QXheoCPLFGi1HpEtyBju8aUA6Bkreqhr";

        assertEquals(walletAddress.getBase58(), mainAddress);
        assertEquals(mainAddress, addr00);
        assertEquals(walletAddress.getSubaddressBase58(privateViewKeyHex, 0, 1), addr01);
        assertEquals(walletAddress.getSubaddressBase58(privateViewKeyHex, 1, 0), addr10);
        assertEquals(walletAddress.getSubaddressBase58(privateViewKeyHex, 1, 1), addr11);
    }
}
