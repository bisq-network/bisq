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

package io.bisq.protobuffer.crypto;

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.vo.PubKeyRingVO;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.Marshaller;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;


@EqualsAndHashCode
@Slf4j
public abstract class PubKeyRingProto implements Marshaller {

    @Delegate
    protected PubKeyRingVO pubKeyRingVO;

    public PubKeyRingVO get() {
        return pubKeyRingVO;
    }

    public PubKeyRingProto(PubKeyRingVO pubKeyRingVO) {
        this.pubKeyRingVO = pubKeyRingVO;
    }

    @Override
    public PB.PubKeyRingPayload toProto() {
        return PB.PubKeyRingPayload.newBuilder()
                .setSignaturePubKeyBytes(ByteString.copyFrom(pubKeyRingVO.getSignaturePubKeyBytes()))
                .setEncryptionPubKeyBytes(ByteString.copyFrom(pubKeyRingVO.getEncryptionPubKeyBytes()))
                .setPgpPubKeyAsPem(pubKeyRingVO.getPgpPubKeyAsPem())
                .build();
    }
}
