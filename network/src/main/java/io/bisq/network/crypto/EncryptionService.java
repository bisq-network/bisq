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

package io.bisq.network.crypto;

import com.google.protobuf.InvalidProtocolBufferException;
import io.bisq.common.crypto.*;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.DecryptedMessageWithPubKey;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import javax.inject.Inject;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import static io.bisq.common.crypto.Encryption.decryptSecretKey;

@Slf4j
public class EncryptionService {
    private final KeyRing keyRing;
    private final NetworkProtoResolver networkProtoResolver;

    @Inject
    public EncryptionService(KeyRing keyRing, NetworkProtoResolver networkProtoResolver) {
        this.keyRing = keyRing;
        this.networkProtoResolver = networkProtoResolver;
    }

    public SealedAndSigned encryptAndSign(PubKeyRing pubKeyRing, NetworkEnvelope networkEnvelop) throws CryptoException {
        return encryptHybridWithSignature(networkEnvelop, keyRing.getSignatureKeyPair(), pubKeyRing.getEncryptionPubKey());
    }

    /**
     * @param sealedAndSigned The sealedAndSigned object.
     * @param privateKey      The private key for decryption
     * @return A DecryptedPayloadWithPubKey object.
     * @throws CryptoException
     */
    public DecryptedDataTuple decryptHybridWithSignature(SealedAndSigned sealedAndSigned, PrivateKey privateKey) throws CryptoException {
        SecretKey secretKey = decryptSecretKey(sealedAndSigned.getEncryptedSecretKey(), privateKey);
        boolean isValid = Sig.verify(sealedAndSigned.getSigPublicKey(),
                Hash.getSha256Hash(sealedAndSigned.getEncryptedPayloadWithHmac()),
                sealedAndSigned.getSignature());
        if (!isValid)
            throw new CryptoException("Signature verification failed.");

        try {
            final byte[] bytes = Encryption.decryptPayloadWithHmac(sealedAndSigned.getEncryptedPayloadWithHmac(), secretKey);
            final PB.NetworkEnvelope envelope = PB.NetworkEnvelope.parseFrom(bytes);
            NetworkEnvelope decryptedPayload = networkProtoResolver.fromProto(envelope);
            return new DecryptedDataTuple(decryptedPayload, sealedAndSigned.getSigPublicKey());
        } catch (InvalidProtocolBufferException e) {
            throw new CryptoException("Unable to parse protobuffer message.", e);
        }

    }

    public DecryptedMessageWithPubKey decryptAndVerify(SealedAndSigned sealedAndSigned) throws CryptoException {
        DecryptedDataTuple decryptedDataTuple = decryptHybridWithSignature(sealedAndSigned,
                keyRing.getEncryptionKeyPair().getPrivate());
        return new DecryptedMessageWithPubKey(decryptedDataTuple.getNetworkEnvelope(),
                decryptedDataTuple.getSigPublicKey());
    }

    private static byte[] encryptPayloadWithHmac(NetworkEnvelope networkEnvelop, SecretKey secretKey) throws CryptoException {
        return Encryption.encryptPayloadWithHmac(networkEnvelop.toProtoNetworkEnvelope().toByteArray(), secretKey);
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
        SecretKey secretKey = Encryption.generateSecretKey();

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

