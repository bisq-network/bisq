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
import io.bisq.common.Payload;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.util.encoders.Hex;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
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
@Slf4j
@EqualsAndHashCode
public final class PubKeyRing implements Payload {
    private final byte[] signaturePubKeyBytes;
    private final byte[] encryptionPubKeyBytes;
    private String pgpPubKeyAsPem;

    transient private PublicKey signaturePubKey;
    transient private PublicKey encryptionPubKey;

    @Nullable
    // TODO  remove Nullable once impl.
    transient private PGPPublicKey pgpPubKey;

    public PubKeyRing(PublicKey signaturePubKey, PublicKey encryptionPubKey, @Nullable PGPPublicKey pgpPubKey) {
        this(new X509EncodedKeySpec(signaturePubKey.getEncoded()).getEncoded(),
                new X509EncodedKeySpec(encryptionPubKey.getEncoded()).getEncoded(),
                PGP.getPEMFromPubKey(pgpPubKey));

        this.signaturePubKey = signaturePubKey;
        this.encryptionPubKey = encryptionPubKey;
        this.pgpPubKey = pgpPubKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PubKeyRing(byte[] signaturePubKeyBytes, byte[] encryptionPubKeyBytes, @NotNull String pgpPubKeyAsPem) {
        this.signaturePubKeyBytes = signaturePubKeyBytes;
        this.encryptionPubKeyBytes = encryptionPubKeyBytes;
        this.pgpPubKeyAsPem = pgpPubKeyAsPem;
    }

    @Override
    public PB.PubKeyRing toProto() {
        return PB.PubKeyRing.newBuilder()
                .setSignaturePubKeyBytes(ByteString.copyFrom(signaturePubKeyBytes))
                .setEncryptionPubKeyBytes(ByteString.copyFrom(encryptionPubKeyBytes))
                .setPgpPubKeyAsPem(pgpPubKeyAsPem)
                .build();
    }

    public static PubKeyRing fromProto(PB.PubKeyRing pubKeyRing) {
        return new PubKeyRing(pubKeyRing.getSignaturePubKeyBytes().toByteArray(),
                pubKeyRing.getEncryptionPubKeyBytes().toByteArray(),
                pubKeyRing.getPgpPubKeyAsPem());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PublicKey getSignaturePubKey() {
        if (signaturePubKey == null) {
            try {
                signaturePubKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC")
                        .generatePublic(new X509EncodedKeySpec(signaturePubKeyBytes));
            } catch (InvalidKeySpecException | NoSuchProviderException | NoSuchAlgorithmException e) {
                log.error("Key cannot be created. error={}, signaturePubKeyBytes.length={}", e.getMessage(), signaturePubKeyBytes.length);
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return signaturePubKey;
    }

    public PublicKey getEncryptionPubKey() {
        if (encryptionPubKey == null) {
            try {
                encryptionPubKey = KeyFactory.getInstance(Encryption.ASYM_KEY_ALGO, "BC").generatePublic(new X509EncodedKeySpec(encryptionPubKeyBytes));
            } catch (InvalidKeySpecException | NoSuchProviderException | NoSuchAlgorithmException e) {
                log.error("Key cannot be created. error={}, encryptionPubKeyBytes.length={}", e.getMessage(), encryptionPubKeyBytes.length);
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return encryptionPubKey;
    }

    public PGPPublicKey getPgpPubKey() {
        if (pgpPubKey == null)
            try {
                pgpPubKey = PGP.getPubKeyFromPEM(pgpPubKeyAsPem);
            } catch (NoSuchAlgorithmException | PGPException | IOException | InvalidKeySpecException e) {
                log.error("Key cannot be created. error={}, pgpPubKeyAsPem={}", e.getMessage(), pgpPubKeyAsPem);
                e.printStackTrace();
                //throw new RuntimeException(e);
            }
        return pgpPubKey;
    }

    // Hex
    @Override
    public String toString() {
        return "PubKeyRing{" +
                "signaturePubKeyHex=" + Hex.toHexString(signaturePubKeyBytes) +
                ", encryptionPubKeyHex=" + Hex.toHexString(encryptionPubKeyBytes) +
                ", pgpPubKeyAsString=" + pgpPubKeyAsPem +
                '}';
    }
}
