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

public class CMBToken extends AbstractAssetTest {

    public CMBTokenTest() {
        super(new CMBToken());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("0x7ce477bbb5580f08ca0734e2223a0827f40adc40");
        String longAddress = String.join("", Collections.nCopies(2 * CMBToken.PAYLOAD_LIMIT, "af"));
        assertValidAddress(longAddress);
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1A");
        assertInvalidAddress("afa");
        assertInvalidAddress("1KfcXfeWhEPcqpF5a3fF6uXGaFEMxYgwJ6");
        String tooLongAddress = String.join("", Collections.nCopies(2 * CMBToken.PAYLOAD_LIMIT + 1, "af"));
        assertInvalidAddress(tooLongAddress);
    }
}
