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

import io.bisq.common.crypto.Encryption;
import io.bisq.common.crypto.KeyStorage;
import io.bisq.common.crypto.PGP;
import io.bisq.common.crypto.Sig;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPublicKey;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import java.security.KeyPair;

@Value
@Slf4j
@Immutable
public class KeyRingVO {
    private final KeyPair signatureKeyPair;
    private final KeyPair encryptionKeyPair;
    private final PubKeyRingVO pubKeyRingVO;

    // We generate by default a PGP keypair but the user can set his own if he prefers.
    // Not impl. yet but prepared in data structure
    @Setter
    @Nullable
    // TODO  remove Nullable once impl.
    private PGPKeyPair pgpKeyPair;

    @Inject
    public KeyRingVO(KeyStorage keyStorage) {
        if (keyStorage.allKeyFilesExist()) {
            signatureKeyPair = keyStorage.loadKeyPair(KeyStorage.KeyEntry.MSG_SIGNATURE);
            encryptionKeyPair = keyStorage.loadKeyPair(KeyStorage.KeyEntry.MSG_ENCRYPTION);

            // TODO not impl
            pgpKeyPair = keyStorage.loadPgpKeyPair(KeyStorage.KeyEntry.PGP);
        } else {
            // First time we create key pairs
            signatureKeyPair = Sig.generateKeyPair();
            encryptionKeyPair = Encryption.generateKeyPair();

            // TODO not impl
            pgpKeyPair = PGP.generateKeyPair();
            keyStorage.saveKeyRing(this);
        }
        // TODO  remove Nullable once impl.
        final PGPPublicKey pgpPublicKey = pgpKeyPair != null ? pgpKeyPair.getPublicKey() : null;
        pubKeyRingVO = new PubKeyRingVO(signatureKeyPair.getPublic(), encryptionKeyPair.getPublic(), pgpPublicKey);
    }

    // Don't print keys for security reasons
    @Override
    public String toString() {
        return "KeyRing{" +
                "signatureKeyPair.hashCode()=" + signatureKeyPair.hashCode() +
                ", encryptionKeyPair.hashCode()=" + encryptionKeyPair.hashCode() +
                ", pubKeyRing.hashCode()=" + pubKeyRingVO.hashCode() +
                '}';
    }
}
