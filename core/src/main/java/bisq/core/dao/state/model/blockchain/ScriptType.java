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
import bisq.common.util.Hex;

import org.bitcoinj.script.ScriptOpCodes;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;



import bisq.wallets.bitcoind.rpc.responses.BitcoindScriptPubKey;

@Slf4j
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

    // A type of a newer Bitcoin Core version can occur in every block, so we log each unknown type only once
    private static final Set<String> REPORTED_UNKNOWN_TYPES = ConcurrentHashMap.newKeySet();

    @JsonCreator
    public static ScriptType fromScriptPubKey(BitcoindScriptPubKey scriptPubKey) {
        String name = scriptPubKey.getType();
        // Old Bitcoin Core nodes return "witness_unknown" for P2A output scripts and the DAO doesn't use any
        // P2A outputs.
        if ("anchor".equals(name)) {
            return WITNESS_UNKNOWN;
        }

        for (ScriptType scriptType : ScriptType.values()) {
            // UNDEFINED is no type of Bitcoin Core and has no protobuf value
            if (scriptType != UNDEFINED && scriptType.getName().equals(name)) {
                return scriptType;
            }
        }

        // Bitcoin Core adds types for new scripts, so an unknown type must not stop the parsing of the block. A
        // Bitcoin Core version which does not know the script reports a witness program of version 1 to 16 as
        // "witness_unknown" and any other script as "nonstandard". We use the same type, so that full nodes with
        // different Bitcoin Core versions agree on it. See docs/specifications/dao/bitcoind-script-types.md.
        ScriptType scriptType = isWitnessProgramAboveVersionZero(scriptPubKey.getHex()) ? WITNESS_UNKNOWN : NONSTANDARD;
        if (REPORTED_UNKNOWN_TYPES.add(String.valueOf(name))) {
            log.warn("Bitcoin Core reports the unknown script type {}. We use {} for it.", name, scriptType);
        }
        return scriptType;
    }

    // As CScript::IsWitnessProgram of Bitcoin Core: a version opcode followed by one push of 2 to 40 bytes which ends
    // the script.
    private static boolean isWitnessProgramAboveVersionZero(@Nullable String hex) {
        if (hex == null) {
            return false;
        }
        byte[] script;
        try {
            script = Hex.decode(hex);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (script.length < 4 || script.length > 42) {
            return false;
        }
        int versionOpcode = script[0] & 0xff;
        boolean isVersionAboveZero = versionOpcode >= ScriptOpCodes.OP_1 && versionOpcode <= ScriptOpCodes.OP_16;
        return isVersionAboveZero && (script[1] & 0xff) + 2 == script.length;
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
