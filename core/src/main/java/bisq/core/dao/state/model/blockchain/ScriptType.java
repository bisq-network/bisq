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
import bisq.core.encoding.canonical.CanonicalEnum;

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

    private static final int UNDEFINED_CODE = protobuf.ScriptType.PB_ERROR_SCRIPT_TYPES.getNumber();
    private static final int PUB_KEY_CODE = protobuf.ScriptType.PUB_KEY.getNumber();
    private static final int PUB_KEY_HASH_CODE = protobuf.ScriptType.PUB_KEY_HASH.getNumber();
    private static final int SCRIPT_HASH_CODE = protobuf.ScriptType.SCRIPT_HASH.getNumber();
    private static final int MULTISIG_CODE = protobuf.ScriptType.MULTISIG.getNumber();
    private static final int NULL_DATA_CODE = protobuf.ScriptType.NULL_DATA.getNumber();
    private static final int WITNESS_V0_KEYHASH_CODE = protobuf.ScriptType.WITNESS_V0_KEYHASH.getNumber();
    private static final int WITNESS_V0_SCRIPTHASH_CODE = protobuf.ScriptType.WITNESS_V0_SCRIPTHASH.getNumber();
    private static final int NONSTANDARD_CODE = protobuf.ScriptType.NONSTANDARD.getNumber();
    private static final int WITNESS_UNKNOWN_CODE = protobuf.ScriptType.WITNESS_UNKNOWN.getNumber();
    private static final int WITNESS_V1_TAPROOT_CODE = protobuf.ScriptType.WITNESS_V1_TAPROOT.getNumber();

    @Override
    public int getCode() {
        switch (this) {
            case UNDEFINED:
                return UNDEFINED_CODE;
            case PUB_KEY:
                return PUB_KEY_CODE;
            case PUB_KEY_HASH:
                return PUB_KEY_HASH_CODE;
            case SCRIPT_HASH:
                return SCRIPT_HASH_CODE;
            case MULTISIG:
                return MULTISIG_CODE;
            case NULL_DATA:
                return NULL_DATA_CODE;
            case WITNESS_V0_KEYHASH:
                return WITNESS_V0_KEYHASH_CODE;
            case WITNESS_V0_SCRIPTHASH:
                return WITNESS_V0_SCRIPTHASH_CODE;
            case NONSTANDARD:
                return NONSTANDARD_CODE;
            case WITNESS_UNKNOWN:
                return WITNESS_UNKNOWN_CODE;
            case WITNESS_V1_TAPROOT:
                return WITNESS_V1_TAPROOT_CODE;
            default:
                throw new IllegalStateException("Unhandled script type " + this);
        }
    }
}
