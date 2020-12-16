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

import bisq.core.dao.governance.bond.BondedAsset;
import bisq.core.dao.state.model.ImmutableDaoStateModel;
import bisq.core.locale.Res;

import bisq.common.crypto.Hash;
import bisq.common.proto.ProtoUtil;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;

import java.util.Objects;
import java.util.UUID;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable data for a role. Is stored in the DaoState as part of the evaluated proposals.
 */
@Immutable
@Slf4j
@Value
public final class Role implements PersistablePayload, NetworkPayload, BondedAsset, ImmutableDaoStateModel {
    private final String uid;
    private final String name;
    private final String link;
    private final BondedRoleType bondedRoleType;

    // Only used as cache
    transient private final byte[] hash;

    /**
     * @param name                      Full name or nickname
     * @param link                      GitHub account or forum account of user
     * @param bondedRoleType            BondedRoleType
     */
    public Role(String name,
                String link,
                BondedRoleType bondedRoleType) {
        this(UUID.randomUUID().toString(),
                name,
                link,
                bondedRoleType
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Role(String uid,
                 String name,
                 String link,
                 BondedRoleType bondedRoleType) {
        this.uid = uid;
        this.name = name;
        this.link = link;
        this.bondedRoleType = bondedRoleType;

        hash = Hash.getSha256Ripemd160hash(toProtoMessage().toByteArray());
    }

    @Override
    public protobuf.Role toProtoMessage() {
        protobuf.Role.Builder builder = protobuf.Role.newBuilder()
                .setUid(uid)
                .setName(name)
                .setLink(link)
                .setBondedRoleType(bondedRoleType.name());
        return builder.build();
    }

    public static Role fromProto(protobuf.Role proto) {
        return new Role(proto.getUid(),
                proto.getName(),
                proto.getLink(),
                ProtoUtil.enumFromProto(BondedRoleType.class, proto.getBondedRoleType()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BondedAsset implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public byte[] getHash() {
        return hash;
    }

    @Override
    public String getDisplayString() {
        return Res.get("dao.bond.bondedRoleType." + bondedRoleType.name()) + ": " + name;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We use only the immutable data
    // bondedRoleType must not be used directly for hashCode or equals as it delivers the Object.hashCode (internal address)!
    // The equals and hashCode methods cannot be overwritten in Enums.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role that = (Role) o;
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
        return "Role{" +
                "\n     uid='" + uid + '\'' +
                ",\n     name='" + name + '\'' +
                ",\n     link='" + link + '\'' +
                ",\n     bondedRoleType=" + bondedRoleType +
                "\n}";
    }
}
