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

package io.bisq.wire.payload.alert;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.wire.payload.StoragePayload;
import io.bisq.wire.proto.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public final class Alert implements StoragePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private static final Logger log = LoggerFactory.getLogger(Alert.class);
    private static final long TTL = TimeUnit.DAYS.toMillis(21);

    // Payload
    public final String message;
    public final String version;
    public final boolean isUpdateInfo;
    private String signatureAsBase64;
    private byte[] storagePublicKeyBytes;

    // Domain
    private transient PublicKey storagePublicKey;

    public Alert(String message, boolean isUpdateInfo, String version) {
        this.message = message;
        this.isUpdateInfo = isUpdateInfo;
        this.version = version;
    }

    public Alert(String message, boolean isUpdateInfo, String version, byte[] storagePublicKeyBytes,
                 String signatureAsBase64) {
        this(message, isUpdateInfo, version);
        this.storagePublicKeyBytes = storagePublicKeyBytes;
        this.signatureAsBase64 = signatureAsBase64;
        init();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            init();
        } catch (Throwable t) {
            log.warn("Exception at readObject: " + t.getMessage());
        }
    }

    private void init() {
        try {
            storagePublicKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC")
                    .generatePublic(new X509EncodedKeySpec(storagePublicKeyBytes));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
            log.error("Couldn't create the storage public key", e);
        }
    }

    public void setSigAndPubKey(String signatureAsBase64, PublicKey storagePublicKey) {
        this.signatureAsBase64 = signatureAsBase64;
        this.storagePublicKey = storagePublicKey;
        this.storagePublicKeyBytes = new X509EncodedKeySpec(this.storagePublicKey.getEncoded()).getEncoded();
    }

    public String getSignatureAsBase64() {
        return signatureAsBase64;
    }

    public boolean isNewVersion() {
        return isNewVersion(Version.VERSION);
    }

    @VisibleForTesting
    protected boolean isNewVersion(String appVersion) {
        // Usually we use 3 digits (0.4.8) but to support also 4 digits in case of hotfixes (0.4.8.1) we 
        // add a 0 at all 3 digit versions to allow correct comparison: 0.4.8 -> 480; 0.4.8.1 -> 481; 481 > 480
        String myVersionString = appVersion.replace(".", "");
        if (myVersionString.length() == 3)
            myVersionString += "0";
        int versionNum = Integer.valueOf(myVersionString);

        String alertVersionString = version.replace(".", "");
        if (alertVersionString.length() == 3)
            alertVersionString += "0";
        int alertVersionNum = Integer.valueOf(alertVersionString);
        return versionNum < alertVersionNum;
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return storagePublicKey;
    }

    @Override
    public Messages.StoragePayload toProtoBuf() {
        return Messages.StoragePayload.newBuilder().setAlert(Messages.Alert.newBuilder().setTTL(TTL)
                .setMessage(message)
                .setVersion(version)
                .setIsUpdateInfo(isUpdateInfo)
                .setSignatureAsBase64(signatureAsBase64)
                .setStoragePublicKeyBytes(ByteString.copyFrom(storagePublicKeyBytes))).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Alert)) return false;

        Alert alert = (Alert) o;

        if (isUpdateInfo != alert.isUpdateInfo) return false;
        if (message != null ? !message.equals(alert.message) : alert.message != null) return false;
        if (version != null ? !version.equals(alert.version) : alert.version != null) return false;
        if (signatureAsBase64 != null ? !signatureAsBase64.equals(alert.signatureAsBase64) : alert.signatureAsBase64 != null)
            return false;
        return Arrays.equals(storagePublicKeyBytes, alert.storagePublicKeyBytes);

    }

    @Override
    public int hashCode() {
        int result = message != null ? message.hashCode() : 0;
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (isUpdateInfo ? 1 : 0);
        result = 31 * result + (signatureAsBase64 != null ? signatureAsBase64.hashCode() : 0);
        result = 31 * result + (storagePublicKeyBytes != null ? Arrays.hashCode(storagePublicKeyBytes) : 0);
        return result;
    }


    @Override
    public String toString() {
        return "Alert{" +
                "message='" + message + '\'' +
                ", version='" + version + '\'' +
                ", isUpdateInfo=" + isUpdateInfo +
                ", signatureAsBase64='" + signatureAsBase64 + '\'' +
                ", storagePublicKeyBytes=" + Arrays.toString(storagePublicKeyBytes) +
                '}';
    }
}
