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

package io.bisq.network.p2p;

import com.google.protobuf.Message;
import io.bisq.common.app.Version;
import io.bisq.common.network.Msg;
import io.bisq.common.persistence.Persistable;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;

import java.security.PublicKey;

@EqualsAndHashCode
public final class DecryptedMsgWithPubKey implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    public final Msg msg;
    public final PublicKey signaturePubKey;

    public DecryptedMsgWithPubKey(Msg msg, PublicKey signaturePubKey) {
        this.msg = msg;
        this.signaturePubKey = signaturePubKey;
    }


    @Override
    public Message toProto() {
        // TODO
        return PB.DecryptedMsgWithPubKey.newBuilder()
                // TODO .setMessage(msg.toEnvelopeProto())
               /* .setSignaturePubKey(signaturePubKey.toProto())*/
                .build();
    }

    @Override
    public String toString() {
        return "DecryptedMsgWithPubKey{" +
                "message=" + msg +
                ", signaturePubKey.hashCode()=" + signaturePubKey.hashCode() +
                '}';
    }
}
