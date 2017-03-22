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

package io.bisq.protobuffer.payload.crypto;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.crypto.Encryption;
import io.bisq.protobuffer.payload.Payload;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * Same as KeyRing but with public keys only.
 * Used to send public keys over the wire to other peer.
 */
@Slf4j
public final class PubKeyRing implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;


    // Payload
    private final byte[] signaturePubKeyBytes;
    private final byte[] encryptionPubKeyBytes;

    // Domain
    @Getter
    transient private PublicKey signaturePubKey;
    @Getter
    transient private PublicKey encryptionPubKey;

    public PubKeyRing(PublicKey signaturePubKey, PublicKey encryptionPubKey) {
        this.signaturePubKey = signaturePubKey;
        this.encryptionPubKey = encryptionPubKey;
        this.signaturePubKeyBytes = new X509EncodedKeySpec(signaturePubKey.getEncoded()).getEncoded();
        this.encryptionPubKeyBytes = new X509EncodedKeySpec(encryptionPubKey.getEncoded()).getEncoded();
    }

    public PubKeyRing(byte[] signaturePubKeyBytes, byte[] encryptionPubKeyBytes) {
        this.signaturePubKeyBytes = signaturePubKeyBytes;
        this.encryptionPubKeyBytes = encryptionPubKeyBytes;
        init();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            init();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    private void init() {
        try {
            signaturePubKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC")
                    .generatePublic(new X509EncodedKeySpec(signaturePubKeyBytes));
            encryptionPubKey = KeyFactory.getInstance(Encryption.ASYM_KEY_ALGO, "BC")
                    .generatePublic(new X509EncodedKeySpec(encryptionPubKeyBytes));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }
    }

    @Override
    public PB.PubKeyRing toProto() {
        return PB.PubKeyRing.newBuilder().setSignaturePubKeyBytes(ByteString.copyFrom(signaturePubKeyBytes))
                .setEncryptionPubKeyBytes(ByteString.copyFrom(encryptionPubKeyBytes)).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PubKeyRing that = (PubKeyRing) o;

        if (!Arrays.equals(signaturePubKeyBytes, that.signaturePubKeyBytes)) return false;
        return Arrays.equals(encryptionPubKeyBytes, that.encryptionPubKeyBytes);
    }

    @Override
    public int hashCode() {
        int result = signaturePubKeyBytes != null ? Arrays.hashCode(signaturePubKeyBytes) : 0;
        result = 31 * result + (encryptionPubKeyBytes != null ? Arrays.hashCode(encryptionPubKeyBytes) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PubKeyRing{" +
                "signaturePubKey=" + Hex.toHexString(signaturePubKey.getEncoded()) +
                ", encryptionPubKey=" + Hex.toHexString(encryptionPubKey.getEncoded()) +
                '}';
    }
}
