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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScriptTypeTest {
    @Test
    public void getCodeMatchesProtobufOrder() {
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
}
