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

package bisq.core.dao.state.model.blockchain;

import bisq.core.dao.node.full.rpc.dto.DtoPubKeyScript;
import bisq.core.dao.state.model.ImmutableDaoStateModel;

import bisq.common.proto.persistable.PersistablePayload;

import com.google.common.collect.ImmutableList;

import java.util.Objects;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Value;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@Value
@AllArgsConstructor
public class PubKeyScript implements PersistablePayload, ImmutableDaoStateModel {
    private final int reqSigs;
    private final ScriptType scriptType;
    @Nullable
    private final ImmutableList<String> addresses;
    private final String asm;
    private final String hex;

    public PubKeyScript(DtoPubKeyScript scriptPubKey) {
        this(scriptPubKey.getReqSigs() != null ? scriptPubKey.getReqSigs() : 0,
                scriptPubKey.getType(),
                scriptPubKey.getAddresses() != null ? ImmutableList.copyOf(scriptPubKey.getAddresses()) : null,
                scriptPubKey.getAsm(),
                scriptPubKey.getHex());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public protobuf.PubKeyScript toProtoMessage() {
        final protobuf.PubKeyScript.Builder builder = protobuf.PubKeyScript.newBuilder()
                .setReqSigs(reqSigs)
                .setScriptType(scriptType.toProtoMessage())
                .setAsm(asm)
                .setHex(hex);
        Optional.ofNullable(addresses).ifPresent(builder::addAllAddresses);
        return builder.build();
    }

    public static PubKeyScript fromProto(protobuf.PubKeyScript proto) {
        return new PubKeyScript(proto.getReqSigs(),
                ScriptType.fromProto(proto.getScriptType()),
                proto.getAddressesList().isEmpty() ? null : ImmutableList.copyOf(proto.getAddressesList()),
                proto.getAsm(),
                proto.getHex());
    }

    // Enums must not be used directly for hashCode or equals as it delivers the Object.hashCode (internal address)!
    // The equals and hashCode methods cannot be overwritten in Enums.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PubKeyScript)) return false;
        if (!super.equals(o)) return false;
        PubKeyScript that = (PubKeyScript) o;
        return reqSigs == that.reqSigs &&
                scriptType.name().equals(that.scriptType.name()) &&
                Objects.equals(addresses, that.addresses) &&
                Objects.equals(asm, that.asm) &&
                Objects.equals(hex, that.hex);
    }

    @Override
    public int hashCode() {

        return Objects.hash(super.hashCode(), reqSigs, scriptType.name(), addresses, asm, hex);
    }
}
