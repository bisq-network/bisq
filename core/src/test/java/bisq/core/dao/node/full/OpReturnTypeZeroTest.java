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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.node.full;

import bisq.core.dao.DaoHardFork;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.dao.state.model.blockchain.TxType;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A BSQ transaction whose OP_RETURN has the type byte 0x00 made the parser throw. The block stayed in the DAO state
 * with the transactions before it only, and the block listeners did not run. It is now an invalid transaction, like
 * one with any other unknown type. Uses the block harness of the lockup spend tests.
 */
public class OpReturnTypeZeroTest extends LockupOutputSpendTestBase {
    private final List<Integer> completedBlocks = new ArrayList<>();

    @BeforeEach
    public void setup() {
        startChainAt(DaoHardFork.getHardFork3ActivationHeight());
        daoStateService.addDaoStateListener(new DaoStateListener() {
            @Override
            public void onParseBlockComplete(Block block) {
                completedBlocks.add(block.getHeight());
            }
        });
        parseBlock(rawTx("split", List.of(new TxInput(GENESIS_TX_ID, 0, null)),
                List.of(output(0, 600_000, "split", null),
                        output(1, 100_000, "split", null),
                        output(2, 300_000, "split", null))));
    }

    @Test
    public void aTxWithOpReturnTypeZeroIsInvalidAndItsBlockIsComplete() {
        int height = nextHeight;
        parseBlock(rawTx("before", List.of(new TxInput("split", 0, null)),
                        List.of(output(0, 600_000, "before", null))),
                rawTx("typeZero", List.of(new TxInput("split", 2, null)),
                        List.of(output(0, 299_000, "typeZero", null),
                                output(1, 0, "typeZero", opReturnData((byte) 0x00, 22)))),
                rawTx("after", List.of(new TxInput("split", 1, null)),
                        List.of(output(0, 100_000, "after", null))));

        Tx typeZero = getTx("typeZero");
        assertEquals(TxType.INVALID, typeZero.getTxType());
        assertEquals(300_000, typeZero.getBurntBsq());
        assertEquals(TxOutputType.INVALID_OUTPUT, typeZero.getLastTxOutput().getTxOutputType());
        assertFalse(daoStateService.isUnspent(new TxOutputKey("typeZero", 0)));

        // The transactions before and after it in the same block are parsed, and the block is complete
        assertEquals(TxType.TRANSFER_BSQ, getTx("before").getTxType());
        assertEquals(TxType.TRANSFER_BSQ, getTx("after").getTxType());
        assertTrue(daoStateService.isUnspent(new TxOutputKey("after", 0)));
        assertTrue(completedBlocks.contains(height));
    }

    private static byte[] opReturnData(byte type, int length) {
        byte[] data = new byte[length];
        data[0] = type;
        data[1] = 0x01;
        return data;
    }
}
