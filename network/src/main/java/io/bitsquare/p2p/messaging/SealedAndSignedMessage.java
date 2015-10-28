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

package io.bitsquare.p2p.messaging;

import io.bitsquare.app.Version;
import io.bitsquare.common.util.Utilities;

import javax.crypto.SealedObject;
import java.security.PublicKey;
import java.util.Arrays;

/**
 * Packs the encrypted symmetric secretKey and the encrypted and signed message into one object.
 * SecretKey is encrypted with asymmetric pubKey of peer. Signed message is encrypted with secretKey.
 * Using that hybrid encryption model we are not restricted by data size and performance as symmetric encryption is very fast.
 */
public final class SealedAndSignedMessage implements MailMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final SealedObject sealedSecretKey;
    public final SealedObject sealedMessage;
    public final PublicKey signaturePubKey;

    public SealedAndSignedMessage(SealedObject sealedSecretKey, SealedObject sealedMessage, PublicKey signaturePubKey) {
        this.sealedSecretKey = sealedSecretKey;
        this.sealedMessage = sealedMessage;
        this.signaturePubKey = signaturePubKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SealedAndSignedMessage)) return false;

        SealedAndSignedMessage that = (SealedAndSignedMessage) o;

        return Arrays.equals(Utilities.objectToByteArray(this), Utilities.objectToByteArray(that));
    }

    @Override
    public int hashCode() {
        byte[] bytes = Utilities.objectToByteArray(this);
        return bytes != null ? Arrays.hashCode(bytes) : 0;
    }

    @Override
    public String toString() {
        return "SealedAndSignedMessage{" +
                "hashCode=" + hashCode() +
                '}';
    }
}
