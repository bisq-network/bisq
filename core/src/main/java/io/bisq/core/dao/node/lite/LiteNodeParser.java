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

package io.bisq.core.dao.node.lite;

import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.node.BsqParser;
import io.bisq.core.dao.node.consensus.BsqBlockController;
import io.bisq.core.dao.node.consensus.BsqTxController;
import io.bisq.core.dao.node.consensus.GenesisTxController;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Parser for lite nodes. Iterates blocks to find BSQ relevant transactions.
 * <p>
 * We are in threaded context. Don't mix up with UserThread.
 */
@Slf4j
public class LiteNodeParser extends BsqParser {

    @Inject
    public LiteNodeParser(BsqBlockController bsqBlockController,
                          GenesisTxController genesisTxController,
                          BsqTxController bsqTxController) {
        super(bsqBlockController, genesisTxController, bsqTxController);
    }

    void parseBsqBlocks(List<BsqBlock> bsqBlocks,
                        Consumer<BsqBlock> newBlockHandler)
            throws BlockNotConnectingException {
        for (BsqBlock bsqBlock : bsqBlocks) {
            parseBsqBlock(bsqBlock);
            newBlockHandler.accept(bsqBlock);
        }
    }

    void parseBsqBlock(BsqBlock bsqBlock) throws BlockNotConnectingException {
        int blockHeight = bsqBlock.getHeight();
        log.info("Parse block at height={} ", blockHeight);
        List<Tx> txList = new ArrayList<>(bsqBlock.getTxs());
        List<Tx> bsqTxsInBlock = new ArrayList<>();
        bsqBlock.getTxs().forEach(tx -> checkForGenesisTx(blockHeight, bsqTxsInBlock, tx));
        recursiveFindBsqTxs(bsqTxsInBlock, txList, blockHeight, 0, 5300);
        bsqBlockController.addBlockIfValid(bsqBlock);
    }
}
