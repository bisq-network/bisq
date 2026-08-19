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

package bisq.common.crypto;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.DSAPublicKeySpec;

import java.math.BigInteger;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SigTest {
    @Test
    public void acceptsKeysWeGenerateOurselves() {
        byte[] encoded = Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic());

        assertTrue(encoded.length <= Sig.MAX_SIG_PUB_KEY_BYTES,
                "Our own keys must fit into the accepted size, but were " + encoded.length + " bytes");
        assertEquals(encoded.length, Sig.getPublicKeyBytes(Sig.getPublicKeyFromBytes(encoded)).length);
    }

    @Test
    public void rejectsOversizedEncoding() {
        byte[] encoded = Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic());
        byte[] oversized = Arrays.copyOf(encoded, Sig.MAX_SIG_PUB_KEY_BYTES + 1);

        assertThrows(KeyConversionException.class, () -> Sig.getPublicKeyFromBytes(oversized));
    }

    // A hand crafted SPKI can carry a large modulus in few bytes by pairing it with a tiny generator and public
    // value, so the encoded size alone does not bound the cost of a verification.
    @Test
    public void rejectsSmallEncodingCarryingAnOversizedModulus() throws Exception {
        BigInteger p = BigInteger.ONE.shiftLeft(2560).subtract(BigInteger.ONE);
        BigInteger q = BigInteger.ONE.shiftLeft(159).setBit(159).nextProbablePrime();
        PublicKey key = KeyFactory.getInstance(Sig.KEY_ALGO)
                .generatePublic(new DSAPublicKeySpec(BigInteger.valueOf(3), p, q, BigInteger.TWO));
        byte[] encoded = key.getEncoded();

        assertTrue(encoded.length <= Sig.MAX_SIG_PUB_KEY_BYTES,
                "Test premise: the oversized modulus must pass the size check, but the encoding was "
                        + encoded.length + " bytes");
        assertThrows(KeyConversionException.class, () -> Sig.getPublicKeyFromBytes(encoded));
    }

    @Test
    public void rejectsMalformedEncoding() {
        assertThrows(KeyConversionException.class, () -> Sig.getPublicKeyFromBytes(new byte[]{1, 2, 3}));
    }
}
