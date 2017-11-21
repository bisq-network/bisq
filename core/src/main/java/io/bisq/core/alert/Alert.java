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

package io.bisq.core.alert;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode
@Getter
@ToString
@Slf4j
public final class Alert implements ProtectedStoragePayload {
    private final String message;
    private final boolean isUpdateInfo;
    private final String version;

    @Nullable
    private byte[] ownerPubKeyBytes;
    @Nullable
    private String signatureAsBase64;
    @Nullable
    private PublicKey ownerPubKey;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility 
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new 
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    public Alert(String message,
                 boolean isUpdateInfo,
                 String version) {
        this.message = message;
        this.isUpdateInfo = isUpdateInfo;
        this.version = version;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("NullableProblems")
    public Alert(String message,
                 boolean isUpdateInfo,
                 String version,
                 byte[] ownerPubKeyBytes,
                 String signatureAsBase64,
                 Map<String, String> extraDataMap) {
        this.message = message;
        this.isUpdateInfo = isUpdateInfo;
        this.version = version;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        this.signatureAsBase64 = signatureAsBase64;
        this.extraDataMap = extraDataMap;

        ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyBytes);
    }

    @Override
    public PB.StoragePayload toProtoMessage() {
        checkNotNull(ownerPubKeyBytes, "storagePublicKeyBytes must not be null");
        checkNotNull(signatureAsBase64, "signatureAsBase64 must not be null");
        final PB.Alert.Builder builder = PB.Alert.newBuilder()
                .setMessage(message)
                .setIsUpdateInfo(isUpdateInfo)
                .setVersion(version)
                .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes))
                .setSignatureAsBase64(signatureAsBase64);
        Optional.ofNullable(getExtraDataMap()).ifPresent(builder::putAllExtraData);
        return PB.StoragePayload.newBuilder().setAlert(builder).build();
    }

    public static Alert fromProto(PB.Alert proto) {
        return new Alert(proto.getMessage(),
                proto.getIsUpdateInfo(),
                proto.getVersion(),
                proto.getOwnerPubKeyBytes().toByteArray(),
                proto.getSignatureAsBase64(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ?
                        null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TimeUnit.DAYS.toMillis(30);
    }

    public void setSigAndPubKey(String signatureAsBase64, PublicKey ownerPubKey) {
        this.signatureAsBase64 = signatureAsBase64;
        this.ownerPubKey = ownerPubKey;

        ownerPubKeyBytes = Sig.getPublicKeyBytes(ownerPubKey);
    }

    public boolean isNewVersion() {
        return Version.isNewVersion(version);
    }
}
