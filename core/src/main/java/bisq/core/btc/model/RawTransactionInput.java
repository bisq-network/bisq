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

package bisq.core.btc.model;

import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;

import javax.annotation.concurrent.Immutable;

@EqualsAndHashCode
@Immutable
public final class RawTransactionInput implements NetworkPayload, PersistablePayload {
    // Payload
    public final long index;
    public final byte[] parentTransaction;
    public final long value;

    public RawTransactionInput(long index, byte[] parentTransaction, long value) {
        this.index = index;
        this.parentTransaction = parentTransaction;
        this.value = value;
    }

    @Override
    public protobuf.RawTransactionInput toProtoMessage() {
        return protobuf.RawTransactionInput.newBuilder()
                .setIndex(index)
                .setParentTransaction(ByteString.copyFrom(parentTransaction))
                .setValue(value)
                .build();
    }

    public static RawTransactionInput fromProto(protobuf.RawTransactionInput proto) {
        return new RawTransactionInput(proto.getIndex(), proto.getParentTransaction().toByteArray(), proto.getValue());
    }

    @Override
    public String toString() {
        return "RawTransactionInput{" +
                "index=" + index +
                ", parentTransaction as HEX " + Utilities.bytesAsHexString(parentTransaction) +
                ", value=" + value +
                '}';
    }
}
