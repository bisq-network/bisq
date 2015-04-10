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

import java.io.Serializable;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Same as KeyRing but with public keys only.
 * Used to sent over the wire to other peer.
 */
public class PubKeyRing implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(PubKeyRing.class);

    private final byte[] dhtSignaturePubKeyBytes;
    private final byte[] msgSignaturePubKeyBytes;
    private final byte[] msgEncryptionPubKeyBytes;

    transient private PublicKey dhtSignaturePubKey;
    transient private PublicKey msgSignaturePubKey;
    transient private PublicKey msgEncryptionPubKey;

    public PubKeyRing(PublicKey dhtSignaturePubKey, PublicKey msgSignaturePubKey, PublicKey msgEncryptionPubKey) {
        this.dhtSignaturePubKey = dhtSignaturePubKey;
        this.msgSignaturePubKey = msgSignaturePubKey;
        this.msgEncryptionPubKey = msgEncryptionPubKey;

        this.dhtSignaturePubKeyBytes = new X509EncodedKeySpec(dhtSignaturePubKey.getEncoded()).getEncoded();
        this.msgSignaturePubKeyBytes = new X509EncodedKeySpec(msgSignaturePubKey.getEncoded()).getEncoded();
        this.msgEncryptionPubKeyBytes = new X509EncodedKeySpec(msgEncryptionPubKey.getEncoded()).getEncoded();
    }

    public PublicKey getDhtSignaturePubKey() {
        if (dhtSignaturePubKey == null) {
            try {
                dhtSignaturePubKey = KeyFactory.getInstance(CryptoService.DHT_SIGN_KEY_ALGO).generatePublic(new X509EncodedKeySpec(dhtSignaturePubKeyBytes));
            } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                e.printStackTrace();
                log.error(e.getMessage());
            }
        }
        return dhtSignaturePubKey;
    }


    public PublicKey getMsgSignaturePubKey() {
        if (msgSignaturePubKey == null) {
            try {
                msgSignaturePubKey = KeyFactory.getInstance(CryptoService.MSG_SIGN_KEY_ALGO).generatePublic(new X509EncodedKeySpec(msgSignaturePubKeyBytes));
            } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                e.printStackTrace();
                log.error(e.getMessage());
            }
        }
        return msgSignaturePubKey;
    }

    public PublicKey getMsgEncryptionPubKey() {
        if (msgEncryptionPubKey == null) {
            try {
                msgEncryptionPubKey = KeyFactory.getInstance(CryptoService.MSG_ENCR_KEY_ALGO).generatePublic(new X509EncodedKeySpec(msgEncryptionPubKeyBytes));
            } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                e.printStackTrace();
                log.error(e.getMessage());
            }
        }
        return msgEncryptionPubKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PubKeyRing that = (PubKeyRing) o;

        if (!Arrays.equals(dhtSignaturePubKeyBytes, that.dhtSignaturePubKeyBytes)) return false;
        if (!Arrays.equals(msgSignaturePubKeyBytes, that.msgSignaturePubKeyBytes)) return false;
        return Arrays.equals(msgEncryptionPubKeyBytes, that.msgEncryptionPubKeyBytes);

    }

    @Override
    public int hashCode() {
        int result = dhtSignaturePubKeyBytes != null ? Arrays.hashCode(dhtSignaturePubKeyBytes) : 0;
        result = 31 * result + (msgSignaturePubKeyBytes != null ? Arrays.hashCode(msgSignaturePubKeyBytes) : 0);
        result = 31 * result + (msgEncryptionPubKeyBytes != null ? Arrays.hashCode(msgEncryptionPubKeyBytes) : 0);
        return result;
    }


    public String getHashString() {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(dhtSignaturePubKeyBytes);
            messageDigest.update(msgSignaturePubKeyBytes);
            messageDigest.update(msgEncryptionPubKeyBytes);
            return new String(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash Algorithm not found.", e);
        }
    }

    @Override
    public String toString() {
        return "PubKeyRing{" +
                "\ndhtSignaturePubKey=\n" + Util.pubKeyToString(getDhtSignaturePubKey()) +
                "\n\nmsgSignaturePubKey=\n" + Util.pubKeyToString(getMsgSignaturePubKey()) +
                "\n\nmsgEncryptionPubKey=\n" + Util.pubKeyToString(getMsgEncryptionPubKey()) +
                '}';
    }

}
