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

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;
import io.bitsquare.util.Utilities;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;

import java.util.Arrays;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class EncryptionService<T> {
    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);
    private static final String ALGO_SYM = "AES";
    private static final String CIPHER_SYM = "AES";// AES/CBC/PKCS5Padding
    private static final String ALGO_ASYM = "RSA";
    private static final String CIPHER_ASYM = "RSA/ECB/PKCS1Padding";

    private static final int MAX_SIZE = 10000; // in bytes

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private SignatureService signatureService;

    @Inject
    public EncryptionService(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    public KeyPair getGeneratedDSAKeyPair() throws NoSuchAlgorithmException {
        long ts = System.currentTimeMillis();
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DSA");
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.genKeyPair();
        log.debug("getGeneratedDSAKeyPair needed {} ms", System.currentTimeMillis() - ts);
        return keyPair;
    }

    public KeyPair getGeneratedRSAKeyPair() throws NoSuchAlgorithmException {
        long ts = System.currentTimeMillis();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGO_ASYM);
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.genKeyPair();
        log.debug("getGeneratedRSAKeyPair needed {} ms", System.currentTimeMillis() - ts);
        return keyPair;
    }

    public byte[] encryptObject(PublicKey publicKey, ECKey signatureKeyPair, Object object) throws CryptoException {
        return encryptBytes(publicKey, signatureKeyPair, Utilities.objectToBytArray(object));
    }

    public byte[] encryptMessage(PublicKey publicKey, ECKey signatureKeyPair, Message object) throws CryptoException {
        return encryptBytes(publicKey, signatureKeyPair, Utilities.objectToBytArray(object));
    }

    public byte[] encryptBytes(PublicKey publicKey, ECKey signatureKeyPair, byte[] plainText) throws CryptoException {
        long ts = System.currentTimeMillis();

        if (plainText.length == 0)
            throw new CryptoException("Input data is null.");

        try {
            // Create symmetric key 
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGO_SYM);
            keyGenerator.init(128);
            SecretKey oneTimeKey = keyGenerator.generateKey();

            // Encrypt secretKey with asymmetric key (16 bytes)
            Cipher cipherAsym = Cipher.getInstance(CIPHER_ASYM);
            cipherAsym.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] encryptedOneTimeKey = cipherAsym.doFinal(oneTimeKey.getEncoded());

            // Create signature of plainText (65 bytes)
            ECKey.ECDSASignature signature = signatureService.signBytes(signatureKeyPair, plainText).toCanonicalised();
            byte[] sig = signature.encodeToDER(); // has 70-72 bytes ;-(
            byte[] sigLength = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sig.length).array();

            // Encrypt plainText with symmetric key
            Cipher cipherSym = Cipher.getInstance(CIPHER_SYM);
            cipherSym.init(Cipher.ENCRYPT_MODE, oneTimeKey);
            byte[] cipherText = cipherSym.doFinal(plainText);

            // payload
            byte[] payload = Utilities.concatByteArrays(sigLength, sig, signatureKeyPair.getPubKey(), encryptedOneTimeKey, cipherText);
            byte[] payloadLength = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.length).array();

            // Checksum
            byte[] checksum = Utils.sha256hash160(Utilities.concatByteArrays(payloadLength, payload));

            // 1 byte version | 20 byte checksum | 4 byte payload length | n bytes payload 
            // payload consist of:  4 byte sigLength | sigLength(70-72) bytes for sig | 33 bytes for signaturePubKey | 128 bytes for encryptedOneTimeKey | 
            // remaining 
            // bytes for cipherText
            byte[] result = Utilities.concatByteArrays(Version.NETWORK_PROTOCOL_VERSION, checksum, payloadLength, payload);
            log.debug("result.length " + result.length);
            log.debug("Encryption needed {} ms", System.currentTimeMillis() - ts);
            return result;
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            throw new CryptoException(e);
        }
    }

    public T decryptToMessage(PrivateKey privateKey, byte[] data) throws IllegalBlockSizeException, InvalidKeyException,
            BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException, CryptoException {
        return Utilities.<T>byteArrayToObject(decryptBytes(privateKey, data));
    }

    public byte[] decryptBytes(PrivateKey privateKey, byte[] data) throws CryptoException {
        long ts = System.currentTimeMillis();

        if (data.length < 25)
            throw new CryptoException("The data is shorter as the min. overhead length.");
        else if (data.length > MAX_SIZE)
            throw new CryptoException("The data exceeds the max. size.");

        // 1 byte version | 20 byte checksum | 4 byte payload length | n bytes payload consisting of sig, encryptedOneTimeKey, cipherText
        byte[] version = new byte[1];
        int cursor = 0;
        System.arraycopy(data, cursor, version, 0, version.length);

        if (!Arrays.equals(version, Version.NETWORK_PROTOCOL_VERSION))
            throw new CryptoException("Incorrect version.");

        byte[] checksum = new byte[20];
        cursor += version.length;
        System.arraycopy(data, cursor, checksum, 0, checksum.length);

        byte[] payloadLength = new byte[4];
        cursor += checksum.length;
        System.arraycopy(data, cursor, payloadLength, 0, payloadLength.length);
        int payloadLengthInt = ByteBuffer.wrap(payloadLength).order(ByteOrder.LITTLE_ENDIAN).getInt();
        log.debug("encode payloadLengthInt " + payloadLengthInt);
        if (payloadLengthInt < 0)
            throw new CryptoException("Payload length cannot be negative.");
        else if (payloadLengthInt > data.length - 25)
            throw new CryptoException("Payload length cannot be larger then data excluding overhead.");

        byte[] payload = new byte[payloadLengthInt];
        cursor += payloadLength.length;
        System.arraycopy(data, cursor, payload, 0, payload.length);


        byte[] sigLength = new byte[4];
        // cursor stays the same
        System.arraycopy(data, cursor, sigLength, 0, sigLength.length);
        int sigLengthInt = ByteBuffer.wrap(sigLength).order(ByteOrder.LITTLE_ENDIAN).getInt();

        byte[] sig = new byte[sigLengthInt];
        cursor += sigLength.length;
        System.arraycopy(data, cursor, sig, 0, sig.length);

        byte[] signaturePubKey = new byte[33];
        cursor += sig.length;
        System.arraycopy(data, cursor, signaturePubKey, 0, signaturePubKey.length);

        byte[] encryptedOneTimeKey = new byte[128];
        cursor += signaturePubKey.length;
        System.arraycopy(data, cursor, encryptedOneTimeKey, 0, encryptedOneTimeKey.length);

        byte[] cipherText = new byte[payloadLengthInt - (sigLength.length + sig.length + signaturePubKey.length + encryptedOneTimeKey.length)];
        cursor += encryptedOneTimeKey.length;
        System.arraycopy(data, cursor, cipherText, 0, cipherText.length);

        // Checksum
        byte[] controlChecksum = Utils.sha256hash160(Utilities.concatByteArrays(payloadLength, payload));
        if (!Arrays.equals(checksum, controlChecksum))
            throw new CryptoException("The checksum is invalid.");

        try {
            // Decrypt oneTimeKey key with asymmetric key
            Cipher cipherAsym = Cipher.getInstance(CIPHER_ASYM);
            cipherAsym.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] oneTimeKey = cipherAsym.doFinal(encryptedOneTimeKey);

            // Decrypt payload with symmetric key
            Key key = new SecretKeySpec(oneTimeKey, ALGO_SYM);
            Cipher cipherSym = Cipher.getInstance(CIPHER_SYM);
            cipherSym.init(Cipher.DECRYPT_MODE, key);
            byte[] plainText = cipherSym.doFinal(cipherText);

            // Verify signature
            boolean verified = signatureService.verify(ECKey.fromPublicOnly(signaturePubKey).getPubKey(), plainText, ECKey.ECDSASignature.decodeFromDER(sig));
            if (!verified)
                throw new CryptoException("Signature is not valid");

            log.debug("Encryption needed {} ms", System.currentTimeMillis() - ts);
            return plainText;
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            throw new CryptoException(e);
        }
    }

}

