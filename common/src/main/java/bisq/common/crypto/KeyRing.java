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

import javax.inject.Inject;

import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPublicKey;

import java.security.KeyPair;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Getter
@EqualsAndHashCode
@Slf4j
public class KeyRing {
    private final KeyPair signatureKeyPair;
    private final KeyPair encryptionKeyPair;
    private final PubKeyRing pubKeyRing;

    // We generate by default a PGP keypair but the user can set his own if he prefers.
    // Not impl. yet but prepared in data structure
    @Nullable
    @Setter
    // TODO  remove Nullable once impl.
    private PGPKeyPair pgpKeyPair;

    @Inject
    public KeyRing(KeyStorage keyStorage) {
        if (keyStorage.allKeyFilesExist()) {
            signatureKeyPair = keyStorage.loadKeyPair(KeyStorage.KeyEntry.MSG_SIGNATURE);
            encryptionKeyPair = keyStorage.loadKeyPair(KeyStorage.KeyEntry.MSG_ENCRYPTION);

            // TODO not impl
            pgpKeyPair = keyStorage.loadPgpKeyPair(KeyStorage.KeyEntry.PGP);
        } else {
            // First time we create key pairs
            signatureKeyPair = Sig.generateKeyPair();
            encryptionKeyPair = Encryption.generateKeyPair();

            // TODO not impl
            pgpKeyPair = PGP.generateKeyPair();
            keyStorage.saveKeyRing(this);
        }
        // TODO  remove Nullable once impl.
        final PGPPublicKey pgpPublicKey = pgpKeyPair != null ? pgpKeyPair.getPublicKey() : null;
        pubKeyRing = new PubKeyRing(signatureKeyPair.getPublic(), encryptionKeyPair.getPublic(), pgpPublicKey);
    }

    // Don't print keys for security reasons
    @Override
    public String toString() {
        return "KeyRing{" +
                "signatureKeyPair.hashCode()=" + signatureKeyPair.hashCode() +
                ", encryptionKeyPair.hashCode()=" + encryptionKeyPair.hashCode() +
                ", pubKeyRing.hashCode()=" + pubKeyRing.hashCode() +
                '}';
    }
}
