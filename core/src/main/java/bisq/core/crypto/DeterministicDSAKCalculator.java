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

import com.google.common.collect.Range;

import org.bouncycastle.crypto.signers.DSAKCalculator;

import java.security.SecureRandom;

import java.math.BigInteger;

import java.util.Arrays;

public abstract class DeterministicDSAKCalculator implements DSAKCalculator {
    Range<BigInteger> kRange;

    @Override
    public boolean isDeterministic() {
        return true;
    }

    @Override
    public void init(BigInteger n, SecureRandom random) {
        throw new IllegalStateException("Operation not supported");
    }

    @Override
    public void init(BigInteger n, BigInteger d, byte[] message) {
        kRange = Range.closedOpen(BigInteger.ONE, n);
        init(n, d, toScalar(message, n).mod(n));
    }

    abstract void init(BigInteger n, BigInteger d, BigInteger e);

    static byte[] toByteArrayBE(BigInteger k, int len) {
        byte[] guardedKBytes = k.or(BigInteger.ONE.shiftLeft(len * 8)).toByteArray();
        return Arrays.copyOfRange(guardedKBytes, guardedKBytes.length - len, guardedKBytes.length);
    }

    static BigInteger toScalar(byte[] kBytes, BigInteger n) {
        return new BigInteger(1, kBytes).shiftRight(Math.max(kBytes.length * 8 - n.bitLength(), 0));
    }
}
