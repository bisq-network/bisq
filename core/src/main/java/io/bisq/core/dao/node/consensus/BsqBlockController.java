/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.node.consensus;

import io.bisq.core.dao.blockchain.ReadableBsqBlockChain;
import io.bisq.core.dao.blockchain.WritableBsqBlockChain;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.LinkedList;

@Slf4j
public class BsqBlockController {

    private final WritableBsqBlockChain writableBsqBlockChain;
    private final ReadableBsqBlockChain readableBsqBlockChain;

    @Inject
    public BsqBlockController(WritableBsqBlockChain writableBsqBlockChain, ReadableBsqBlockChain readableBsqBlockChain) {
        this.writableBsqBlockChain = writableBsqBlockChain;
        this.readableBsqBlockChain = readableBsqBlockChain;
    }

    public void addBlockIfValid(BsqBlock bsqBlock) throws BlockNotConnectingException {
        LinkedList<BsqBlock> bsqBlocks = readableBsqBlockChain.getBsqBlocks();
        if (!bsqBlocks.contains(bsqBlock)) {
            if (isBlockConnecting(bsqBlock, bsqBlocks)) {
                writableBsqBlockChain.addBlock(bsqBlock);
            } else {
                log.warn("addBlock called with a not connecting block:\n" +
                                "height()={}, hash()={}, head.height()={}, head.hash()={}",
                        bsqBlock.getHeight(), bsqBlock.getHash(), bsqBlocks.getLast().getHeight(), bsqBlocks.getLast().getHash());
                throw new BlockNotConnectingException(bsqBlock);
            }
        } else {
            log.warn("We got that block already. Ignore the call.");
        }
    }

    private boolean isBlockConnecting(BsqBlock bsqBlock, LinkedList<BsqBlock> bsqBlocks) {
        // Case 1: bsqBlocks is empty
        // Case 2: bsqBlocks not empty. Last block must match new blocks getPreviousBlockHash and
        // height of last block +1 must be new blocks height
        return bsqBlocks.isEmpty() ||
                (bsqBlocks.getLast().getHash().equals(bsqBlock.getPreviousBlockHash()) &&
                        bsqBlocks.getLast().getHeight() + 1 == bsqBlock.getHeight());
    }
}
