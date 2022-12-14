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

// Block data is aggressively optimized for minimal size.
// Block has 21 bytes base cost
// Tx has 2 byte base cost.
// TxOutput has variable byte size depending on name length, usually about 10-20 bytes.
// Some extra overhead of a few bytes is present depending on lists filled or not.
// Example fee tx (1 output) has about 40 bytes
// Example DPT tx with 2 outputs has about 60 bytes, typical DPT with 15-20 outputs might have 500 bytes.
// 2 year legacy BM had about 100k fee txs and 1000 DPTs. Would be about 4MB for fee txs and 500kB for DPT.
// As most blocks have at least 1 tx we might not have empty blocks.
// With above estimates we can expect about 2 MB growth per year.
@Slf4j
@EqualsAndHashCode

public final class AccountingBlock implements NetworkPayload {
    @Getter
    private final int height;
    private final int timeInSec;
    // We use only last 4 bytes of 32 byte hash to save space.
    // We use a byte array for flexibility if we would need to change the length of the hash later.
    @Getter
    private final byte[] truncatedHash;
    @Getter
    private final byte[] truncatedPreviousBlockHash;
    @Getter
    private final List<AccountingTx> txs;

    public AccountingBlock(int height,
                           int timeInSec,
                           byte[] truncatedHash,
                           byte[] truncatedPreviousBlockHash,
                           List<AccountingTx> txs) {
        this.height = height;
        this.timeInSec = timeInSec;
        this.truncatedHash = truncatedHash;
        this.truncatedPreviousBlockHash = truncatedPreviousBlockHash;
        this.txs = txs;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.AccountingBlock toProtoMessage() {
        return protobuf.AccountingBlock.newBuilder()
                .setHeight(height)
                .setTimeInSec(timeInSec)
                .setTruncatedHash(ByteString.copyFrom(truncatedHash))
                .setTruncatedPreviousBlockHash(ByteString.copyFrom(truncatedPreviousBlockHash))
                .addAllTxs(txs.stream().map(AccountingTx::toProtoMessage).collect(Collectors.toList()))
                .build();
    }

    public static AccountingBlock fromProto(protobuf.AccountingBlock proto) {
        List<AccountingTx> txs = proto.getTxsList().stream()
                .map(AccountingTx::fromProto)
                .collect(Collectors.toList());
        // log.error("AccountingBlock.getSerializedSize {}, txs.size={}", proto.getSerializedSize(), txs.size());
        return new AccountingBlock(proto.getHeight(),
                proto.getTimeInSec(),
                proto.getTruncatedHash().toByteArray(),
                proto.getTruncatedPreviousBlockHash().toByteArray(),
                txs);
    }

    public long getDate() {
        return timeInSec * 1000L;
    }

    @Override
    public String toString() {
        return "AccountingBlock{" +
                "\r\n     height=" + height +
                ",\r\n     timeInSec=" + timeInSec +
                ",\r\n     truncatedHash=" + Hex.encode(truncatedHash) +
                ",\r\n     truncatedPreviousBlockHash=" + Hex.encode(truncatedPreviousBlockHash) +
                ",\r\n     txs=" + txs +
                "\r\n}";
    }
}
