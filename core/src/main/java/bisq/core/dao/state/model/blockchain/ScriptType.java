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

import bisq.core.dao.state.model.ImmutableDaoStateModel;

import bisq.common.proto.ProtoUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.ToString;

import javax.annotation.concurrent.Immutable;

@ToString
@Immutable
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public enum ScriptType implements ImmutableDaoStateModel {
    UNDEFINED("undefined"),
    // https://github.com/bitcoin/bitcoin/blob/master/src/script/standard.cpp
    NONSTANDARD("nonstandard"),
    PUB_KEY("pubkey"),
    PUB_KEY_HASH("pubkeyhash"),
    SCRIPT_HASH("scripthash"),
    MULTISIG("multisig"),
    NULL_DATA("nulldata"),
    WITNESS_V0_KEYHASH("witness_v0_keyhash"),
    WITNESS_V0_SCRIPTHASH("witness_v0_scripthash"),
    WITNESS_V1_TAPROOT("witness_v1_taproot"),
    WITNESS_UNKNOWN("witness_unknown");

    private final String name;

    @JsonValue
    private String getName() {
        return name;
    }

    @JsonCreator
    public static ScriptType forName(String name) {
        if (name != null) {
            for (ScriptType scriptType : ScriptType.values()) {
                if (name.equals(scriptType.getName())) {
                    return scriptType;
                }
            }
        }
        throw new IllegalArgumentException("Expected the argument to be a valid 'bitcoind' script type, "
                + "but was invalid/unsupported instead. Received scriptType=" + name);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static ScriptType fromProto(protobuf.ScriptType scriptType) {
        return ProtoUtil.enumFromProto(ScriptType.class, scriptType.name());
    }

    public protobuf.ScriptType toProtoMessage() {
        return protobuf.ScriptType.valueOf(name());
    }
}
