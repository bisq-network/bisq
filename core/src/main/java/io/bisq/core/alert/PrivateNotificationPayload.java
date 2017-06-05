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
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.util.Utilities;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nullable;
import java.security.PublicKey;

import static com.google.common.base.Preconditions.checkNotNull;


@EqualsAndHashCode
@Getter
public final class PrivateNotificationPayload implements NetworkPayload {
    private final String message;
    @Nullable
    private String signatureAsBase64;
    @Nullable
    private byte[] sigPublicKeyBytes;
    @Nullable
    private PublicKey publicKey;

    public PrivateNotificationPayload(String message) {
        this.message = message;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PrivateNotificationPayload(String message, String signatureAsBase64, byte[] sigPublicKeyBytes) {
        this(message);
        this.signatureAsBase64 = signatureAsBase64;
        this.sigPublicKeyBytes = sigPublicKeyBytes;
        publicKey = Sig.getPublicKeyFromBytes(sigPublicKeyBytes);
    }

    public static PrivateNotificationPayload fromProto(PB.PrivateNotificationPayload proto) {
        return new PrivateNotificationPayload(proto.getMessage(),
                proto.getSignatureAsBase64(),
                proto.getSignatureAsBase64Bytes().toByteArray());
    }

    @Override
    public PB.PrivateNotificationPayload toProtoMessage() {
        checkNotNull(sigPublicKeyBytes, "sigPublicKeyBytes must nto be null");
        return PB.PrivateNotificationPayload.newBuilder()
                .setMessage(message)
                .setSignatureAsBase64(signatureAsBase64)
                .setSigPublicKeyBytes(ByteString.copyFrom(sigPublicKeyBytes)).build();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setSigAndPubKey(String signatureAsBase64, PublicKey storagePublicKey) {
        this.signatureAsBase64 = signatureAsBase64;
        this.publicKey = storagePublicKey;
        sigPublicKeyBytes = Sig.getPublicKeyBytes(publicKey);
    }

    // Hex
    @Override
    public String toString() {
        return "PrivateNotification{" +
                "message='" + message + '\'' +
                ", signatureAsBase64='" + signatureAsBase64 + '\'' +
                ", publicKeyBytes=" + Utilities.bytesAsHexString(sigPublicKeyBytes) +
                '}';
    }
}
