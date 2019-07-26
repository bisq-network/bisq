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

package bisq.common.crypto;

import bisq.common.proto.network.NetworkPayload;

import com.google.protobuf.ByteString;

import java.security.PublicKey;

import lombok.Value;

@Value
public final class SealedAndSigned implements NetworkPayload {
    private final byte[] encryptedSecretKey;
    private final byte[] encryptedPayloadWithHmac;
    private final byte[] signature;
    private final byte[] sigPublicKeyBytes;
    transient private final PublicKey sigPublicKey;

    public SealedAndSigned(byte[] encryptedSecretKey,
                           byte[] encryptedPayloadWithHmac,
                           byte[] signature,
                           PublicKey sigPublicKey) {
        this.encryptedSecretKey = encryptedSecretKey;
        this.encryptedPayloadWithHmac = encryptedPayloadWithHmac;
        this.signature = signature;
        this.sigPublicKey = sigPublicKey;

        sigPublicKeyBytes = Sig.getPublicKeyBytes(sigPublicKey);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private SealedAndSigned(byte[] encryptedSecretKey,
                            byte[] encryptedPayloadWithHmac,
                            byte[] signature,
                            byte[] sigPublicKeyBytes) {
        this.encryptedSecretKey = encryptedSecretKey;
        this.encryptedPayloadWithHmac = encryptedPayloadWithHmac;
        this.signature = signature;
        this.sigPublicKeyBytes = sigPublicKeyBytes;

        sigPublicKey = Sig.getPublicKeyFromBytes(sigPublicKeyBytes);
    }

    public protobuf.SealedAndSigned toProtoMessage() {
        return protobuf.SealedAndSigned.newBuilder()
                .setEncryptedSecretKey(ByteString.copyFrom(encryptedSecretKey))
                .setEncryptedPayloadWithHmac(ByteString.copyFrom(encryptedPayloadWithHmac))
                .setSignature(ByteString.copyFrom(signature))
                .setSigPublicKeyBytes(ByteString.copyFrom(sigPublicKeyBytes))
                .build();
    }

    public static SealedAndSigned fromProto(protobuf.SealedAndSigned proto) {
        return new SealedAndSigned(proto.getEncryptedSecretKey().toByteArray(),
                proto.getEncryptedPayloadWithHmac().toByteArray(),
                proto.getSignature().toByteArray(),
                proto.getSigPublicKeyBytes().toByteArray());
    }
}
