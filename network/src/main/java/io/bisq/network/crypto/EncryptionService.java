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

import io.bisq.common.crypto.CryptoException;
import io.bisq.network.p2p.DecryptedMsgWithPubKey;
import io.bisq.protobuffer.crypto.DecryptedDataTuple;
import io.bisq.protobuffer.message.Message;
import io.bisq.vo.crypto.KeyRingVO;
import io.bisq.vo.crypto.PubKeyRingVO;
import io.bisq.vo.crypto.SealedAndSignedVO;

import javax.inject.Inject;

public class EncryptionService {
    private final KeyRingVO keyRingVO;

    @Inject
    public EncryptionService(KeyRingVO keyRingVO) {
        this.keyRingVO = keyRingVO;
    }

    public SealedAndSignedVO encryptAndSign(PubKeyRingVO pubKeyRingVO, Message message) throws CryptoException {
        return NetworkCryptoUtils.encryptHybridWithSignature(message, keyRingVO.getSignatureKeyPair(), pubKeyRingVO.getEncryptionPubKey());
    }

    public DecryptedMsgWithPubKey decryptAndVerify(SealedAndSignedVO sealedAndSignedVO) throws CryptoException {
        DecryptedDataTuple decryptedDataTuple = NetworkCryptoUtils.decryptHybridWithSignature(sealedAndSignedVO,
                keyRingVO.getEncryptionKeyPair().getPrivate());
        return new DecryptedMsgWithPubKey(decryptedDataTuple.payload,
                decryptedDataTuple.sigPublicKey);
    }
}

