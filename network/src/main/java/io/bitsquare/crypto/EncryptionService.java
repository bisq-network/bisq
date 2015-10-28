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

import io.bitsquare.common.crypto.CryptoException;
import io.bitsquare.common.crypto.CryptoUtil;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.messaging.DecryptedMessageWithPubKey;
import io.bitsquare.p2p.messaging.SealedAndSignedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.crypto.*;
import javax.inject.Inject;
import java.io.IOException;
import java.security.*;

public class EncryptionService {
    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    @Nullable
    private final KeyRing keyRing;

    @Inject
    public EncryptionService(KeyRing keyRing) {
        this.keyRing = keyRing;
    }


    public SealedAndSignedMessage encryptAndSignMessage(PubKeyRing pubKeyRing, Message message) throws CryptoException {
        log.trace("encryptAndSignMessage message = " + message);
        //long ts = System.currentTimeMillis();

        try {
            // Create symmetric key 
            KeyGenerator keyGenerator = KeyGenerator.getInstance(CryptoUtil.SYM_ENCR_KEY_ALGO);
            // TODO consider 256 bit as key length
            keyGenerator.init(128);
            SecretKey secretKey = keyGenerator.generateKey();

            // Encrypt secretKey with peers pubKey using SealedObject
            Cipher cipherAsym = Cipher.getInstance(CryptoUtil.ASYM_CIPHER);
            cipherAsym.init(Cipher.ENCRYPT_MODE, pubKeyRing.getMsgEncryptionPubKey());
            SealedObject sealedSecretKey = new SealedObject(secretKey, cipherAsym);

            // Sign (hash of) message and pack it into SignedObject
            SignedObject signedMessage = new SignedObject(message, keyRing.getMsgSignatureKeyPair().getPrivate(), Signature.getInstance(CryptoUtil.MSG_SIGN_ALGO));

            // // Encrypt signedMessage with secretKey using SealedObject
            Cipher cipherSym = Cipher.getInstance(CryptoUtil.SYM_CIPHER);
            cipherSym.init(Cipher.ENCRYPT_MODE, secretKey);
            SealedObject sealedMessage = new SealedObject(signedMessage, cipherSym);

            SealedAndSignedMessage sealedAndSignedMessage = new SealedAndSignedMessage(sealedSecretKey,
                    sealedMessage,
                    keyRing.getMsgSignatureKeyPair().getPublic()
            );
            //log.trace("Encryption needed {} ms", System.currentTimeMillis() - ts);
            log.trace("sealedAndSignedMessage size " + Utilities.objectToByteArray(sealedAndSignedMessage).length);
            return sealedAndSignedMessage;
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException
                | IllegalBlockSizeException | IOException | SignatureException e) {
            throw new CryptoException(e);
        }
    }

    public DecryptedMessageWithPubKey decryptAndVerifyMessage(SealedAndSignedMessage sealedAndSignedMessage) throws CryptoException {
        // long ts = System.currentTimeMillis();
        try {
            if (keyRing == null)
                throw new CryptoException("keyRing is null");

            SealedObject sealedSecretKey = sealedAndSignedMessage.sealedSecretKey;
            SealedObject sealedMessage = sealedAndSignedMessage.sealedMessage;
            PublicKey signaturePubKey = sealedAndSignedMessage.signaturePubKey;

            // Decrypt secretKey with my privKey
            Cipher cipherAsym = Cipher.getInstance(CryptoUtil.ASYM_CIPHER);
            cipherAsym.init(Cipher.DECRYPT_MODE, keyRing.getMsgEncryptionKeyPair().getPrivate());
            Object secretKeyObject = sealedSecretKey.getObject(cipherAsym);
            if (secretKeyObject instanceof SecretKey) {
                SecretKey secretKey = (SecretKey) secretKeyObject;

                // Decrypt signedMessage with secretKey
                Cipher cipherSym = Cipher.getInstance(CryptoUtil.SYM_CIPHER);
                cipherSym.init(Cipher.DECRYPT_MODE, secretKey);
                Object signedMessageObject = sealedMessage.getObject(cipherSym);
                if (signedMessageObject instanceof SignedObject) {
                    SignedObject signedMessage = (SignedObject) signedMessageObject;

                    // Verify message with peers pubKey
                    if (signedMessage.verify(signaturePubKey, Signature.getInstance(CryptoUtil.MSG_SIGN_ALGO))) {
                        // Get message
                        Object messageObject = signedMessage.getObject();
                        if (messageObject instanceof Message) {
                            //log.trace("Decryption needed {} ms", System.currentTimeMillis() - ts);
                            return new DecryptedMessageWithPubKey((Message) messageObject, signaturePubKey);
                        } else {
                            throw new CryptoException("messageObject is not instance of Message");
                        }
                    } else {
                        throw new CryptoException("Signature is not valid");
                    }
                } else {
                    throw new CryptoException("signedMessageObject is not instance of SignedObject");
                }
            } else {
                throw new CryptoException("secretKeyObject is not instance of SecretKey");
            }
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | BadPaddingException |
                ClassNotFoundException | IllegalBlockSizeException | IOException | SignatureException e) {
            throw new CryptoException(e);
        }
    }
}

