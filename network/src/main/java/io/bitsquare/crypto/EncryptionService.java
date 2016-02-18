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
import io.bitsquare.p2p.messaging.DecryptedMsgWithPubKey;

import javax.inject.Inject;
import java.security.KeyPair;

public class EncryptionService {
    private final KeyRing keyRing;

    @Inject
    public EncryptionService(KeyRing keyRing) {
        this.keyRing = keyRing;
    }

    public SealedAndSigned encryptAndSign(PubKeyRing pubKeyRing, Message message) throws CryptoException {
        KeyPair signatureKeyPair = keyRing.getSignatureKeyPair();
        return Encryption.encryptHybridWithSignature(message, signatureKeyPair, pubKeyRing.getEncryptionPubKey());
    }

    public DecryptedMsgWithPubKey decryptAndVerify(SealedAndSigned sealedAndSigned) throws CryptoException {
        DecryptedDataTuple decryptedDataTuple = Encryption.decryptHybridWithSignature(sealedAndSigned,
                keyRing.getEncryptionKeyPair().getPrivate());
        if (decryptedDataTuple.payload instanceof Message) {
            return new DecryptedMsgWithPubKey((Message) decryptedDataTuple.payload,
                    decryptedDataTuple.sigPublicKey);
        } else {
            throw new CryptoException("decryptedPayloadWithPubKey.payload is not instance of Message");
        }
    }
}

