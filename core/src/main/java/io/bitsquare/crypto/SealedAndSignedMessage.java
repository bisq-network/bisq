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

package io.bitsquare.crypto;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;

import java.io.Serializable;

import java.security.PublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SealedObject;

/**
 * Packs the encrypted symmetric secretKey and the encrypted signed message into one object.
 * SecretKey is encrypted with asymmetric pubKey of peer. Signed message is encrypted with secretKey.
 * Using that hybrid encryption model we are not restricted by data size and performance as symmetric encryption is very fast.
 */
public class SealedAndSignedMessage implements Serializable, Message {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(SealedAndSignedMessage.class);

    private final SealedObject sealedSecretKey;
    private final SealedObject sealedMessage;
    private final PublicKey signaturePubKey;

    public SealedAndSignedMessage(SealedObject sealedSecretKey, SealedObject sealedMessage, PublicKey signaturePubKey) {
        this.sealedSecretKey = sealedSecretKey;
        this.sealedMessage = sealedMessage;
        this.signaturePubKey = signaturePubKey;
    }

    public SealedObject getSealedSecretKey() {
        return sealedSecretKey;
    }

    public SealedObject getSealedMessage() {
        return sealedMessage;
    }

    public PublicKey getSignaturePubKey() {
        return signaturePubKey;
    }
}
