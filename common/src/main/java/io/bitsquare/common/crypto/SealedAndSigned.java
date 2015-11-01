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

import io.bitsquare.app.Version;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.Arrays;

/**
 * Packs the encrypted symmetric secretKey and the encrypted and signed message into one object.
 * SecretKey is encrypted with asymmetric pubKey of peer. Signed message is encrypted with secretKey.
 * Using that hybrid encryption model we are not restricted by data size and performance as symmetric encryption is very fast.
 */
public final class SealedAndSigned implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final byte[] encryptedSecretKey;
    public final byte[] encryptedPayloadWithHmac;
    public final byte[] signature;
    public final PublicKey sigPublicKey;

    public SealedAndSigned(byte[] encryptedSecretKey, byte[] encryptedPayloadWithHmac, byte[] signature, PublicKey sigPublicKey) {
        this.encryptedSecretKey = encryptedSecretKey;
        this.encryptedPayloadWithHmac = encryptedPayloadWithHmac;
        this.signature = signature;
        this.sigPublicKey = sigPublicKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SealedAndSigned)) return false;

        SealedAndSigned that = (SealedAndSigned) o;

        if (!Arrays.equals(encryptedSecretKey, that.encryptedSecretKey)) return false;
        if (!Arrays.equals(encryptedPayloadWithHmac, that.encryptedPayloadWithHmac)) return false;
        if (!Arrays.equals(signature, that.signature)) return false;
        return !(sigPublicKey != null ? !sigPublicKey.equals(that.sigPublicKey) : that.sigPublicKey != null);

    }

    @Override
    public int hashCode() {
        int result = encryptedSecretKey != null ? Arrays.hashCode(encryptedSecretKey) : 0;
        result = 31 * result + (encryptedPayloadWithHmac != null ? Arrays.hashCode(encryptedPayloadWithHmac) : 0);
        result = 31 * result + (signature != null ? Arrays.hashCode(signature) : 0);
        result = 31 * result + (sigPublicKey != null ? sigPublicKey.hashCode() : 0);
        return result;
    }

}
