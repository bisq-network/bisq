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

package io.bisq.common.crypto.vo;

import io.bisq.common.app.Version;
import io.bisq.common.crypto.Encryption;
import io.bisq.common.crypto.PGP;
import io.bisq.common.crypto.Sig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.util.encoders.Hex;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

@Slf4j
// TODO remove Serializable/serialVersionUID after PB refactoring
public final class PubKeyRingVO implements Serializable {
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    @Getter
    private final byte[] signaturePubKeyBytes;
    @Getter
    private final byte[] encryptionPubKeyBytes;
    @Getter
    private final String pgpPubKeyAsPem;

    @Nullable
    private PublicKey signaturePubKey;
    @Nullable
    private PublicKey encryptionPubKey;
    @Nullable
    // TODO  remove Nullable once impl.
    private PGPPublicKey pgpPubKey;

    public PubKeyRingVO(@NotNull PublicKey signaturePubKey, @NotNull PublicKey encryptionPubKey, @Nullable PGPPublicKey pgpPubKey) {
        this.signaturePubKey = signaturePubKey;
        this.encryptionPubKey = encryptionPubKey;
        this.pgpPubKey = pgpPubKey;

        this.signaturePubKeyBytes = new X509EncodedKeySpec(signaturePubKey.getEncoded()).getEncoded();
        this.encryptionPubKeyBytes = new X509EncodedKeySpec(encryptionPubKey.getEncoded()).getEncoded();

        //TODO not impl yet
        pgpPubKeyAsPem = io.bisq.common.crypto.PGP.getPEMFromPubKey(pgpPubKey);
    }

    public PubKeyRingVO(byte[] signaturePubKeyBytes, byte[] encryptionPubKeyBytes, @NotNull String pgpPubKeyAsPem) {
        this.signaturePubKeyBytes = signaturePubKeyBytes;
        this.encryptionPubKeyBytes = encryptionPubKeyBytes;
        this.pgpPubKeyAsPem = pgpPubKeyAsPem;
    }

    @NotNull
    public PublicKey getSignaturePubKey() {
        if (signaturePubKey == null) {
            try {
                signaturePubKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC")
                        .generatePublic(new X509EncodedKeySpec(getSignaturePubKeyBytes()));
            } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
                e.printStackTrace();
                log.error(e.getMessage());
                throw new RuntimeException(e);
            }
        }
        return signaturePubKey;
    }

    @NotNull
    public PublicKey getEncryptionPubKey() {
        if (encryptionPubKey == null) {
            try {
                encryptionPubKey = KeyFactory.getInstance(Encryption.ASYM_KEY_ALGO, "BC")
                        .generatePublic(new X509EncodedKeySpec(getEncryptionPubKeyBytes()));
            } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
                e.printStackTrace();
                log.error(e.getMessage());
                throw new RuntimeException(e);
            }
        }
        return encryptionPubKey;
    }

    @Nullable
    public PGPPublicKey getPgpPubKey() {
        if (pgpPubKey == null) {
            try {
                pgpPubKey = PGP.getPubKeyFromPEM(getPgpPubKeyAsPem());
            } catch (InvalidKeySpecException | NoSuchAlgorithmException | IOException | PGPException e) {
                e.printStackTrace();
                log.error(e.getMessage());
                throw new RuntimeException(e);
            }
        }
        return pgpPubKey;
    }

    // Only use the raw data
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PubKeyRingVO that = (PubKeyRingVO) o;

        if (!Arrays.equals(signaturePubKeyBytes, that.signaturePubKeyBytes)) return false;
        if (!Arrays.equals(encryptionPubKeyBytes, that.encryptionPubKeyBytes)) return false;
        return !(pgpPubKeyAsPem != null ? !pgpPubKeyAsPem.equals(that.pgpPubKeyAsPem) : that.pgpPubKeyAsPem != null);

    }

    @Override
    public int hashCode() {
        int result = signaturePubKeyBytes != null ? Arrays.hashCode(signaturePubKeyBytes) : 0;
        result = 31 * result + (encryptionPubKeyBytes != null ? Arrays.hashCode(encryptionPubKeyBytes) : 0);
        result = 31 * result + (pgpPubKeyAsPem != null ? pgpPubKeyAsPem.hashCode() : 0);
        return result;
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
