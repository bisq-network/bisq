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
import bisq.common.encoding.canonical.CanonicalEnum;

import bisq.common.proto.ProtoUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.ToString;

import javax.annotation.concurrent.Immutable;



import bisq.wallets.bitcoind.rpc.responses.BitcoindScriptPubKey;

@ToString
@Immutable
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public enum ScriptType implements ImmutableDaoStateModel, CanonicalEnum {
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
    public static ScriptType fromScriptPubKey(BitcoindScriptPubKey scriptPubKey) {
        String name = scriptPubKey.getType();
        // Old Bitcoin Core nodes return "witness_unknown" for P2A output scripts and the DAO doesn't use any
        // P2A outputs.
        if (name.equals("anchor")) {
            return WITNESS_UNKNOWN;
        }

        for (ScriptType scriptType : ScriptType.values()) {
            if (name.equals(scriptType.getName())) {
                return scriptType;
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CanonicalEnum
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public int getCode() {
        switch (this) {
            case UNDEFINED:
                return 0;
            case PUB_KEY:
                return 1;
            case PUB_KEY_HASH:
                return 2;
            case SCRIPT_HASH:
                return 3;
            case MULTISIG:
                return 4;
            case NULL_DATA:
                return 5;
            case WITNESS_V0_KEYHASH:
                return 6;
            case WITNESS_V0_SCRIPTHASH:
                return 7;
            case NONSTANDARD:
                return 8;
            case WITNESS_UNKNOWN:
                return 9;
            case WITNESS_V1_TAPROOT:
                return 10;
            default:
                throw new IllegalStateException("Unhandled script type " + this);
        }
    }
}
