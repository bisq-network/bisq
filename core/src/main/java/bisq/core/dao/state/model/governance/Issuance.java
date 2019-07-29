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

package bisq.core.dao.state.model.governance;

import bisq.core.dao.state.model.ImmutableDaoStateModel;

import bisq.common.proto.ProtoUtil;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;

import java.util.Objects;
import java.util.Optional;

import lombok.Value;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Holds the issuance data (compensation request which was accepted in voting).
 */
@Immutable
@Value
public class Issuance implements PersistablePayload, NetworkPayload, ImmutableDaoStateModel {
    private final String txId; // comp. request txId
    private final int chainHeight; // of issuance (first block of result phase)
    private final long amount;

    // sig key as hex of first input in issuance tx used for signing the merits
    // Can be null (payToPubKey tx) but in our case it will never be null. Still keep it nullable to be safe.
    @Nullable
    private final String pubKey;

    private final IssuanceType issuanceType;

    public Issuance(String txId, int chainHeight, long amount, @Nullable String pubKey, IssuanceType issuanceType) {
        this.txId = txId;
        this.chainHeight = chainHeight;
        this.amount = amount;
        this.pubKey = pubKey;
        this.issuanceType = issuanceType;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public protobuf.Issuance toProtoMessage() {
        final protobuf.Issuance.Builder builder = protobuf.Issuance.newBuilder()
                .setTxId(txId)
                .setChainHeight(chainHeight)
                .setAmount(amount)
                .setIssuanceType(issuanceType.name());
        Optional.ofNullable(pubKey).ifPresent(e -> builder.setPubKey(pubKey));
        return builder.build();
    }

    public static Issuance fromProto(protobuf.Issuance proto) {
        return new Issuance(proto.getTxId(),
                proto.getChainHeight(),
                proto.getAmount(),
                proto.getPubKey().isEmpty() ? null : proto.getPubKey(),
                proto.getIssuanceType().isEmpty() ? IssuanceType.UNDEFINED : ProtoUtil.enumFromProto(IssuanceType.class, proto.getIssuanceType()));
    }

    @Override
    public String toString() {
        return "Issuance{" +
                "\n     txId='" + txId + '\'' +
                ",\n     chainHeight=" + chainHeight +
                ",\n     amount=" + amount +
                ",\n     pubKey='" + pubKey + '\'' +
                ",\n     issuanceType='" + issuanceType + '\'' +
                "\n}";
    }

    // Enums must not be used directly for hashCode or equals as it delivers the Object.hashCode (internal address)!
    // The equals and hashCode methods cannot be overwritten in Enums.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Issuance)) return false;
        if (!super.equals(o)) return false;
        Issuance issuance = (Issuance) o;
        return chainHeight == issuance.chainHeight &&
                amount == issuance.amount &&
                Objects.equals(txId, issuance.txId) &&
                Objects.equals(pubKey, issuance.pubKey) &&
                issuanceType.name().equals(issuance.issuanceType.name());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), txId, chainHeight, amount, pubKey, issuanceType.name());
    }
}
