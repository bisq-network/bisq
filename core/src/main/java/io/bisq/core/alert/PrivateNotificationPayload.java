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

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.Sig;
import io.bisq.common.network.NetworkPayload;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

@EqualsAndHashCode
@Slf4j
public final class PrivateNotificationPayload implements NetworkPayload {
    // Payload
    public final String message;
    private String signatureAsBase64;
    private byte[] publicKeyBytes;

    // Domain
    private transient PublicKey publicKey;

    public PrivateNotificationPayload(String message) {
        this.message = message;
    }

    public PrivateNotificationPayload(String message, String signatureAsBase64, byte[] publicKeyBytes) {
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
    public PB.PrivateNotificationPayload toProtoMessage() {
        return PB.PrivateNotificationPayload.newBuilder()
                .setMessage(message)
                .setSignatureAsBase64(signatureAsBase64)
                .setPublicKeyBytes(ByteString.copyFrom(publicKeyBytes)).build();
    }

    // Hex
    @Override
    public String toString() {
        return "PrivateNotification{" +
                "message='" + message + '\'' +
                ", signatureAsBase64='" + signatureAsBase64 + '\'' +
                ", publicKeyBytes=" + Hex.toHexString(publicKeyBytes) +
                '}';
    }
}
