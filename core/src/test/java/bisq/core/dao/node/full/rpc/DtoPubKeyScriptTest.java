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

package bisq.core.dao.node.full.rpc;

import bisq.core.dao.state.model.blockchain.PubKeyScript;
import bisq.core.dao.state.model.blockchain.ScriptType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;



import bisq.wallets.bitcoind.rpc.responses.BitcoindScriptPubKey;

class DtoPubKeyScriptTest {
    // RpcService converts every output of every transaction of a block. An output with a script type of a newer
    // Bitcoin Core version stopped the parsing of the block.
    @Test
    void outputWithUnknownScriptTypeIsConverted() {
        BitcoindScriptPubKey bitcoindScriptPubKey = mock(BitcoindScriptPubKey.class);
        when(bitcoindScriptPubKey.getType()).thenReturn("witness_v2_future");
        // OP_2, push of 32 bytes
        when(bitcoindScriptPubKey.getHex()).thenReturn("52202222222222222222222222222222222222222222222222222222222222222222");
        when(bitcoindScriptPubKey.getAsm()).thenReturn("2 2222222222222222222222222222222222222222222222222222222222222222");

        DtoPubKeyScript dtoPubKeyScript = new DtoPubKeyScript(bitcoindScriptPubKey);

        assertEquals(ScriptType.WITNESS_UNKNOWN, dtoPubKeyScript.getType());
        assertEquals(protobuf.ScriptType.WITNESS_UNKNOWN,
                new PubKeyScript(dtoPubKeyScript).toProtoMessage().getScriptType());
    }
}
