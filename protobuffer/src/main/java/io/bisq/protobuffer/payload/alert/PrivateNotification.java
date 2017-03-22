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

package io.bisq.protobuffer.payload.alert;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.payload.Payload;
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

public final class PrivateNotification implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private static final Logger log = LoggerFactory.getLogger(PrivateNotification.class);

    // Payload
    public final String message;
    private String signatureAsBase64;
    private byte[] publicKeyBytes;

    // Domain
    private transient PublicKey publicKey;

    public PrivateNotification(String message) {
        this.message = message;
    }

    public PrivateNotification(String message, String signatureAsBase64, byte[] publicKeyBytes) {
        this(message);
        this.signatureAsBase64 = signatureAsBase64;
        this.publicKeyBytes = publicKeyBytes;
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
            publicKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
            log.error("Could not create public key from bytes", e);
        }
    }

    public void setSigAndPubKey(String signatureAsBase64, PublicKey storagePublicKey) {
        this.signatureAsBase64 = signatureAsBase64;
        this.publicKey = storagePublicKey;
        this.publicKeyBytes = new X509EncodedKeySpec(this.publicKey.getEncoded()).getEncoded();
    }

    public String getSignatureAsBase64() {
        return signatureAsBase64;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrivateNotification)) return false;

        PrivateNotification that = (PrivateNotification) o;

        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        if (signatureAsBase64 != null ? !signatureAsBase64.equals(that.signatureAsBase64) : that.signatureAsBase64 != null)
            return false;
        return Arrays.equals(publicKeyBytes, that.publicKeyBytes);

    }

    @Override
    public int hashCode() {
        int result = message != null ? message.hashCode() : 0;
        result = 31 * result + (signatureAsBase64 != null ? signatureAsBase64.hashCode() : 0);
        result = 31 * result + (publicKeyBytes != null ? Arrays.hashCode(publicKeyBytes) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PrivateNotification{" +
                "message='" + message + '\'' +
                ", signatureAsBase64='" + signatureAsBase64 + '\'' +
                ", publicKeyBytes=" + Arrays.toString(publicKeyBytes) +
                '}';
    }

    @Override
    public PB.PrivateNotification toProto() {
        return PB.PrivateNotification.newBuilder()
                .setMessage(message)
                .setSignatureAsBase64(signatureAsBase64)
                .setPublicKeyBytes(ByteString.copyFrom(publicKeyBytes)).build();
    }
}
