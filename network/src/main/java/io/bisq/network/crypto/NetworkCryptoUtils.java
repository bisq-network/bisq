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

package io.bisq.network.crypto;

import com.google.protobuf.InvalidProtocolBufferException;
import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.Encryption;
import io.bisq.common.crypto.Hash;
import io.bisq.common.crypto.Sig;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.Marshaller;
import io.bisq.protobuffer.ProtoBufferUtilities;
import io.bisq.protobuffer.crypto.DecryptedDataTuple;
import io.bisq.protobuffer.message.Message;
import io.bisq.vo.crypto.SealedAndSignedVO;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import static io.bisq.common.crypto.Encryption.decryptSecretKey;

public class NetworkCryptoUtils {

    private static byte[] encryptPayloadWithHmac(Message object, SecretKey secretKey) throws CryptoException {
        return Encryption.encryptPayloadWithHmac(object.toProto().toByteArray(), secretKey);
    }

    /**
     * @param payload             The data to encrypt.
     * @param signatureKeyPair    The key pair for signing.
     * @param encryptionPublicKey The public key used for encryption.
     * @return A SealedAndSigned object.
     * @throws CryptoException
     */
    public static SealedAndSignedVO encryptHybridWithSignature(Message payload, KeyPair signatureKeyPair,
                                                               PublicKey encryptionPublicKey)
            throws CryptoException {
        // Create a symmetric key
        SecretKey secretKey = Encryption.generateSecretKey();

        // Encrypt secretKey with receiver's publicKey 
        byte[] encryptedSecretKey = Encryption.encryptSecretKey(secretKey, encryptionPublicKey);

        // Encrypt with sym key payload with appended hmac
        byte[] encryptedPayloadWithHmac = encryptPayloadWithHmac(payload, secretKey);

        // sign hash of encryptedPayloadWithHmac
        byte[] hash = Hash.getHash(encryptedPayloadWithHmac);
        byte[] signature = Sig.sign(signatureKeyPair.getPrivate(), hash);

        // Pack all together
        return new SealedAndSignedVO(encryptedSecretKey, encryptedPayloadWithHmac, signature, signatureKeyPair.getPublic());
    }

    /**
     * @param data Any serializable object. Will be converted into a byte array using Java serialisation.
     * @return Hash of data
     */
    public static byte[] getHash(Marshaller data) {
        return Hash.getHash((data.toProto()).toByteArray());
    }

    /**
     * @param sealedAndSignedVO The sealedAndSigned object.
     * @param privateKey        The private key for decryption
     * @return A DecryptedPayloadWithPubKey object.
     * @throws CryptoException
     */
    public static DecryptedDataTuple decryptHybridWithSignature(SealedAndSignedVO sealedAndSignedVO, PrivateKey privateKey) throws CryptoException {
        SecretKey secretKey = decryptSecretKey(sealedAndSignedVO.getEncryptedSecretKey(), privateKey);
        boolean isValid = Sig.verify(sealedAndSignedVO.getSigPublicKey(),
                Hash.getHash(sealedAndSignedVO.getEncryptedPayloadWithHmac()),
                sealedAndSignedVO.getSignature());
        if (!isValid)
            throw new CryptoException("Signature verification failed.");

        Message decryptedPayload = null;
        try {
            decryptedPayload = ProtoBufferUtilities
                    .fromProtoBuf(PB.Envelope.parseFrom(Encryption.decryptPayloadWithHmac(sealedAndSignedVO.getEncryptedPayloadWithHmac(), secretKey))).get();
        } catch (InvalidProtocolBufferException e) {
            throw new CryptoException("Unable to parse protobuffer message.", e);
        }
        return new DecryptedDataTuple(decryptedPayload, sealedAndSignedVO.getSigPublicKey());
    }
}
