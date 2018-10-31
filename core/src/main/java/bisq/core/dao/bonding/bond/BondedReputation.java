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

package bisq.core.dao.bonding.bond;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateService;
import bisq.core.locale.Res;

import bisq.common.crypto.Hash;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import java.math.BigInteger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;

@Getter
public final class BondedReputation implements PersistablePayload, NetworkPayload, BondWithHash {
    private final String salt;

    @Setter
    private String lockupTxId;

    @Nullable
    @Setter
    private String unlockTxId;

    public BondedReputation(@Nullable String salt) {
        this(salt,
                null,
                null
        );
    }

    public static BondedReputation createBondedReputation() {
        return new BondedReputation(UUID.randomUUID().toString());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BondedReputation(String salt,
                            @Nullable String lockupTxId,
                            @Nullable String unlockTxId) {
        this.salt = salt;
        this.lockupTxId = lockupTxId;
        this.unlockTxId = unlockTxId;
    }

    @Override
    public PB.BondedReputation toProtoMessage() {
        PB.BondedReputation.Builder builder = PB.BondedReputation.newBuilder()
                .setSalt(salt);
        Optional.ofNullable(lockupTxId).ifPresent(builder::setLockupTxId);
        Optional.ofNullable(unlockTxId).ifPresent(builder::setUnlockTxId);
        return builder.build();
    }

    public static BondedReputation fromProto(PB.BondedReputation proto) {
        return new BondedReputation(proto.getSalt(),
                proto.getLockupTxId().isEmpty() ? null : proto.getLockupTxId(),
                proto.getUnlockTxId().isEmpty() ? null : proto.getUnlockTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public String getUnlockTxId() {
        return unlockTxId;
    }

    @Override
    public byte[] getHash() {
        // We use the salt as input for the hash
        byte[] bytes = BigInteger.valueOf(hashCode()).toByteArray();
        byte[] hash = Hash.getSha256Ripemd160hash(bytes);
        return hash;
    }

    public String getDisplayString() {
        return Res.get("dao.bond.bondedReputation");
    }

    public boolean isLockedUp() {
        return lockupTxId != null;
    }

    public boolean isUnlocked() {
        return unlockTxId != null;
    }

    public boolean isUnlocking(DaoFacade daoFacade) {
        return daoFacade.isUnlocking(this);
    }

    public boolean isUnlocking(DaoStateService daoStateService) {
        return daoStateService.isUnlocking(this);
    }

    // We use only the immutable data
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BondedReputation that = (BondedReputation) o;
        return Objects.equals(salt, that.salt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(salt);
    }

    @Override
    public String toString() {
        return "BondedReputation{" +
                "\n     salt='" + salt + '\'' +
                ",\n     lockupTxId='" + lockupTxId + '\'' +
                ",\n     unlockTxId='" + unlockTxId + '\'' +
                "\n}";
    }
}
