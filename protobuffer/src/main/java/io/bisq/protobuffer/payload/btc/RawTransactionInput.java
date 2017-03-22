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

package io.bisq.protobuffer.payload.btc;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.payload.Payload;

import java.util.Arrays;

public final class RawTransactionInput implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RawTransactionInput)) return false;

        RawTransactionInput rawTransactionInput = (RawTransactionInput) o;

        if (index != rawTransactionInput.index) return false;
        if (value != rawTransactionInput.value) return false;
        return Arrays.equals(parentTransaction, rawTransactionInput.parentTransaction);

    }

    @Override
    public int hashCode() {
        int result = (int) (index ^ (index >>> 32));
        result = 31 * result + (parentTransaction != null ? Arrays.hashCode(parentTransaction) : 0);
        result = 31 * result + (int) (value ^ (value >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "RawTransactionInput{" +
                "index=" + index +
                ", parentTransaction=" + Arrays.toString(parentTransaction) +
                ", value=" + value +
                '}';
    }

    @Override
    public PB.RawTransactionInput toProto() {
        return PB.RawTransactionInput.newBuilder()
                .setIndex(index)
                .setParentTransaction(ByteString.copyFrom(parentTransaction))
                .setValue(value).build();
    }
}
