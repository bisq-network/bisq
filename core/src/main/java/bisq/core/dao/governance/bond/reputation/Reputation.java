/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.governance.bond.reputation;

import bisq.core.dao.governance.bond.BondWithHash;

import bisq.common.crypto.Hash;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import lombok.Value;

import javax.annotation.concurrent.Immutable;

@Immutable
@Value
public final class Reputation implements PersistablePayload, NetworkPayload, BondWithHash {
    private final byte[] salt;

    public Reputation(byte[] salt) {
        this.salt = salt;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.Reputation toProtoMessage() {
        PB.Reputation.Builder builder = PB.Reputation.newBuilder()
                .setSalt(ByteString.copyFrom(salt));
        return builder.build();
    }

    public static Reputation fromProto(PB.Reputation proto) {
        return new Reputation(proto.getSalt().toByteArray());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public byte[] getHash() {
        return Hash.getSha256Ripemd160hash(salt);
    }

    @Override
    public String toString() {
        return "Reputation{" +
                "\n     salt=" + Utilities.bytesAsHexString(salt) +
                "\n}";
    }
}
