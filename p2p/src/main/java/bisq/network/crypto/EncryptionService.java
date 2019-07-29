/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.crypto;

import bisq.network.p2p.DecryptedMessageWithPubKey;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.Hash;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.SealedAndSigned;
import bisq.common.crypto.Sig;
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkProtoResolver;

import com.google.protobuf.InvalidProtocolBufferException;

import javax.inject.Inject;

import javax.crypto.SecretKey;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.crypto.Encryption.decryptSecretKey;

@Slf4j
public class EncryptionService {
    private final KeyRing keyRing;
    private final NetworkProtoResolver networkProtoResolver;

    @Inject
    public EncryptionService(KeyRing keyRing, NetworkProtoResolver networkProtoResolver) {
        this.keyRing = keyRing;
        this.networkProtoResolver = networkProtoResolver;
    }

    public SealedAndSigned encryptAndSign(PubKeyRing pubKeyRing, NetworkEnvelope networkEnvelope) throws CryptoException {
        return encryptHybridWithSignature(networkEnvelope, keyRing.getSignatureKeyPair(), pubKeyRing.getEncryptionPubKey());
    }

    /**
     * @param sealedAndSigned The sealedAndSigned object.
     * @param privateKey      The private key for decryption
     * @return A DecryptedPayloadWithPubKey object.
     * @throws CryptoException
     */
    public DecryptedDataTuple decryptHybridWithSignature(SealedAndSigned sealedAndSigned, PrivateKey privateKey) throws
            CryptoException, ProtobufferException {
        SecretKey secretKey = decryptSecretKey(sealedAndSigned.getEncryptedSecretKey(), privateKey);
        boolean isValid = Sig.verify(sealedAndSigned.getSigPublicKey(),
                Hash.getSha256Hash(sealedAndSigned.getEncryptedPayloadWithHmac()),
                sealedAndSigned.getSignature());
        if (!isValid)
            throw new CryptoException("Signature verification failed.");

        try {
            final byte[] bytes = Encryption.decryptPayloadWithHmac(sealedAndSigned.getEncryptedPayloadWithHmac(), secretKey);
            final protobuf.NetworkEnvelope envelope = protobuf.NetworkEnvelope.parseFrom(bytes);
            NetworkEnvelope decryptedPayload = networkProtoResolver.fromProto(envelope);
            return new DecryptedDataTuple(decryptedPayload, sealedAndSigned.getSigPublicKey());
        } catch (InvalidProtocolBufferException e) {
            throw new ProtobufferException("Unable to parse protobuffer message.", e);
        }
    }

    public DecryptedMessageWithPubKey decryptAndVerify(SealedAndSigned sealedAndSigned) throws
            CryptoException, ProtobufferException {
        DecryptedDataTuple decryptedDataTuple = decryptHybridWithSignature(sealedAndSigned,
                keyRing.getEncryptionKeyPair().getPrivate());
        return new DecryptedMessageWithPubKey(decryptedDataTuple.getNetworkEnvelope(),
                decryptedDataTuple.getSigPublicKey());
    }

    private static byte[] encryptPayloadWithHmac(NetworkEnvelope networkEnvelope, SecretKey secretKey) throws CryptoException {
        return Encryption.encryptPayloadWithHmac(networkEnvelope.toProtoNetworkEnvelope().toByteArray(), secretKey);
    }

    /**
     * @param payload             The data to encrypt.
     * @param signatureKeyPair    The key pair for signing.
     * @param encryptionPublicKey The public key used for encryption.
     * @return A SealedAndSigned object.
     * @throws CryptoException
     */
    public static SealedAndSigned encryptHybridWithSignature(NetworkEnvelope payload, KeyPair signatureKeyPair,
                                                             PublicKey encryptionPublicKey)
            throws CryptoException {
        // Create a symmetric key
        SecretKey secretKey = Encryption.generateSecretKey(256);

        // Encrypt secretKey with receiver's publicKey
        byte[] encryptedSecretKey = Encryption.encryptSecretKey(secretKey, encryptionPublicKey);

        // Encrypt with sym key payload with appended hmac
        byte[] encryptedPayloadWithHmac = encryptPayloadWithHmac(payload, secretKey);

        // sign hash of encryptedPayloadWithHmac
        byte[] hash = Hash.getSha256Hash(encryptedPayloadWithHmac);
        byte[] signature = Sig.sign(signatureKeyPair.getPrivate(), hash);

        // Pack all together
        return new SealedAndSigned(encryptedSecretKey, encryptedPayloadWithHmac, signature, signatureKeyPair.getPublic());
    }
}

