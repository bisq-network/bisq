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

package bisq.core.dao.state.model.governance;

import bisq.core.dao.governance.ConsensusCritical;
import bisq.core.dao.state.model.ImmutableDaoStateModel;

import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.concurrent.Immutable;

@Immutable
@EqualsAndHashCode
public class Merit implements PersistablePayload, NetworkPayload, ConsensusCritical, ImmutableDaoStateModel {
    @Getter
    private final Issuance issuance;
    @Getter
    private final byte[] signature;

    public Merit(Issuance issuance, byte[] signature) {
        this.issuance = issuance;
        this.signature = signature;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.Merit toProtoMessage() {
        final protobuf.Merit.Builder builder = protobuf.Merit.newBuilder()
                .setIssuance(issuance.toProtoMessage())
                .setSignature(ByteString.copyFrom(signature));
        return builder.build();
    }

    public static Merit fromProto(protobuf.Merit proto) {
        return new Merit(Issuance.fromProto(proto.getIssuance()),
                proto.getSignature().toByteArray());
    }

    public String getIssuanceTxId() {
        return issuance.getTxId();
    }

    @Override
    public String toString() {
        return "Merit{" +
                "\n     issuance=" + issuance +
                ",\n     signature=" + Utilities.bytesAsHexString(signature) +
                "\n}";
    }
}
