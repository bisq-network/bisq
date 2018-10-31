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

package bisq.core.dao.governance.role;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.bonding.bond.BondWithHash;
import bisq.core.dao.state.DaoStateService;
import bisq.core.locale.Res;

import bisq.common.crypto.Hash;
import bisq.common.proto.ProtoUtil;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import java.math.BigInteger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@Getter
public final class BondedRole implements PersistablePayload, NetworkPayload, BondWithHash {
    private final String uid;
    private final String name;
    private final String link;
    private final BondedRoleType bondedRoleType;

    @Setter
    private long startDate;

    // LockupTxId is null as long the bond holder has not been accepted by voting and made the lockup tx.
    // It will get set after the proposal has been accepted and the lockup tx is confirmed.
    @Nullable
    @Setter
    private String lockupTxId;

    // Date when role has been revoked
    @Setter
    private long revokeDate;
    @Nullable
    @Setter
    private String unlockTxId;

    /**
     * @param name                      Full name or nickname
     * @param link             Github account or forum account of user
     * @param bondedRoleType            BondedRoleType
     */
    public BondedRole(String name,
                      String link,
                      BondedRoleType bondedRoleType) {
        this(UUID.randomUUID().toString(),
                name,
                link,
                bondedRoleType,
                0,
                null,
                0,
                null
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BondedRole(String uid,
                      String name,
                      String link,
                      BondedRoleType bondedRoleType,
                      long startDate,
                      @Nullable String lockupTxId,
                      long revokeDate,
                      @Nullable String unlockTxId) {
        this.uid = uid;
        this.name = name;
        this.link = link;
        this.bondedRoleType = bondedRoleType;
        this.startDate = startDate;
        this.lockupTxId = lockupTxId;
        this.revokeDate = revokeDate;
        this.unlockTxId = unlockTxId;
    }

    @Override
    public PB.BondedRole toProtoMessage() {
        PB.BondedRole.Builder builder = PB.BondedRole.newBuilder()
                .setUid(uid)
                .setName(name)
                .setLink(link)
                .setBondedRoleType(bondedRoleType.name())
                .setStartDate(startDate)
                .setRevokeDate(revokeDate);
        Optional.ofNullable(lockupTxId).ifPresent(builder::setLockupTxId);
        Optional.ofNullable(unlockTxId).ifPresent(builder::setUnlockTxId);
        return builder.build();
    }

    public static BondedRole fromProto(PB.BondedRole proto) {
        return new BondedRole(proto.getUid(),
                proto.getName(),
                proto.getLink(),
                ProtoUtil.enumFromProto(BondedRoleType.class, proto.getBondedRoleType()),
                proto.getStartDate(),
                proto.getLockupTxId().isEmpty() ? null : proto.getLockupTxId(),
                proto.getRevokeDate(),
                proto.getUnlockTxId().isEmpty() ? null : proto.getUnlockTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BondWithHash implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getUnlockTxId() {
        return unlockTxId;
    }

    @Override
    public byte[] getHash() {
        // We use only the immutable data as input for hash
        byte[] bytes = BigInteger.valueOf(hashCode()).toByteArray();
        byte[] hash = Hash.getSha256Ripemd160hash(bytes);
       /* log.error("BondedRole.getHash: hash={}, bytes={}\nbondedRole={}", Utilities.bytesAsHexString(hash),
                Utilities.bytesAsHexString(bytes), toString());*/
        return hash;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getDisplayString() {
        return name + " / " + Res.get("dao.bond.bondedRoleType." + bondedRoleType.name());
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
    // bondedRoleType must not be used directly for hashCode or equals as it delivers he Object.hashCode (internal address)!
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BondedRole that = (BondedRole) o;
        return Objects.equals(uid, that.uid) &&
                Objects.equals(name, that.name) &&
                Objects.equals(link, that.link) &&
                bondedRoleType.name().equals(that.bondedRoleType.name());
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid, name, link, bondedRoleType.name());
    }

    @Override
    public String toString() {
        return "BondedRole{" +
                "\n     uid='" + uid + '\'' +
                ",\n     name='" + name + '\'' +
                ",\n     link='" + link + '\'' +
                ",\n     bondedRoleType=" + bondedRoleType +
                ",\n     startDate=" + startDate +
                ",\n     lockupTxId='" + lockupTxId + '\'' +
                ",\n     revokeDate=" + revokeDate +
                ",\n     unlockTxId='" + unlockTxId + '\'' +
                "\n}";
    }
}
