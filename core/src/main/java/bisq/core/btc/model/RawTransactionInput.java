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

import bisq.core.btc.wallet.BtcWalletService;

import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.script.Script;

import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.concurrent.Immutable;

@EqualsAndHashCode
@Immutable
@Getter
public final class RawTransactionInput implements NetworkPayload, PersistablePayload {
    public final long index;                // Index of spending txo
    public final byte[] parentTransaction;  // Spending tx (fromTx)
    public final long value;

    // Added at Bsq swap release
    // id of the org.bitcoinj.script.Script.ScriptType. Useful to know if input is segwit.
    // Lowest Script.ScriptType.id value is 1, so we use 0 as value for not defined
    public final int scriptTypeId;

    public RawTransactionInput(TransactionInput input) {
        this(input.getOutpoint().getIndex(),
                Objects.requireNonNull(Objects.requireNonNull(input.getConnectedOutput()).getParentTransaction()),
                Objects.requireNonNull(input.getValue()).value,
                input.getConnectedOutput() != null &&
                        input.getConnectedOutput().getScriptPubKey() != null &&
                        input.getConnectedOutput().getScriptPubKey().getScriptType() != null ?
                        input.getConnectedOutput().getScriptPubKey().getScriptType().id : -1);
    }

    // Does not set the scriptTypeId. Use RawTransactionInput(TransactionInput input) for any new code.
    @Deprecated
    public RawTransactionInput(long index, byte[] parentTransaction, long value) {
        this(index, parentTransaction, value, 0);
    }

    private RawTransactionInput(long index, Transaction parentTransaction, long value, int scriptTypeId) {
        this(index,
                parentTransaction.bitcoinSerialize(scriptTypeId == Script.ScriptType.P2WPKH.id ||
                        scriptTypeId == Script.ScriptType.P2WSH.id),
                value, scriptTypeId);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Holds the relevant data for the connected output for a tx input.
     * @param index  the index of the parentTransaction
     * @param parentTransaction  the spending output tx, not the parent tx of the input
     * @param value  the number of satoshis being spent
     * @param scriptTypeId The id of the org.bitcoinj.script.Script.ScriptType of the spending output
     *                     If not set it is 0.
     */
    private RawTransactionInput(long index,
                                byte[] parentTransaction,
                                long value,
                                int scriptTypeId) {
        this.index = index;
        this.parentTransaction = parentTransaction;
        this.value = value;
        this.scriptTypeId = scriptTypeId;
    }

    @Override
    public protobuf.RawTransactionInput toProtoMessage() {
        return protobuf.RawTransactionInput.newBuilder()
                .setIndex(index)
                .setParentTransaction(ByteString.copyFrom(parentTransaction))
                .setValue(value)
                .setScriptTypeId(scriptTypeId)
                .build();
    }

    public static RawTransactionInput fromProto(protobuf.RawTransactionInput proto) {
        return new RawTransactionInput(proto.getIndex(),
                proto.getParentTransaction().toByteArray(),
                proto.getValue(),
                proto.getScriptTypeId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isSegwit() {
        return isP2WPKH() || isP2WSH();
    }

    public boolean isP2WPKH() {
        return scriptTypeId == Script.ScriptType.P2WPKH.id;
    }

    public boolean isP2WSH() {
        return scriptTypeId == Script.ScriptType.P2WSH.id;
    }

    public String getParentTxId(BtcWalletService btcWalletService) {
        return btcWalletService.getTxFromSerializedTx(parentTransaction).getTxId().toString();
    }

    @Override
    public String toString() {
        return "RawTransactionInput{" +
                "index=" + index +
                ", parentTransaction as HEX " + Utilities.bytesAsHexString(parentTransaction) +
                ", value=" + value +
                ", scriptTypeId=" + scriptTypeId +
                '}';
    }
}
