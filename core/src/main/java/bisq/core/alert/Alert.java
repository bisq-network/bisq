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

package bisq.core.alert;

import bisq.core.user.Preferences;

import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Version;
import bisq.common.crypto.Sig;
import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.encoding.canonical.CanonicalSchema;
import bisq.common.encoding.canonical.TreeMapIterator;
import bisq.common.proto.network.GetDataResponsePriority;
import bisq.common.util.CollectionUtils;
import bisq.common.util.ExtraDataMapValidator;
import bisq.common.util.Hex;

import com.google.protobuf.ByteString;

import java.security.PublicKey;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.VisibleForTesting;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode
@Getter
@Slf4j
public final class Alert implements ProtectedStoragePayload, ExpirablePayload {
    public static final long TTL = TimeUnit.DAYS.toMillis(90);

    private final String message;
    private final boolean isUpdateInfo;
    private final boolean isPreReleaseInfo;
    private final String version;

    @Nullable
    private byte[] ownerPubKeyBytes;
    @Nullable
    private String signatureAsBase64;
    @Nullable
    private PublicKey ownerPubKey;

    @Nullable
    private final TreeMap<String, String> extraDataMap;

    public Alert(String message,
                 boolean isUpdateInfo,
                 boolean isPreReleaseInfo,
                 String version) {
        this(message,
                isUpdateInfo,
                isPreReleaseInfo,
                version,
                null);
    }

    public Alert(String message,
                 boolean isUpdateInfo,
                 boolean isPreReleaseInfo,
                 String version,
                 @Nullable TreeMap<String, String> extraDataMap) {
        this.message = message;
        this.isUpdateInfo = isUpdateInfo;
        this.isPreReleaseInfo = isPreReleaseInfo;
        this.version = version;
        Map<String, String> validatedExtraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);
        this.extraDataMap = validatedExtraDataMap == null ? null : new TreeMap<>(validatedExtraDataMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER

    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    public Alert(String message,
                 boolean isUpdateInfo,
                 boolean isPreReleaseInfo,
                 String version,
                 byte[] ownerPubKeyBytes,
                 String signatureAsBase64) {
        this(message,
                isUpdateInfo,
                isPreReleaseInfo,
                version,
                ownerPubKeyBytes,
                signatureAsBase64,
                null);
    }

    @SuppressWarnings("NullableProblems")
    @VisibleForTesting
    public Alert(String message,
                 boolean isUpdateInfo,
                 boolean isPreReleaseInfo,
                 String version,
                 byte[] ownerPubKeyBytes,
                 String signatureAsBase64,
                 @Nullable TreeMap<String, String> extraDataMap) {
        this.message = message;
        this.isUpdateInfo = isUpdateInfo;
        this.isPreReleaseInfo = isPreReleaseInfo;
        this.version = version;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        this.signatureAsBase64 = signatureAsBase64;
        Map<String, String> validatedExtraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);
        this.extraDataMap = validatedExtraDataMap == null ? null : new TreeMap<>(validatedExtraDataMap);

        ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyBytes);
    }

    @Override
    public protobuf.StoragePayload toProtoMessage() {
        checkNotNull(ownerPubKeyBytes, "storagePublicKeyBytes must not be null");
        checkNotNull(signatureAsBase64, "signatureAsBase64 must not be null");
        protobuf.Alert.Builder builder = protobuf.Alert.newBuilder()
                .setMessage(message)
                .setIsUpdateInfo(isUpdateInfo)
                .setIsPreReleaseInfo(isPreReleaseInfo)
                .setVersion(version)
                .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes))
                .setSignatureAsBase64(signatureAsBase64);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return protobuf.StoragePayload.newBuilder().setAlert(builder).build();
    }

    @Nullable
    public static Alert fromProto(protobuf.Alert proto) {
        // We got in dev testing sometimes an empty protobuf Alert. Not clear why that happened but as it causes an
        // exception and corrupted user db file we prefer to set it to null.
        if (proto.getSignatureAsBase64().isEmpty())
            return null;

        return new Alert(proto.getMessage(),
                proto.getIsUpdateInfo(),
                proto.getIsPreReleaseInfo(),
                proto.getVersion(),
                proto.getOwnerPubKeyBytes().toByteArray(),
                proto.getSignatureAsBase64(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ?
                        null : new TreeMap<>(proto.getExtraDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static final CanonicalSchema<Alert> SCHEMA =
            CanonicalSchema.oneof("StoragePayload",
                    1,
                    CanonicalSchema.<Alert>newBuilder()
                            .string(1, alert -> alert.message)
                            .string(2, alert -> alert.version)
                            .bool(3, alert -> alert.isUpdateInfo)
                            .string(4, alert -> alert.signatureAsBase64)
                            .bytes(5, alert -> alert.ownerPubKeyBytes)
                            .mapStringToString(6,
                                    Alert::getExtraDataMapForCanonical,
                                    TreeMapIterator.naturalOrder())
                            .bool(7, alert -> alert.isPreReleaseInfo));

    @Override
    public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
        if (signatureAsBase64 == null) {
            throw new IllegalStateException("signatureAsBase64 must not be null for canonical Alert encoding");
        }
        if (ownerPubKeyBytes == null) {
            throw new IllegalStateException("ownerPubKeyBytes must not be null for canonical Alert encoding");
        }
        return canonicalEncoder.encode(this, SCHEMA);
    }

    private Map<String, String> getExtraDataMapForCanonical() {
        return extraDataMap == null ? Collections.emptyMap() : extraDataMap;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    @Override
    public Map<String, String> getExtraDataMap() {
        return extraDataMap;
    }

    @Override
    public GetDataResponsePriority getGetDataResponsePriority() {
        return GetDataResponsePriority.HIGH;
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    public void setSigAndPubKey(String signatureAsBase64, PublicKey ownerPubKey) {
        this.signatureAsBase64 = signatureAsBase64;
        this.ownerPubKey = ownerPubKey;

        ownerPubKeyBytes = Sig.getPublicKeyBytes(ownerPubKey);
    }

    public boolean isNewVersion(Preferences preferences) {
        // regular release: always notify user
        // pre-release: if user has set preference to receive pre-release notification
        if (isUpdateInfo ||
                (isPreReleaseInfo && preferences.isNotifyOnPreRelease())) {
            return Version.isNewVersion(version);
        }
        return false;
    }

    public boolean isSoftwareUpdateNotification() {
        return (isUpdateInfo || isPreReleaseInfo);
    }

    public boolean canShowPopup(Preferences preferences) {
        // only show popup if its version is newer than current
        // and only if user has not checked "don't show again"
        return isNewVersion(preferences) && preferences.showAgain(showAgainKey());
    }

    public String showAgainKey() {
        return "Update_" + version;
    }

    @Override
    public String toString() {
        return "Alert{" +
                "message='" + message + '\'' +
                ", isUpdateInfo=" + isUpdateInfo +
                ", isPreReleaseInfo=" + isPreReleaseInfo +
                ", version='" + version + '\'' +
                ", ownerPubKeyAsHex=" + (ownerPubKeyBytes == null ? "null" : Hex.encode(ownerPubKeyBytes)) +
                ", signatureAsBase64='" + signatureAsBase64 + '\'' +
                ", extraDataMap=" + extraDataMap +
                '}';
    }
}
