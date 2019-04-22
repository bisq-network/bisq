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

package bisq.core.dao.state.unconfirmed;

import bisq.core.dao.state.model.ImmutableDaoStateModel;
import bisq.core.dao.state.model.blockchain.TxOutputKey;

import bisq.common.proto.persistable.PersistablePayload;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * Used for tracking unconfirmed change outputs to allow them to be spent in follow up
 * transactions in txType permits it. We can assume that the user is not intending to
 * double spend own transactions as well that he does not try to spend an invalid BSQ
 * output to a BSQ address.
 * We do not allow spending unconfirmed BSQ outputs received from elsewhere.
 */
@Slf4j
@Immutable
@Data
public final class UnconfirmedTxOutput implements PersistablePayload, ImmutableDaoStateModel {

    public static UnconfirmedTxOutput fromTransactionOutput(TransactionOutput transactionOutput) {
        Transaction parentTransaction = transactionOutput.getParentTransaction();
        if (parentTransaction != null) {
            return new UnconfirmedTxOutput(transactionOutput.getIndex(),
                    transactionOutput.getValue().value,
                    parentTransaction.getTxId().toString());
        } else {
            log.warn("parentTransaction of transactionOutput is null. " +
                            "This must not happen. " +
                            "We could throw an exception as well " +
                            "here but we prefer to be for now more tolerant and just " +
                            "assign the value 0 if that would be the case. transactionOutput={}",
                    transactionOutput);
            return new UnconfirmedTxOutput(transactionOutput.getIndex(),
                    0,
                    "null");
        }
    }

    protected final int index;
    protected final long value;
    protected final String txId;

    private UnconfirmedTxOutput(int index,
                                long value,
                                String txId) {
        this.index = index;
        this.value = value;
        this.txId = txId;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public protobuf.UnconfirmedTxOutput toProtoMessage() {
        return protobuf.UnconfirmedTxOutput.newBuilder()
                .setIndex(index)
                .setValue(value)
                .setTxId(txId).build();
    }

    public static UnconfirmedTxOutput fromProto(protobuf.UnconfirmedTxOutput proto) {
        return new UnconfirmedTxOutput(proto.getIndex(),
                proto.getValue(),
                proto.getTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxOutputKey getKey() {
        return new TxOutputKey(txId, index);
    }

    @Override
    public String toString() {
        return "UnconfirmedTxOutput{" +
                "\n     index=" + index +
                ",\n     value=" + value +
                ",\n     txId='" + txId + '\'' +
                "\n}";
    }
}
