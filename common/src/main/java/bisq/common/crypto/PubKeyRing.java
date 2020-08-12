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

import bisq.common.consensus.UsedForTradeContractJson;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import com.google.common.annotations.VisibleForTesting;

import java.security.PublicKey;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Same as KeyRing but with public keys only.
 * Used to send public keys over the wire to other peer.
 */
@Slf4j
@EqualsAndHashCode
@Getter
public final class PubKeyRing implements NetworkPayload, UsedForTradeContractJson {
    private final byte[] signaturePubKeyBytes;
    private final byte[] encryptionPubKeyBytes;

    private transient PublicKey signaturePubKey;
    private transient PublicKey encryptionPubKey;

    public PubKeyRing(PublicKey signaturePubKey, PublicKey encryptionPubKey) {
        this.signaturePubKeyBytes = Sig.getPublicKeyBytes(signaturePubKey);
        this.encryptionPubKeyBytes = Encryption.getPublicKeyBytes(encryptionPubKey);
        this.signaturePubKey = signaturePubKey;
        this.encryptionPubKey = encryptionPubKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    public PubKeyRing(byte[] signaturePubKeyBytes, byte[] encryptionPubKeyBytes) {
        this.signaturePubKeyBytes = signaturePubKeyBytes;
        this.encryptionPubKeyBytes = encryptionPubKeyBytes;
        signaturePubKey = Sig.getPublicKeyFromBytes(signaturePubKeyBytes);
        encryptionPubKey = Encryption.getPublicKeyFromBytes(encryptionPubKeyBytes);
    }

    @Override
    public protobuf.PubKeyRing toProtoMessage() {
        return protobuf.PubKeyRing.newBuilder()
                .setSignaturePubKeyBytes(ByteString.copyFrom(signaturePubKeyBytes))
                .setEncryptionPubKeyBytes(ByteString.copyFrom(encryptionPubKeyBytes))
                .build();
    }

    public static PubKeyRing fromProto(protobuf.PubKeyRing proto) {
        return new PubKeyRing(
                proto.getSignaturePubKeyBytes().toByteArray(),
                proto.getEncryptionPubKeyBytes().toByteArray());
    }

    @Override
    public String toString() {
        return "PubKeyRing{" +
                "signaturePubKeyHex=" + Utilities.bytesAsHexString(signaturePubKeyBytes) +
                ", encryptionPubKeyHex=" + Utilities.bytesAsHexString(encryptionPubKeyBytes) +
                "}";
    }
}
