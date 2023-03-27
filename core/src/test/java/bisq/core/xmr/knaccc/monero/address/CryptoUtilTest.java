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

package bisq.core.xmr.knaccc.monero.address;

import bisq.core.xmr.knaccc.monero.crypto.CryptoUtil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class CryptoUtilTest {
    @Test
    public void testWalletAddress() {
        String txKey = "6c336e52ed537676968ee319af6983c80b869ca6a732b5962c02748b486f8f0f";
        assertEquals(txKey, CryptoUtil.toCanonicalTxKey(txKey));
        assertEquals(txKey, CryptoUtil.toCanonicalTxKey(txKey.toUpperCase()));

        // key with 1 above l value (created with HexEncoder.getString(ensure32BytesAndConvertToLittleEndian(l.add(BigInteger.ONE).toByteArray())))
        txKey = "eed3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010";
        assertNotEquals(txKey, CryptoUtil.toCanonicalTxKey(txKey));
    }
}
