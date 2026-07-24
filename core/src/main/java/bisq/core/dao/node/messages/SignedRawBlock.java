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

import bisq.core.dao.node.full.RawBlock;

import bisq.common.proto.network.NetworkPayload;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode
@Getter
public final class SignedRawBlock implements NetworkPayload {
    private final RawBlock block;
    private final DaoBlockSignature signature;

    public SignedRawBlock(RawBlock block, DaoBlockSignature signature) {
        this.block = checkNotNull(block, "block must not be null");
        this.signature = checkNotNull(signature, "signature must not be null");
    }

    @Override
    public protobuf.SignedRawBlock toProtoMessage() {
        return protobuf.SignedRawBlock.newBuilder()
                .setRawBlock(block.toProtoMessage())
                .setSignature(signature.toProtoMessage())
                .build();
    }

    public static SignedRawBlock fromProto(protobuf.SignedRawBlock proto) {
        return new SignedRawBlock(RawBlock.fromProto(proto.getRawBlock()),
                DaoBlockSignature.fromProto(proto.getSignature()));
    }

    @Override
    public String toString() {
        return "SignedRawBlock{" +
                "\n     block.height=" + block.getHeight() +
                ",\n     block.hash='" + block.getHash() + '\'' +
                ",\n     signature=" + signature +
                "\n}";
    }
}
