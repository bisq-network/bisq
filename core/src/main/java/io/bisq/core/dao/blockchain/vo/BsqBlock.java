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

package io.bisq.core.dao.blockchain.vo;

import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class BsqBlock implements PersistablePayload {
    private final int height;
    private final String hash;
    private final String previousBlockHash;
    private final List<Tx> txs;

    public BsqBlock(int height, String hash, String previousBlockHash, List<Tx> txs) {
        this.height = height;
        this.hash = hash;
        this.previousBlockHash = previousBlockHash;
        this.txs = txs;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.BsqBlock toProtoMessage() {
        return PB.BsqBlock.newBuilder()
                .setHeight(height)
                .setHash(hash)
                .setPreviousBlockHash(previousBlockHash)
                .addAllTxs(txs.stream()
                        .map(Tx::toProtoMessage)
                        .collect(Collectors.toList()))
                .build();
    }

    public static BsqBlock fromProto(PB.BsqBlock proto) {
        return new BsqBlock(proto.getHeight(),
                proto.getHash(),
                proto.getPreviousBlockHash(),
                proto.getTxsList().isEmpty() ?
                        new ArrayList<>() :
                        proto.getTxsList().stream()
                                .map(Tx::fromProto)
                                .collect(Collectors.toList()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void reset() {
        txs.stream().forEach(Tx::reset);
    }

    @Override
    public String toString() {
        return "BsqBlock{" +
                "\n     height=" + getHeight() +
                ",\n     hash='" + getHash() + '\'' +
                ",\n     previousBlockHash='" + getPreviousBlockHash() + '\'' +
                ",\n     txs='" + txs + '\'' +
                "\n}";
    }
}
