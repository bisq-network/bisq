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

package io.bitsquare.crypto;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyRing {
    private static final Logger log = LoggerFactory.getLogger(KeyRing.class);

    // Public key is used as ID in DHT network. Used for data protection mechanism in TomP2P DHT
    private KeyPair dhtSignatureKeyPair;
    // Used for signing messages sent over the wire
    private KeyPair msgSignatureKeyPair;
    // Used for encrypting messages sent over the wire (hybrid encryption scheme is used, so it is used only to encrypt a symmetric session key) 
    private KeyPair msgEncryptionKeyPair;

    private PubKeyRing pubKeyRing;
    private final KeyStorage keyStorage;

    @Inject
    public KeyRing(KeyStorage keyStorage) throws CryptoException {
        this.keyStorage = keyStorage;

        init(keyStorage);
    }

    // consider extra thread for loading (takes about 264 ms at first startup, then load only takes nearly no time)
    public void init(KeyStorage keyStorage) throws CryptoException {
        if (keyStorage.allKeyFilesExist()) {
            dhtSignatureKeyPair = keyStorage.loadKeyPair(KeyStorage.Key.DHT_SIGNATURE);
            msgSignatureKeyPair = keyStorage.loadKeyPair(KeyStorage.Key.MSG_SIGNATURE);
            msgEncryptionKeyPair = keyStorage.loadKeyPair(KeyStorage.Key.MSG_ENCRYPTION);
        }
        else {
            // First time we create key pairs
            try {
                this.dhtSignatureKeyPair = CryptoService.generateDhtSignatureKeyPair();
                this.msgSignatureKeyPair = CryptoService.generateMsgSignatureKeyPair();
                this.msgEncryptionKeyPair = CryptoService.generateMsgEncryptionKeyPair();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                throw new CryptoException("Error at KeyRing constructor ", e);
            }
            keyStorage.saveKeyRing(this);
        }

        pubKeyRing = new PubKeyRing(dhtSignatureKeyPair.getPublic(), msgSignatureKeyPair.getPublic(), msgEncryptionKeyPair.getPublic());
    }

    // For unit testing
    KeyRing() throws NoSuchAlgorithmException {
        keyStorage = null;
        this.dhtSignatureKeyPair = CryptoService.generateDhtSignatureKeyPair();
        this.msgSignatureKeyPair = CryptoService.generateMsgSignatureKeyPair();
        this.msgEncryptionKeyPair = CryptoService.generateMsgEncryptionKeyPair();
        pubKeyRing = new PubKeyRing(dhtSignatureKeyPair.getPublic(), msgSignatureKeyPair.getPublic(), msgEncryptionKeyPair.getPublic());
    }

    KeyRing(KeyPair dhtSignatureKeyPair, KeyPair msgSignatureKeyPair, KeyPair msgEncryptionKeyPair) {
        this.keyStorage = null;
        this.dhtSignatureKeyPair = dhtSignatureKeyPair;
        this.msgSignatureKeyPair = msgSignatureKeyPair;
        this.msgEncryptionKeyPair = msgEncryptionKeyPair;
        pubKeyRing = new PubKeyRing(dhtSignatureKeyPair.getPublic(), msgSignatureKeyPair.getPublic(), msgEncryptionKeyPair.getPublic());
    }

    public KeyPair getDhtSignatureKeyPair() {
        return dhtSignatureKeyPair;
    }

    public KeyPair getMsgSignatureKeyPair() {
        return msgSignatureKeyPair;
    }

    public KeyPair getMsgEncryptionKeyPair() {
        return msgEncryptionKeyPair;
    }

    public PubKeyRing getPubKeyRing() {
        return pubKeyRing;
    }
}
