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

import io.bisq.common.app.Version;
import io.bisq.common.persistance.Persistable;

import java.security.PublicKey;

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DecryptedMsgWithPubKey)) return false;

        DecryptedMsgWithPubKey that = (DecryptedMsgWithPubKey) o;

        if (msg != null ? !msg.equals(that.msg) : that.msg != null) return false;
        return !(signaturePubKey != null ? !signaturePubKey.equals(that.signaturePubKey) : that.signaturePubKey != null);

    }

    @Override
    public int hashCode() {
        int result = msg != null ? msg.hashCode() : 0;
        result = 31 * result + (signaturePubKey != null ? signaturePubKey.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DecryptedMsgWithPubKey{" +
                "message=" + msg +
                ", signaturePubKey.hashCode()=" + signaturePubKey.hashCode() +
                '}';
    }
}
