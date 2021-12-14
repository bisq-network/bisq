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

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import static java.util.Objects.requireNonNull;

@EqualsAndHashCode
@Getter
public class TxInfo implements Payload {

    // The client cannot see an instance of an org.bitcoinj.core.Transaction.  We use the
    // lighter weight TxInfo proto wrapper instead, containing just enough fields to
    // view some transaction details.  A block explorer or bitcoin-core client can be
    // used to see more detail.

    private final String txId;
    private final long inputSum;
    private final long outputSum;
    private final long fee;
    private final int size;
    private final boolean isPending;
    private final String memo;

    public TxInfo(Builder builder) {
        this.txId = builder.txId;
        this.inputSum = builder.inputSum;
        this.outputSum = builder.outputSum;
        this.fee = builder.fee;
        this.size = builder.size;
        this.isPending = builder.isPending;
        this.memo = builder.memo;
    }

    public static TxInfo toTxInfo(Transaction transaction) {
        if (transaction == null)
            throw new IllegalStateException("server created a null transaction");

        if (transaction.getFee() != null)
            return new Builder()
                    .withTxId(transaction.getTxId().toString())
                    .withInputSum(transaction.getInputSum().value)
                    .withOutputSum(transaction.getOutputSum().value)
                    .withFee(transaction.getFee().value)
                    .withSize(transaction.getMessageSize())
                    .withIsPending(transaction.isPending())
                    .withMemo(transaction.getMemo())
                    .build();
        else
            return new Builder()
                    .withTxId(transaction.getTxId().toString())
                    .withInputSum(transaction.getInputSum().value)
                    .withOutputSum(transaction.getOutputSum().value)
                    // Do not set fee == null.
                    .withSize(transaction.getMessageSize())
                    .withIsPending(transaction.isPending())
                    .withMemo(transaction.getMemo())
                    .build();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    //////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.TxInfo toProtoMessage() {
        return bisq.proto.grpc.TxInfo.newBuilder()
                .setTxId(txId)
                .setInputSum(inputSum)
                .setOutputSum(outputSum)
                .setFee(fee)
                .setSize(size)
                .setIsPending(isPending)
                .setMemo(memo == null ? "" : memo)
                .build();
    }

    @SuppressWarnings("unused")
    public static TxInfo fromProto(bisq.proto.grpc.TxInfo proto) {
        return new Builder()
                .withTxId(proto.getTxId())
                .withInputSum(proto.getInputSum())
                .withOutputSum(proto.getOutputSum())
                .withFee(proto.getFee())
                .withSize(proto.getSize())
                .withIsPending(proto.getIsPending())
                .withMemo(proto.getMemo())
                .build();
    }

    private static class Builder {
        private String txId;
        private long inputSum;
        private long outputSum;
        private long fee;
        private int size;
        private boolean isPending;
        private String memo;

        public Builder withTxId(String txId) {
            this.txId = txId;
            return this;
        }

        public Builder withInputSum(long inputSum) {
            this.inputSum = inputSum;
            return this;
        }

        public Builder withOutputSum(long outputSum) {
            this.outputSum = outputSum;
            return this;
        }

        public Builder withFee(long fee) {
            this.fee = fee;
            return this;
        }

        public Builder withSize(int size) {
            this.size = size;
            return this;
        }

        public Builder withIsPending(boolean isPending) {
            this.isPending = isPending;
            return this;
        }

        public Builder withMemo(String memo) {
            this.memo = memo;
            return this;
        }

        public TxInfo build() {
            return new TxInfo(this);
        }
    }

    @Override
    public String toString() {
        return "TxInfo{" + "\n" +
                "  txId='" + txId + '\'' + "\n" +
                ", inputSum=" + inputSum + "\n" +
                ", outputSum=" + outputSum + "\n" +
                ", fee=" + fee + "\n" +
                ", size=" + size + "\n" +
                ", isPending=" + isPending + "\n" +
                ", memo='" + memo + '\'' + "\n" +
                '}';
    }

    public static String getTransactionDetailString(Transaction tx) {
        if (tx == null)
            throw new IllegalArgumentException("Cannot print details for null transaction");

        StringBuilder builder = new StringBuilder("Transaction " + requireNonNull(tx).getTxId() + ":").append("\n");

        builder.append("\tisPending:                    ").append(tx.isPending()).append("\n");
        builder.append("\tfee:                          ").append(tx.getFee()).append("\n");
        builder.append("\tweight:                       ").append(tx.getWeight()).append("\n");
        builder.append("\tVsize:                        ").append(tx.getVsize()).append("\n");
        builder.append("\tinputSum:                     ").append(tx.getInputSum()).append("\n");
        builder.append("\toutputSum:                    ").append(tx.getOutputSum()).append("\n");

        Map<Sha256Hash, Integer> appearsInHashes = tx.getAppearsInHashes();
        if (appearsInHashes != null)
            builder.append("\tappearsInHashes: yes, count:  ").append(appearsInHashes.size()).append("\n");
        else
            builder.append("\tappearsInHashes:              ").append("no").append("\n");

        builder.append("\tanyOutputSpent:               ").append(tx.isAnyOutputSpent()).append("\n");
        builder.append("\tupdateTime:                   ").append(tx.getUpdateTime()).append("\n");
        builder.append("\tincludedInBestChainAt:        ").append(tx.getIncludedInBestChainAt()).append("\n");
        builder.append("\thasWitnesses:                 ").append(tx.hasWitnesses()).append("\n");
        builder.append("\tlockTime:                     ").append(tx.getLockTime()).append("\n");
        builder.append("\tversion:                      ").append(tx.getVersion()).append("\n");
        builder.append("\thasConfidence:                ").append(tx.hasConfidence()).append("\n");
        builder.append("\tsigOpCount:                   ").append(tx.getSigOpCount()).append("\n");
        builder.append("\tisTimeLocked:                 ").append(tx.isTimeLocked()).append("\n");
        builder.append("\thasRelativeLockTime:          ").append(tx.hasRelativeLockTime()).append("\n");
        builder.append("\tisOptInFullRBF:               ").append(tx.isOptInFullRBF()).append("\n");
        builder.append("\tpurpose:                      ").append(tx.getPurpose()).append("\n");
        builder.append("\texchangeRate:                 ").append(tx.getExchangeRate()).append("\n");
        builder.append("\tmemo:                         ").append(tx.getMemo()).append("\n");
        return builder.toString();
    }
}
