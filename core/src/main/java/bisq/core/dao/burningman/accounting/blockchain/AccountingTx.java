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

package bisq.core.dao.burningman.accounting.blockchain;

import bisq.common.proto.network.NetworkPayload;
import bisq.common.util.Hex;

import com.google.protobuf.ByteString;

import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EqualsAndHashCode
@Getter
public final class AccountingTx implements NetworkPayload {
    public enum Type {
        BTC_TRADE_FEE_TX,
        DPT_TX
    }

    private final Type type;
    private final List<AccountingTxOutput> outputs;
    // We store only last 4 bytes to have a unique ID. Chance for collusion is very low, and we take that risk that
    // one object might get overridden in a hashset by the colluding truncatedTxId and all other data being the same as well.
    private final byte[] truncatedTxId;

    public AccountingTx(Type type, List<AccountingTxOutput> outputs, String txId) {
        this(type, outputs, Hex.decodeLast4Bytes(txId));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AccountingTx(Type type, List<AccountingTxOutput> outputs, byte[] truncatedTxId) {
        this.type = type;
        this.outputs = outputs;
        this.truncatedTxId = truncatedTxId;
    }

    @Override
    public protobuf.AccountingTx toProtoMessage() {
        return protobuf.AccountingTx.newBuilder()
                .setType(type.ordinal())
                .addAllOutputs(outputs.stream().map(AccountingTxOutput::toProtoMessage).collect(Collectors.toList()))
                .setTruncatedTxId(ByteString.copyFrom(truncatedTxId)).build();
    }

    public static AccountingTx fromProto(protobuf.AccountingTx proto) {
        List<AccountingTxOutput> outputs = proto.getOutputsList().stream()
                .map(AccountingTxOutput::fromProto)
                .collect(Collectors.toList());
        return new AccountingTx(Type.values()[proto.getType()],
                outputs,
                proto.getTruncatedTxId().toByteArray());
    }

    @Override
    public String toString() {
        return "AccountingTx{" +
                ",\n               type='" + type + '\'' +
                ",\n               outputs=" + outputs +
                ",\n               truncatedTxId=" + Hex.encode(truncatedTxId) +
                "\n          }";
    }
}
