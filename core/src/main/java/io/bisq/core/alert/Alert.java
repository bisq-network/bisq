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

package io.bisq.core.alert;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.CryptoUtils;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.StoragePayload;
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
public final class Alert implements StoragePayload {
    //TODO remove after refact.
    private static final long serialVersionUID = 1;

    private final String message;
    private final String version;
    private final boolean isUpdateInfo;

    @Nullable
    private String signatureAsBase64;
    @Nullable
    private PublicKey storagePublicKey;
    @Nullable
    private final byte[] storagePublicKeyBytes;
    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility 
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new 
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    // StoragePayload
    private transient PublicKey ownerPubKey;


    public Alert(String message,
                 boolean isUpdateInfo,
                 String version,
                 @Nullable byte[] storagePublicKeyBytes,
                 @Nullable String signatureAsBase64,
                 @Nullable Map<String, String> extraDataMap) {
        this.message = message;
        this.isUpdateInfo = isUpdateInfo;
        this.version = version;
        this.storagePublicKeyBytes = storagePublicKeyBytes;
        this.signatureAsBase64 = signatureAsBase64;
        this.extraDataMap = extraDataMap;
    }

    public Alert(String message,
                 boolean isUpdateInfo,
                 String version) {
        this(message, isUpdateInfo, version, null, null, null);
    }


    public void setSigAndPubKey(String signatureAsBase64, PublicKey storagePublicKey) {
        this.signatureAsBase64 = signatureAsBase64;
        this.storagePublicKey = storagePublicKey;
    }

    public boolean isNewVersion() {
        return isNewVersion(Version.VERSION);
    }

    @VisibleForTesting
    protected boolean isNewVersion(String myVersion) {
        // We need to support different version usages (0.5, 0.5.1, 0.5.1.1.1, 0.5.10.1)
        // So we fill up the right part up to 8 digits 0.5 -> 05000000, 0.5.1.1.1 -> 05111000
        // that should handle all cases.
        // TODO make it more elegant and add tests :-)

        // In case the input comes in a corrupted format we don't want to screw up teh app
        try {
            String myVersionString = myVersion.replace(".", "");
            while (myVersionString.length() < 9)
                myVersionString += "0";
            int myVersionNum = Integer.valueOf(myVersionString);
            String alertVersionString = getVersion().replace(".", "");
            while (alertVersionString.length() < 9)
                alertVersionString += "0";
            int alertVersionNum = Integer.valueOf(alertVersionString);
            return myVersionNum < alertVersionNum;
        } catch (Throwable t) {
            return false;
        }
    }

    //StoragePayload
    @Override
    public long getTTL() {
        return TimeUnit.DAYS.toMillis(30);
    }

    @Override
    public PublicKey getOwnerPubKey() {
        try {
            checkNotNull(getStoragePublicKeyBytes(), "alertVO.getStoragePublicKeyBytes() must not be null");
            if (ownerPubKey == null)
                ownerPubKey = CryptoUtils.getPubKeyFromBytes(getStoragePublicKeyBytes());
            return ownerPubKey;
        } catch (Throwable t) {
            log.error(t.toString());
            return null;
        }
    }

    //Marshaller
    @Override
    public PB.StoragePayload toProtoMessage() {
        checkNotNull(getStoragePublicKeyBytes(), "storagePublicKeyBytes must not be null");
        checkNotNull(getSignatureAsBase64(), "signatureAsBase64 must not be null");
        final PB.Alert.Builder builder = PB.Alert.newBuilder()
                .setMessage(getMessage())
                .setVersion(getVersion())
                .setIsUpdateInfo(isUpdateInfo())
                .setSignatureAsBase64(getSignatureAsBase64())
                .setStoragePublicKeyBytes(ByteString.copyFrom(getStoragePublicKeyBytes()));
        Optional.ofNullable(getExtraDataMap()).ifPresent(builder::putAllExtraDataMap);
        return PB.StoragePayload.newBuilder().setAlert(builder).build();
    }

    public static Alert fromProto(PB.Alert alert) {
        return new Alert(alert.getMessage(),
                alert.getIsUpdateInfo(),
                alert.getVersion(),
                alert.getStoragePublicKeyBytes().toByteArray(),
                alert.getSignatureAsBase64(),
                CollectionUtils.isEmpty(alert.getExtraDataMapMap()) ?
                        null : alert.getExtraDataMapMap());
    }
}
