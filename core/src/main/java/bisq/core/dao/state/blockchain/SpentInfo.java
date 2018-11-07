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

package bisq.core.dao.state.blockchain;

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import lombok.Value;

// Only used for json export
@Value
public final class SpentInfo implements PersistablePayload {
    private final long blockHeight;
    // Spending tx
    private final String txId;
    private final int inputIndex;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static SpentInfo fromProto(PB.SpentInfo proto) {
        return new SpentInfo(proto.getBlockHeight(),
                proto.getTxId(),
                proto.getInputIndex());
    }

    public PB.SpentInfo toProtoMessage() {
        return PB.SpentInfo.newBuilder()
                .setBlockHeight(blockHeight)
                .setTxId(txId)
                .setInputIndex(inputIndex)
                .build();
    }

    @Override
    public String toString() {
        return "SpentInfo{" +
                "\n     blockHeight=" + blockHeight +
                ",\n     txId='" + txId + '\'' +
                ",\n     inputIndex=" + inputIndex +
                "\n}";
    }
}
