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

import io.bitsquare.p2p.Message;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;

import com.google.common.base.Charsets;

import java.io.IOException;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.SignedObject;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.spongycastle.util.encoders.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class CryptoService<T> {
    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);
    public static final String DHT_SIGN_KEY_ALGO = "DSA";
    public static final String MSG_SIGN_KEY_ALGO = "DSA";
    public static final String MSG_ENCR_KEY_ALGO = "RSA";

    private static final String SYM_ENCR_KEY_ALGO = "AES";
    private static final String SYM_CIPHER = "AES";
    private static final String ASYM_CIPHER = "RSA"; //RSA/ECB/PKCS1Padding
    private static final String MSG_SIGN_ALGO = "SHA1withDSA";

    public static KeyPair generateDhtSignatureKeyPair() throws NoSuchAlgorithmException {
        long ts = System.currentTimeMillis();
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(DHT_SIGN_KEY_ALGO);
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.genKeyPair();
        log.debug("Generate dhtSignatureKeyPair needed {} ms", System.currentTimeMillis() - ts);
        return keyPair;
    }

    public static KeyPair generateMsgSignatureKeyPair() throws NoSuchAlgorithmException {
        long ts = System.currentTimeMillis();
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(MSG_SIGN_KEY_ALGO);
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.genKeyPair();
        log.debug("Generate dhtSignatureKeyPair needed {} ms", System.currentTimeMillis() - ts);
        return keyPair;
    }

    public static KeyPair generateMsgEncryptionKeyPair() throws NoSuchAlgorithmException {
        long ts = System.currentTimeMillis();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(MSG_ENCR_KEY_ALGO);
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.genKeyPair();
        log.debug("Generate msgEncryptionKeyPair needed {} ms", System.currentTimeMillis() - ts);
        return keyPair;
    }

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private KeyRing keyRing;

    @Inject
    public CryptoService(KeyRing keyRing) {
        this.keyRing = keyRing;
    }

    public SealedAndSignedMessage encryptAndSignMessage(PubKeyRing pubKeyRing, Message message) throws CryptoException {
        long ts = System.currentTimeMillis();

        try {
            // Create symmetric key 
            KeyGenerator keyGenerator = KeyGenerator.getInstance(SYM_ENCR_KEY_ALGO);
            keyGenerator.init(128);
            SecretKey secretKey = keyGenerator.generateKey();

            // Encrypt secretKey with peers pubKey using SealedObject
            Cipher cipherAsym = Cipher.getInstance(ASYM_CIPHER);
            cipherAsym.init(Cipher.ENCRYPT_MODE, pubKeyRing.getMsgEncryptionPubKey());
            SealedObject sealedSecretKey = new SealedObject(secretKey, cipherAsym);

            // Sign (hash of) message and pack it into SignedObject
            SignedObject signedMessage = new SignedObject(message, keyRing.getMsgSignatureKeyPair().getPrivate(), Signature.getInstance(MSG_SIGN_ALGO));

            // // Encrypt signedMessage with secretKey using SealedObject
            Cipher cipherSym = Cipher.getInstance(SYM_CIPHER);
            cipherSym.init(Cipher.ENCRYPT_MODE, secretKey);
            SealedObject sealedMessage = new SealedObject(signedMessage, cipherSym);

            SealedAndSignedMessage sealedAndSignedMessage = new SealedAndSignedMessage(sealedSecretKey,
                    sealedMessage,
                    keyRing.getMsgSignatureKeyPair().getPublic()
            );
            log.debug("Encryption needed {} ms", System.currentTimeMillis() - ts);
            return sealedAndSignedMessage;
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException
                | IllegalBlockSizeException | IOException | SignatureException e) {
            throw new CryptoException(e);

        }
    }

    public MessageWithPubKey decryptAndVerifyMessage(SealedAndSignedMessage sealedAndSignedMessage) throws CryptoException {
        long ts = System.currentTimeMillis();
        try {
            SealedObject sealedSecretKey = sealedAndSignedMessage.getSealedSecretKey();
            SealedObject sealedMessage = sealedAndSignedMessage.getSealedMessage();
            PublicKey signaturePubKey = sealedAndSignedMessage.getSignaturePubKey();

            // Decrypt secretKey with my privKey
            Cipher cipherAsym = Cipher.getInstance(ASYM_CIPHER);
            cipherAsym.init(Cipher.DECRYPT_MODE, keyRing.getMsgEncryptionKeyPair().getPrivate());
            Object secretKeyObject = sealedSecretKey.getObject(cipherAsym);
            assert secretKeyObject instanceof SecretKey;
            SecretKey secretKey = (SecretKey) secretKeyObject;

            // Decrypt signedMessage with secretKey
            Cipher cipherSym = Cipher.getInstance(SYM_CIPHER);
            cipherSym.init(Cipher.DECRYPT_MODE, secretKey);
            Object signedMessageObject = sealedMessage.getObject(cipherSym);
            assert signedMessageObject instanceof SignedObject;
            SignedObject signedMessage = (SignedObject) signedMessageObject;

            // Verify message with peers pubKey
            if (signedMessage.verify(signaturePubKey, Signature.getInstance(MSG_SIGN_ALGO))) {
                // Get message
                Object messageObject = signedMessage.getObject();
                assert messageObject instanceof Message;
                log.debug("Decryption needed {} ms", System.currentTimeMillis() - ts);
                return new MessageWithPubKey((Message) messageObject, signaturePubKey);
            }
            else {
                throw new CryptoException("Signature is not valid");
            }
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | BadPaddingException |
                ClassNotFoundException | IllegalBlockSizeException | IOException | SignatureException e) {
            throw new CryptoException(e);
        }
    }


    public String signMessage(ECKey key, Sha256Hash hash) {
        ECKey.ECDSASignature sig = key.sign(hash, null);
        // Now we have to work backwards to figure out the recId needed to recover the signature.
        int recId = -1;
        for (int i = 0; i < 4; i++) {
            ECKey k = ECKey.recoverFromSignature(i, sig, hash, key.isCompressed());
            if (k != null && k.getPubKeyPoint().equals(key.getPubKeyPoint())) {
                recId = i;
                break;
            }
        }
        if (recId == -1)
            throw new RuntimeException("Could not construct a recoverable key. This should never happen.");
        int headerByte = recId + 27 + (key.isCompressed() ? 4 : 0);
        byte[] sigData = new byte[65];  // 1 header + 32 bytes for R + 32 bytes for S
        sigData[0] = (byte) headerByte;
        System.arraycopy(Utils.bigIntegerToBytes(sig.r, 32), 0, sigData, 1, 32);
        System.arraycopy(Utils.bigIntegerToBytes(sig.s, 32), 0, sigData, 33, 32);
        return new String(Base64.encode(sigData), Charsets.UTF_8);
    }


    public byte[] digestMessageWithSignature(ECKey key, String message) {
        String signedMessage = signMessage(key, message);
        return Utils.sha256hash160(message.concat(signedMessage).getBytes(Charsets.UTF_8));
    }

    public String signMessage(ECKey key, String message) {
        byte[] data = Utils.formatMessageForSigning(message);
        Sha256Hash hash = Sha256Hash.hashTwice(data);
        return signMessage(key, hash);
    }

    public Sha256Hash hash(String message) {
        byte[] data = Utils.formatMessageForSigning(message);
        return Sha256Hash.hashTwice(data);
    }
}

