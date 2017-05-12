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

package io.bisq.common.crypto;

import com.google.protobuf.ByteString;
import io.bisq.common.network.NetworkPayload;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

@Slf4j
@EqualsAndHashCode
public final class SealedAndSigned implements NetworkPayload {
    public final byte[] encryptedSecretKey;
    public final byte[] encryptedPayloadWithHmac;
    public final byte[] signature;
    private final byte[] sigPublicKeyBytes;
    public PublicKey sigPublicKey;

    public SealedAndSigned(byte[] encryptedSecretKey, byte[] encryptedPayloadWithHmac, byte[] signature, PublicKey sigPublicKey) {
        this.encryptedSecretKey = encryptedSecretKey;
        this.encryptedPayloadWithHmac = encryptedPayloadWithHmac;
        this.signature = signature;
        this.sigPublicKey = sigPublicKey;
        this.sigPublicKeyBytes = new X509EncodedKeySpec(this.sigPublicKey.getEncoded()).getEncoded();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SealedAndSigned(byte[] encryptedSecretKey, byte[] encryptedPayloadWithHmac, byte[] signature, byte[] sigPublicKeyBytes) {
        this(encryptedSecretKey, encryptedPayloadWithHmac, signature, Sig.getSigPublicKeyFromBytes(sigPublicKeyBytes));
    }

    public PB.SealedAndSigned toProtoMessage() {
        return PB.SealedAndSigned.newBuilder().setEncryptedSecretKey(ByteString.copyFrom(encryptedSecretKey))
                .setEncryptedPayloadWithHmac(ByteString.copyFrom(encryptedPayloadWithHmac))
                .setSignature(ByteString.copyFrom(signature)).setSigPublicKeyBytes(ByteString.copyFrom(sigPublicKeyBytes))
                .build();
    }
}
