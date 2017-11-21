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

package io.bisq.common.crypto;

import com.google.protobuf.ByteString;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.util.Utilities;
import io.bisq.consensus.RestrictedByContractJson;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openpgp.PGPPublicKey;

import javax.annotation.Nullable;
import java.security.PublicKey;

/**
 * Same as KeyRing but with public keys only.
 * Used to send public keys over the wire to other peer.
 */
@Slf4j
@EqualsAndHashCode
@Getter
public final class PubKeyRing implements NetworkPayload, RestrictedByContractJson {
    private final byte[] signaturePubKeyBytes;
    private final byte[] encryptionPubKeyBytes;
    @Nullable
    private final String pgpPubKeyAsPem;

    private PublicKey signaturePubKey;
    private PublicKey encryptionPubKey;
    @Nullable
    private PGPPublicKey pgpPubKey;

    public PubKeyRing(PublicKey signaturePubKey, PublicKey encryptionPubKey, @Nullable PGPPublicKey pgpPubKey) {
        this.signaturePubKeyBytes = Sig.getPublicKeyBytes(signaturePubKey);
        this.encryptionPubKeyBytes = Encryption.getPublicKeyBytes(encryptionPubKey);
        this.pgpPubKeyAsPem = PGP.getPEMFromPubKey(pgpPubKey);

        this.signaturePubKey = signaturePubKey;
        this.encryptionPubKey = encryptionPubKey;
        this.pgpPubKey = pgpPubKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PubKeyRing(byte[] signaturePubKeyBytes, byte[] encryptionPubKeyBytes, @Nullable String pgpPubKeyAsPem) {
        this.signaturePubKeyBytes = signaturePubKeyBytes;
        this.encryptionPubKeyBytes = encryptionPubKeyBytes;
        this.pgpPubKeyAsPem = pgpPubKeyAsPem;

        signaturePubKey = Sig.getPublicKeyFromBytes(signaturePubKeyBytes);
        encryptionPubKey = Encryption.getPublicKeyFromBytes(encryptionPubKeyBytes);
        if (pgpPubKeyAsPem != null)
            pgpPubKey = PGP.getPubKeyFromPem(pgpPubKeyAsPem);
    }

    @Override
    public PB.PubKeyRing toProtoMessage() {
        return PB.PubKeyRing.newBuilder()
                .setSignaturePubKeyBytes(ByteString.copyFrom(signaturePubKeyBytes))
                .setEncryptionPubKeyBytes(ByteString.copyFrom(encryptionPubKeyBytes))
                .setPgpPubKeyAsPem(pgpPubKeyAsPem)
                .build();
    }

    public static PubKeyRing fromProto(PB.PubKeyRing proto) {
        return new PubKeyRing(proto.getSignaturePubKeyBytes().toByteArray(),
                proto.getEncryptionPubKeyBytes().toByteArray(),
                proto.getPgpPubKeyAsPem());
    }

    @Override
    public String toString() {
        return "PubKeyRing{" +
                "signaturePubKeyHex=" + Utilities.bytesAsHexString(signaturePubKeyBytes) +
                ", encryptionPubKeyHex=" + Utilities.bytesAsHexString(encryptionPubKeyBytes) +
                ", pgpPubKeyAsString=" + pgpPubKeyAsPem +
                '}';
    }
}
