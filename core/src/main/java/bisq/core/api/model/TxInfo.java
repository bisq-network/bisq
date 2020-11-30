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

package bisq.core.api.model;

import bisq.common.Payload;

import org.bitcoinj.core.Transaction;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@Getter
public class TxInfo implements Payload {

    private final String id;
    private final long outputSum;
    private final long fee;
    private final int size;

    public TxInfo(String id, long outputSum, long fee, int size) {
        this.id = id;
        this.outputSum = outputSum;
        this.fee = fee;
        this.size = size;
    }

    public static TxInfo toTxInfo(Transaction transaction) {
        if (transaction == null)
            throw new IllegalStateException("server created a null transaction");

        return new TxInfo(transaction.getTxId().toString(),
                transaction.getOutputSum().value,
                transaction.getFee().value,
                transaction.getMessageSize());
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    //////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.TxInfo toProtoMessage() {
        return bisq.proto.grpc.TxInfo.newBuilder()
                .setId(id)
                .setOutputSum(outputSum)
                .setFee(fee)
                .setSize(size)
                .build();
    }

    @SuppressWarnings("unused")
    public static TxInfo fromProto(bisq.proto.grpc.TxInfo proto) {
        return new TxInfo(proto.getId(),
                proto.getOutputSum(),
                proto.getFee(),
                proto.getSize());
    }

    @Override
    public String toString() {
        return "TxInfo{" + "\n" +
                "  id='" + id + '\'' + "\n" +
                ", outputSum=" + outputSum + " sats" + "\n" +
                ", fee=" + fee + " sats" + "\n" +
                ", size=" + size + " bytes" + "\n" +
                '}';
    }
}
