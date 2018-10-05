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

package bisq.core.dao.state.governance;

import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import javax.inject.Inject;

import java.util.Optional;

import lombok.Value;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Holds the issuance data (compensation request which was accepted in voting).
 */
@Immutable
@Value
public class Issuance implements PersistablePayload, NetworkPayload {
    private final String txId; // comp. request txId
    private final int chainHeight; // of issuance (first block of result phase)
    private final long amount;

    //TODO do we need to store that? its in the blockchain anyway
    @Nullable
    private final String pubKey; // sig key as hex of first input in issuance tx

    @Inject
    public Issuance(String txId, int chainHeight, long amount, @Nullable String pubKey) {
        this.txId = txId;
        this.chainHeight = chainHeight;
        this.amount = amount;
        this.pubKey = pubKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.Issuance toProtoMessage() {
        final PB.Issuance.Builder builder = PB.Issuance.newBuilder()
                .setTxId(txId)
                .setChainHeight(chainHeight)
                .setAmount(amount);

        Optional.ofNullable(pubKey).ifPresent(e -> builder.setPubKey(pubKey));

        return builder.build();
    }

    public static Issuance fromProto(PB.Issuance proto) {
        return new Issuance(proto.getTxId(),
                proto.getChainHeight(),
                proto.getAmount(),
                proto.getPubKey().isEmpty() ? null : proto.getPubKey());
    }

    @Override
    public String toString() {
        return "Issuance{" +
                "\n     txId='" + txId + '\'' +
                ",\n     chainHeight=" + chainHeight +
                ",\n     amount=" + amount +
                ",\n     pubKey='" + pubKey + '\'' +
                "\n}";
    }
}
