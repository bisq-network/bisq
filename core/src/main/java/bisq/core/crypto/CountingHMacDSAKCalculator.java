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

package bisq.core.crypto;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.prng.EntropySource;
import org.bouncycastle.crypto.prng.drbg.HMacSP800DRBG;
import org.bouncycastle.crypto.prng.drbg.SP80090DRBG;

import java.math.BigInteger;

import java.util.Arrays;

public class CountingHMacDSAKCalculator extends DeterministicDSAKCalculator {
    private final HMac hMac = new HMac(new SHA256Digest());
    private SP80090DRBG drbg;
    private BigInteger n;
    private int counter;

    @Override
    void init(BigInteger n, BigInteger d, BigInteger e) {
        int len = (n.bitLength() + 7) / 8;
        int securityStrength = Math.min(n.bitLength(), 256);
        byte[] dBytes = toByteArrayBE(d, len);
        byte[] eBytes = toByteArrayBE(e, len);
        byte[] additionalData = counter != 0 ? toByteArrayLE(counter, hMac.getMacSize()) : null;
        drbg = new HMacSP800DRBG(hMac, securityStrength, getEntropySource(dBytes), additionalData, eBytes);
        this.n = n;
    }

    @Override
    public BigInteger nextK() {
        byte[] kBytes = new byte[(n.bitLength() + 7) / 8];
        BigInteger k;
        do {
            drbg.generate(kBytes, null, false);
        } while (!kRange.contains(k = toScalar(kBytes, n)));
        counter++;
        return k;
    }

    private static byte[] toByteArrayLE(int i, int len) {
        return Arrays.copyOf(new byte[]{(byte) i, (byte) (i >> 8), (byte) (i >> 16), (byte) (i >> 24)}, len);
    }

    private EntropySource getEntropySource(byte[] dBytes) {
        return new EntropySource() {
            @Override
            public boolean isPredictionResistant() {
                return false;
            }

            @Override
            public byte[] getEntropy() {
                return dBytes;
            }

            @Override
            public int entropySize() {
                return dBytes.length * 8;
            }
        };
    }
}
