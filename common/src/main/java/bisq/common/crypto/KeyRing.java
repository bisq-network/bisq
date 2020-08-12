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
import javax.inject.Singleton;

import java.security.KeyPair;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@EqualsAndHashCode
@Slf4j
@Singleton
public final class KeyRing {
    private final KeyPair signatureKeyPair;
    private final KeyPair encryptionKeyPair;
    private final PubKeyRing pubKeyRing;

    @Inject
    public KeyRing(KeyStorage keyStorage) {
        if (keyStorage.allKeyFilesExist()) {
            signatureKeyPair = keyStorage.loadKeyPair(KeyStorage.KeyEntry.MSG_SIGNATURE);
            encryptionKeyPair = keyStorage.loadKeyPair(KeyStorage.KeyEntry.MSG_ENCRYPTION);
        } else {
            // First time we create key pairs
            signatureKeyPair = Sig.generateKeyPair();
            encryptionKeyPair = Encryption.generateKeyPair();
            keyStorage.saveKeyRing(this);
        }
        pubKeyRing = new PubKeyRing(signatureKeyPair.getPublic(), encryptionKeyPair.getPublic());
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
