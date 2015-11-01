/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.security.KeyPair;

public class KeyRing {
    private static final Logger log = LoggerFactory.getLogger(KeyRing.class);

    // Used for signing messages sent over the wire
    private final KeyPair signatureKeyPair;
    // Used for encrypting messages sent over the wire (hybrid encryption scheme is used, so it is used only to encrypt a symmetric session key) 
    private final KeyPair encryptionKeyPair;

    private final PubKeyRing pubKeyRing;

    @Inject
    public KeyRing(KeyStorage keyStorage) {
        if (keyStorage.allKeyFilesExist()) {
            signatureKeyPair = keyStorage.loadKeyPair(KeyStorage.Key.MSG_SIGNATURE);
            encryptionKeyPair = keyStorage.loadKeyPair(KeyStorage.Key.MSG_ENCRYPTION);
        } else {
            // First time we create key pairs
            signatureKeyPair = Sig.generateKeyPair();
            encryptionKeyPair = Encryption.generateEncryptionKeyPair();
            keyStorage.saveKeyRing(this);
        }
        pubKeyRing = new PubKeyRing(signatureKeyPair.getPublic(), signatureKeyPair.getPublic(), encryptionKeyPair.getPublic());
    }

    //TODO
    public KeyPair getStorageSignatureKeyPair() {
        return signatureKeyPair;
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
}
