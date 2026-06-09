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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PubKeyRingTest {
    @Test
    public void rejectsNullOrEmptyPublicKeyBytes() {
        byte[] validSignaturePubKeyBytes = Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic());
        byte[] validEncryptionPubKeyBytes = Encryption.getPublicKeyBytes(Encryption.generateKeyPair().getPublic());

        assertThrows(IllegalArgumentException.class,
                () -> new PubKeyRing(null, validEncryptionPubKeyBytes));
        assertThrows(IllegalArgumentException.class,
                () -> new PubKeyRing(validSignaturePubKeyBytes, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PubKeyRing(new byte[0], validEncryptionPubKeyBytes));
        assertThrows(IllegalArgumentException.class,
                () -> new PubKeyRing(validSignaturePubKeyBytes, new byte[0]));
    }

    @Test
    public void copiesPublicKeyByteArrays() {
        byte[] signaturePubKeyBytes = Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic());
        byte[] encryptionPubKeyBytes = Encryption.getPublicKeyBytes(Encryption.generateKeyPair().getPublic());
        byte[] expectedSignaturePubKeyBytes = signaturePubKeyBytes.clone();
        byte[] expectedEncryptionPubKeyBytes = encryptionPubKeyBytes.clone();

        PubKeyRing pubKeyRing = new PubKeyRing(signaturePubKeyBytes, encryptionPubKeyBytes);
        signaturePubKeyBytes[0] ^= 1;
        encryptionPubKeyBytes[0] ^= 1;

        assertArrayEquals(expectedSignaturePubKeyBytes, pubKeyRing.getSignaturePubKeyBytes());
        assertArrayEquals(expectedEncryptionPubKeyBytes, pubKeyRing.getEncryptionPubKeyBytes());

        byte[] returnedSignaturePubKeyBytes = pubKeyRing.getSignaturePubKeyBytes();
        byte[] returnedEncryptionPubKeyBytes = pubKeyRing.getEncryptionPubKeyBytes();
        returnedSignaturePubKeyBytes[0] ^= 1;
        returnedEncryptionPubKeyBytes[0] ^= 1;

        assertArrayEquals(expectedSignaturePubKeyBytes, pubKeyRing.getSignaturePubKeyBytes());
        assertArrayEquals(expectedEncryptionPubKeyBytes, pubKeyRing.getEncryptionPubKeyBytes());
    }
}
