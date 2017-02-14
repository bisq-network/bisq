/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.common.crypto;

import com.google.protobuf.ByteString;
import io.bitsquare.messages.app.Version;
import io.bitsquare.common.wire.Payload;
import io.bitsquare.common.wire.proto.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

/**
 * Same as KeyRing but with public keys only.
 * Used to send public keys over the wire to other peer.
 */
public final class PubKeyRing implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(PubKeyRing.class);

    transient private PublicKey signaturePubKey;
    private final byte[] signaturePubKeyBytes;
    transient private PublicKey encryptionPubKey;
    private final byte[] encryptionPubKeyBytes;

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
        signaturePubKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC").generatePublic(new X509EncodedKeySpec(signaturePubKeyBytes));
        encryptionPubKey = KeyFactory.getInstance(Encryption.ASYM_KEY_ALGO, "BC").generatePublic(new X509EncodedKeySpec(encryptionPubKeyBytes));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }
    }

    public PublicKey getSignaturePubKey() {
        return signaturePubKey;
    }

    public PublicKey getEncryptionPubKey() {
        return encryptionPubKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PubKeyRing)) return false;

        PubKeyRing that = (PubKeyRing) o;

        if (signaturePubKey != null ? !signaturePubKey.equals(that.signaturePubKey) : that.signaturePubKey != null)
            return false;
        return !(encryptionPubKey != null ? !encryptionPubKey.equals(that.encryptionPubKey) : that.encryptionPubKey != null);

    }

    @Override
    public int hashCode() {
        int result = signaturePubKey != null ? signaturePubKey.hashCode() : 0;
        result = 31 * result + (encryptionPubKey != null ? encryptionPubKey.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PubKeyRing{" +
                "signaturePubKey.hashCode()=" + (signaturePubKey != null ? signaturePubKey.hashCode() : "") +
                ", encryptionPubKey.hashCode()=" + (encryptionPubKey != null ? encryptionPubKey.hashCode() : "") +
                '}';
    }

    @Override
    public Messages.PubKeyRing toProtoBuf() {
        return Messages.PubKeyRing.newBuilder().setSignaturePubKeyBytes(ByteString.copyFrom(signaturePubKeyBytes))
                .setEncryptionPubKeyBytes(ByteString.copyFrom(encryptionPubKeyBytes)).build();
    }
}
