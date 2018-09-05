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

package bisq.core.dao.state;

import bisq.core.dao.state.blockchain.Block;
import org.junit.Test;

import org.junit.Assert;

public class BsqStateServiceTest {
    @Test
    public void testIsBlockHashKnown() {
        BsqStateService stateService = new BsqStateService(
                new BsqState(),
                new GenesisTxInfo("fakegenesistxid", 100));
        Assert.assertEquals(
                "Unknown block should not exist.",
                false,
                stateService.isBlockHashKnown("fakeblockhash0")
        );

        Block block = new Block(0, 1534800000, "fakeblockhash0", null);
        stateService.onNewBlockWithEmptyTxs(block);
        Assert.assertEquals(
                "Block that was added should exist.",
                true,
                stateService.isBlockHashKnown("fakeblockhash0")
        );

        Assert.assertEquals(
                "Block that was never added should still not exist.",
                false,
                stateService.isBlockHashKnown("fakeblockhash1")
        );

        block = new Block(1, 1534800001, "fakeblockhash1", null);
        stateService.onNewBlockWithEmptyTxs(block);
        block = new Block(2, 1534800002, "fakeblockhash2", null);
        stateService.onNewBlockWithEmptyTxs(block);
        block = new Block(3, 1534800003, "fakeblockhash3", null);
        stateService.onNewBlockWithEmptyTxs(block);
        Assert.assertEquals(
                "Block that was never added should still not exist after adding more blocks.",
                false,
                stateService.isBlockHashKnown("fakeblockhash4")
        );
        Assert.assertEquals(
                "Block that was added along with more blocks should exist.",
                true,
                stateService.isBlockHashKnown("fakeblockhash3")
        );
    }
}
