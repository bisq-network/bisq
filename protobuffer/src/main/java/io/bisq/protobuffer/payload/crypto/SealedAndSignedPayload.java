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

package io.bisq.protobuffer.payload.crypto;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.payload.Payload;
import io.bisq.vo.crypto.SealedAndSignedVO;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@Slf4j
public final class SealedAndSignedPayload implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    @Delegate
    private SealedAndSignedVO sealedAndSignedVO;

    public SealedAndSignedVO get() {
        return sealedAndSignedVO;
    }

    public SealedAndSignedPayload(SealedAndSignedVO sealedAndSignedVO) {
        this.sealedAndSignedVO = sealedAndSignedVO;
    }

    @Override
    public PB.SealedAndSignedPayload toProto() {
        return PB.SealedAndSignedPayload.newBuilder()
                .setEncryptedSecretKey(ByteString.copyFrom(sealedAndSignedVO.getEncryptedSecretKey()))
                .setEncryptedPayloadWithHmac(ByteString.copyFrom(sealedAndSignedVO.getEncryptedPayloadWithHmac()))
                .setSignature(ByteString.copyFrom(sealedAndSignedVO.getSignature()))
                .setSigPublicKeyBytes(ByteString.copyFrom(sealedAndSignedVO.getSigPublicKeyBytes()))
                .build();
    }
}
