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
        assertEquals(0, ScriptType.UNDEFINED.getCode());
        assertEquals(1, ScriptType.PUB_KEY.getCode());
        assertEquals(2, ScriptType.PUB_KEY_HASH.getCode());
        assertEquals(3, ScriptType.SCRIPT_HASH.getCode());
        assertEquals(4, ScriptType.MULTISIG.getCode());
        assertEquals(5, ScriptType.NULL_DATA.getCode());
        assertEquals(6, ScriptType.WITNESS_V0_KEYHASH.getCode());
        assertEquals(7, ScriptType.WITNESS_V0_SCRIPTHASH.getCode());
        assertEquals(8, ScriptType.NONSTANDARD.getCode());
        assertEquals(9, ScriptType.WITNESS_UNKNOWN.getCode());
        assertEquals(10, ScriptType.WITNESS_V1_TAPROOT.getCode());
    }
}
