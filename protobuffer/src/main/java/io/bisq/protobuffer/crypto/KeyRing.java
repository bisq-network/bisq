/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.protobuffer.crypto;

import io.bisq.common.crypto.Sig;
import io.bisq.protobuffer.payload.crypto.PubKeyRing;

import javax.inject.Inject;
import java.security.KeyPair;

public class KeyRing {
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

    public KeyPair getSignatureKeyPair() {
        return signatureKeyPair;
    }

    public KeyPair getEncryptionKeyPair() {
        return encryptionKeyPair;
    }

    public PubKeyRing getPubKeyRing() {
        return pubKeyRing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KeyRing)) return false;

        KeyRing keyRing = (KeyRing) o;

        if (signatureKeyPair != null ? !signatureKeyPair.equals(keyRing.signatureKeyPair) : keyRing.signatureKeyPair != null)
            return false;
        if (encryptionKeyPair != null ? !encryptionKeyPair.equals(keyRing.encryptionKeyPair) : keyRing.encryptionKeyPair != null)
            return false;
        return !(pubKeyRing != null ? !pubKeyRing.equals(keyRing.pubKeyRing) : keyRing.pubKeyRing != null);

    }

    @Override
    public int hashCode() {
        int result = signatureKeyPair != null ? signatureKeyPair.hashCode() : 0;
        result = 31 * result + (encryptionKeyPair != null ? encryptionKeyPair.hashCode() : 0);
        result = 31 * result + (pubKeyRing != null ? pubKeyRing.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "KeyRing{" +
                "signatureKeyPair.hashCode()=" + signatureKeyPair.hashCode() +
                ", encryptionKeyPair.hashCode()=" + encryptionKeyPair.hashCode() +
                ", pubKeyRing.hashCode()=" + pubKeyRing.hashCode() +
                '}';
    }
}
