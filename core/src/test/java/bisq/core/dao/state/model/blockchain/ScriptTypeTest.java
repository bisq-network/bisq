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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;



import bisq.wallets.bitcoind.rpc.responses.BitcoindScriptPubKey;

public class ScriptTypeTest {
    // OP_RETURN, so that a type taken from the script would not match the reported type
    private static final String NULL_DATA_SCRIPT = "6a";

    @Test
    public void getCodeMatchesProtobufOrder() {
        assertEquals(11, ScriptType.values().length);
        assertEquals(protobuf.ScriptType.PB_ERROR_SCRIPT_TYPES.getNumber(), ScriptType.UNDEFINED.getCode());
        assertEquals(protobuf.ScriptType.PUB_KEY.getNumber(), ScriptType.PUB_KEY.getCode());
        assertEquals(protobuf.ScriptType.PUB_KEY_HASH.getNumber(), ScriptType.PUB_KEY_HASH.getCode());
        assertEquals(protobuf.ScriptType.SCRIPT_HASH.getNumber(), ScriptType.SCRIPT_HASH.getCode());
        assertEquals(protobuf.ScriptType.MULTISIG.getNumber(), ScriptType.MULTISIG.getCode());
        assertEquals(protobuf.ScriptType.NULL_DATA.getNumber(), ScriptType.NULL_DATA.getCode());
        assertEquals(protobuf.ScriptType.WITNESS_V0_KEYHASH.getNumber(), ScriptType.WITNESS_V0_KEYHASH.getCode());
        assertEquals(protobuf.ScriptType.WITNESS_V0_SCRIPTHASH.getNumber(), ScriptType.WITNESS_V0_SCRIPTHASH.getCode());
        assertEquals(protobuf.ScriptType.NONSTANDARD.getNumber(), ScriptType.NONSTANDARD.getCode());
        assertEquals(protobuf.ScriptType.WITNESS_UNKNOWN.getNumber(), ScriptType.WITNESS_UNKNOWN.getCode());
        assertEquals(protobuf.ScriptType.WITNESS_V1_TAPROOT.getNumber(), ScriptType.WITNESS_V1_TAPROOT.getCode());
    }

    @ParameterizedTest
    @CsvSource({
            "nonstandard, NONSTANDARD",
            "pubkey, PUB_KEY",
            "pubkeyhash, PUB_KEY_HASH",
            "scripthash, SCRIPT_HASH",
            "multisig, MULTISIG",
            "nulldata, NULL_DATA",
            "witness_v0_keyhash, WITNESS_V0_KEYHASH",
            "witness_v0_scripthash, WITNESS_V0_SCRIPTHASH",
            "witness_v1_taproot, WITNESS_V1_TAPROOT",
            "witness_unknown, WITNESS_UNKNOWN",
            "anchor, WITNESS_UNKNOWN"
    })
    public void knownTypeIsTakenAsReported(String type, ScriptType expected) {
        assertEquals(expected, ScriptType.fromScriptPubKey(scriptPubKey(type, NULL_DATA_SCRIPT)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // OP_1, push of 32 bytes
            "51202222222222222222222222222222222222222222222222222222222222222222",
            // OP_1, push of 2 bytes: the P2A script, which Bitcoin Core 28 reports as "anchor"
            "51024e73",
            // OP_2, push of 40 bytes
            "522833333333333333333333333333333333333333333333333333333333333333333333333333333333",
            // OP_16, push of 2 bytes
            "6002abcd",
            // upper case hex
            "5120ABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABAB"
    })
    public void unknownTypeOfWitnessProgramAboveVersionZeroIsWitnessUnknown(String hex) {
        assertEquals(ScriptType.WITNESS_UNKNOWN, ScriptType.fromScriptPubKey(scriptPubKey("witness_v2_future", hex)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // OP_0, push of 20 bytes: version 0 is not a witness program of an unknown type
            "00144444444444444444444444444444444444444444",
            // OP_1, push of 41 bytes: too long for a witness program
            "51295555555555555555555555555555555555555555555555555555555555555555555555555555555555",
            // OP_1, push of 1 byte: too short for a witness program
            "510166",
            // OP_1, push of 32 bytes, but only 31 bytes follow
            "512077777777777777777777777777777777777777777777777777777777777777",
            // P2PKH
            "76a914888888888888888888888888888888888888888888ac",
            "51",
            "",
            "invalid hex",
            "512"
    })
    public void unknownTypeOfOtherScriptIsNonstandard(String hex) {
        assertEquals(ScriptType.NONSTANDARD, ScriptType.fromScriptPubKey(scriptPubKey("future_type", hex)));
    }

    @Test
    public void missingTypeIsHandledLikeUnknownType() {
        assertEquals(ScriptType.WITNESS_UNKNOWN, ScriptType.fromScriptPubKey(scriptPubKey(null, "51024e73")));
        assertEquals(ScriptType.NONSTANDARD, ScriptType.fromScriptPubKey(scriptPubKey(null, NULL_DATA_SCRIPT)));
        assertEquals(ScriptType.NONSTANDARD, ScriptType.fromScriptPubKey(scriptPubKey("future_type", null)));
    }

    // UNDEFINED has no protobuf value, so the DAO state could not be persisted with it
    @Test
    public void undefinedIsNotABitcoinCoreType() {
        ScriptType scriptType = ScriptType.fromScriptPubKey(scriptPubKey("undefined", NULL_DATA_SCRIPT));

        assertEquals(ScriptType.NONSTANDARD, scriptType);
        assertEquals(protobuf.ScriptType.NONSTANDARD, scriptType.toProtoMessage());
    }

    private static BitcoindScriptPubKey scriptPubKey(String type, String hex) {
        BitcoindScriptPubKey scriptPubKey = mock(BitcoindScriptPubKey.class);
        when(scriptPubKey.getType()).thenReturn(type);
        when(scriptPubKey.getHex()).thenReturn(hex);
        return scriptPubKey;
    }
}
