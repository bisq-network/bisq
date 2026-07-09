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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.node.messages;

import bisq.network.p2p.NodeAddress;

import bisq.common.proto.network.NetworkPayload;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode
@Getter
public final class DaoBlockSignature implements NetworkPayload {
    private final NodeAddress signerNodeAddress;
    private final byte[] signature;

    public DaoBlockSignature(NodeAddress signerNodeAddress, byte[] signature) {
        this.signerNodeAddress = checkNotNull(signerNodeAddress, "signerNodeAddress must not be null");
        this.signature = checkNotNull(signature, "signature must not be null").clone();
        checkArgument(signature.length > 0, "signature must not be empty");
    }

    public byte[] getSignature() {
        return signature.clone();
    }

    @Override
    public protobuf.DaoBlockSignature toProtoMessage() {
        return protobuf.DaoBlockSignature.newBuilder()
                .setSignerNodeAddress(signerNodeAddress.toProtoMessage())
                .setSignature(ByteString.copyFrom(signature))
                .build();
    }

    public static DaoBlockSignature fromProto(protobuf.DaoBlockSignature proto) {
        return new DaoBlockSignature(NodeAddress.fromProto(proto.getSignerNodeAddress()),
                proto.getSignature().toByteArray());
    }

    @Override
    public String toString() {
        return "DaoBlockSignature{" +
                "signerNodeAddress=" + signerNodeAddress +
                ", signature.length=" + signature.length +
                '}';
    }
}
