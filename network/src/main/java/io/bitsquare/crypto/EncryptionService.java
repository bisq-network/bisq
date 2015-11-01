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

import io.bitsquare.common.crypto.*;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.messaging.DecryptedMessageWithPubKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.security.KeyPair;

public class EncryptionService {
    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private final KeyRing keyRing;

    @Inject
    public EncryptionService(KeyRing keyRing) {
        this.keyRing = keyRing;
    }

    public SealedAndSigned encryptAndSignMessage(PubKeyRing pubKeyRing, Message message) throws CryptoException {
        log.trace("encryptAndSignMessage message = " + message);
        KeyPair signatureKeyPair = keyRing.getSignatureKeyPair();
        return Encryption.encryptHybridWithSignature(message,
                signatureKeyPair.getPrivate(),
                signatureKeyPair.getPublic(),
                pubKeyRing.getEncryptionPubKey());
    }

    public DecryptedMessageWithPubKey decryptAndVerifyMessage(SealedAndSigned sealedAndSigned) throws CryptoException {
        DecryptedPayloadWithPubKey decryptedPayloadWithPubKey = Encryption.decryptHybridWithSignature(sealedAndSigned, keyRing.getEncryptionKeyPair().getPrivate());
        if (decryptedPayloadWithPubKey.payload instanceof Message) {
            //log.trace("Decryption needed {} ms", System.currentTimeMillis() - ts);
            return new DecryptedMessageWithPubKey((Message) decryptedPayloadWithPubKey.payload, decryptedPayloadWithPubKey.sigPublicKey);
        } else {
            throw new CryptoException("messageObject is not instance of Message");
        }
    }
}

