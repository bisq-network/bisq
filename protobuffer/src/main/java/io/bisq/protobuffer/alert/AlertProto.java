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

package io.bisq.protobuffer.alert;

import com.google.protobuf.ByteString;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.Marshaller;
import io.bisq.vo.alert.AlertVO;
import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Immutable
public abstract class AlertProto implements Marshaller {
    @Delegate
    @Getter
    private final AlertVO alertVO;

    public AlertProto(AlertVO alertVO) {
        this.alertVO = alertVO;
    }

    @Override
    public PB.StoragePayload toProto() {
        checkNotNull(alertVO.getStoragePublicKeyBytes(), "storagePublicKeyBytes must not be null");
        checkNotNull(alertVO.getSignatureAsBase64(), "signatureAsBase64 must not be null");
        final PB.AlertProto.Builder builder = PB.AlertProto.newBuilder()
                .setMessage(alertVO.getMessage())
                .setVersion(alertVO.getVersion())
                .setIsUpdateInfo(alertVO.isUpdateInfo())
                .setSignatureAsBase64(alertVO.getSignatureAsBase64())
                .setStoragePublicKeyBytes(ByteString.copyFrom(alertVO.getStoragePublicKeyBytes()));
        Optional.ofNullable(alertVO.getExtraDataMap()).ifPresent(builder::putAllExtraDataMap);
        return PB.StoragePayload.newBuilder().setAlertProto(builder).build();
    }
}
