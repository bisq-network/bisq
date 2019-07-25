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

import bisq.common.crypto.Sig;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.security.PublicKey;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nullable;

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
    private PublicKey sigPublicKey;

    public PrivateNotificationPayload(String message) {
        this.message = message;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("NullableProblems")
    private PrivateNotificationPayload(String message, String signatureAsBase64, byte[] sigPublicKeyBytes) {
        this(message);
        this.signatureAsBase64 = signatureAsBase64;
        this.sigPublicKeyBytes = sigPublicKeyBytes;
        sigPublicKey = Sig.getPublicKeyFromBytes(sigPublicKeyBytes);
    }

    public static PrivateNotificationPayload fromProto(protobuf.PrivateNotificationPayload proto) {
        return new PrivateNotificationPayload(proto.getMessage(),
                proto.getSignatureAsBase64(),
                proto.getSigPublicKeyBytes().toByteArray());
    }

    @Override
    public protobuf.PrivateNotificationPayload toProtoMessage() {
        checkNotNull(sigPublicKeyBytes, "sigPublicKeyBytes must not be null");
        checkNotNull(signatureAsBase64, "signatureAsBase64 must not be null");
        return protobuf.PrivateNotificationPayload.newBuilder()
                .setMessage(message)
                .setSignatureAsBase64(signatureAsBase64)
                .setSigPublicKeyBytes(ByteString.copyFrom(sigPublicKeyBytes))
                .build();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setSigAndPubKey(String signatureAsBase64, PublicKey sigPublicKey) {
        this.signatureAsBase64 = signatureAsBase64;
        this.sigPublicKey = sigPublicKey;
        sigPublicKeyBytes = Sig.getPublicKeyBytes(sigPublicKey);
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
