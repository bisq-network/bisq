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

package bisq.core.filter;

import bisq.common.crypto.Sig;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;

import java.util.Arrays;

import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class FilterWithExcludeForHashFieldTests {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final PublicKey ownerPublicKey;

    public FilterWithExcludeForHashFieldTests() throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(Sig.KEY_ALGO, "BC");
        KeyPair ownerKeyPair = keyPairGenerator.generateKeyPair();
        ownerPublicKey = ownerKeyPair.getPublic();
    }

    @Test
    void testSerializeForHashAnnotation() {
        Filter filterWithoutSig = TestFilter.createFilter(ownerPublicKey, "invalidPubKeyAsHex");
        byte[] serializeForHashBytes = filterWithoutSig.serializeForHash();
        byte[] serializeBytes = filterWithoutSig.serialize();
        assertFalse(Arrays.equals(serializeForHashBytes, serializeBytes));
        assertTrue(serializeBytes.length > serializeForHashBytes.length);
    }
}
