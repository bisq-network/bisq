/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.network.crypto;

import com.google.protobuf.InvalidProtocolBufferException;
import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.Encryption;
import io.bisq.common.crypto.Hash;
import io.bisq.common.crypto.Sig;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.DecryptedMsgWithPubKey;
import io.bisq.protobuffer.ProtoBufferUtilities;
import io.bisq.protobuffer.crypto.DecryptedDataTuple;
import io.bisq.protobuffer.crypto.ProtoCryptoUtil;
import io.bisq.protobuffer.message.Message;
import io.bisq.vo.crypto.KeyRingVO;
import io.bisq.vo.crypto.PubKeyRingVO;
import io.bisq.vo.crypto.SealedAndSignedVO;

import javax.crypto.SecretKey;
import javax.inject.Inject;
import java.security.PrivateKey;

import static io.bisq.common.crypto.Encryption.decryptSecretKey;

public class EncryptionService {
    private final KeyRingVO keyRingVO;

    @Inject
    public EncryptionService(KeyRingVO keyRingVO) {
        this.keyRingVO = keyRingVO;
    }

    public SealedAndSignedVO encryptAndSign(PubKeyRingVO pubKeyRingVO, Message message) throws CryptoException {
        return ProtoCryptoUtil.encryptHybridWithSignature(message, keyRingVO.getSignatureKeyPair(), pubKeyRingVO.getEncryptionPubKey());
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

    public DecryptedMsgWithPubKey decryptAndVerify(SealedAndSignedVO sealedAndSignedVO) throws CryptoException {
        DecryptedDataTuple decryptedDataTuple = decryptHybridWithSignature(sealedAndSignedVO,
                keyRingVO.getEncryptionKeyPair().getPrivate());
        return new DecryptedMsgWithPubKey(decryptedDataTuple.payload,
                decryptedDataTuple.sigPublicKey);
    }
}

