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

package io.bisq.vo.crypto;

import io.bisq.common.crypto.Sig;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;


@Value
@Slf4j
@Immutable
public final class SealedAndSignedVO {
    private final byte[] encryptedSecretKey;
    private final byte[] encryptedPayloadWithHmac;
    private final byte[] signature;
    private final byte[] sigPublicKeyBytes;
    private final PublicKey sigPublicKey;

    public SealedAndSignedVO(byte[] encryptedSecretKey, byte[] encryptedPayloadWithHmac, byte[] signature, PublicKey sigPublicKey) {
        this.encryptedSecretKey = encryptedSecretKey;
        this.encryptedPayloadWithHmac = encryptedPayloadWithHmac;
        this.signature = signature;

        this.sigPublicKey = sigPublicKey;

        this.sigPublicKeyBytes = new X509EncodedKeySpec(this.sigPublicKey.getEncoded()).getEncoded();
    }

    public SealedAndSignedVO(byte[] encryptedSecretKey, byte[] encryptedPayloadWithHmac, byte[] signature, byte[] sigPublicKeyBytes) {
        this.encryptedSecretKey = encryptedSecretKey;
        this.encryptedPayloadWithHmac = encryptedPayloadWithHmac;
        this.signature = signature;

        this.sigPublicKeyBytes = sigPublicKeyBytes;
        try {
            sigPublicKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC")
                    .generatePublic(new X509EncodedKeySpec(sigPublicKeyBytes));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
            log.error("Error creating sigPublicKey", e);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
