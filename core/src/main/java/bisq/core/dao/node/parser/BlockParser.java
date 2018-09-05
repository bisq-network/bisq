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

package bisq.core.dao.node.parser;

import bisq.core.dao.node.parser.exceptions.BlockNotConnectingException;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.RawBlock;
import bisq.core.dao.state.blockchain.Tx;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.util.LinkedList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * Parse a rawBlock and creates a block from it with an empty tx list.
 * Iterates all rawTx and if the tx is a a BSQ tx it gets added to the tx list.
 */
@Slf4j
@Immutable
public class BlockParser {
    private final TxParser txParser;
    private final BsqStateService bsqStateService;
    private final String genesisTxId;
    private final int genesisBlockHeight;
    private final Coin genesisTotalSupply;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BlockParser(TxParser txParser,
                       BsqStateService bsqStateService) {
        this.txParser = txParser;
        this.bsqStateService = bsqStateService;
        this.genesisTxId = bsqStateService.getGenesisTxId();
        this.genesisBlockHeight = bsqStateService.getGenesisBlockHeight();
        this.genesisTotalSupply = bsqStateService.getGenesisTotalSupply();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     *
     * @param rawBlock  Contains all transactions of a bitcoin block without any BSQ specific data
     * @return Block: Gets created from the rawBlock but contains only BSQ specific transactions.
     * @throws BlockNotConnectingException If new block does not connect to previous block
     */
    public Block parseBlock(RawBlock rawBlock) throws BlockNotConnectingException {
        int blockHeight = rawBlock.getHeight();
        log.debug("Parse block at height={} ", blockHeight);

        validateIfBlockIsConnecting(rawBlock);

        bsqStateService.onNewBlockHeight(blockHeight);

        // We create a block from the rawBlock but the transaction list is not set yet (is empty)
        final Block block = new Block(blockHeight,
                rawBlock.getTime(),
                rawBlock.getHash(),
                rawBlock.getPreviousBlockHash());

        if (isBlockAlreadyAdded(rawBlock)) {
            //TODO check how/if that can happen
            log.warn("Block was already added.");
            DevEnv.logErrorAndThrowIfDevMode("Block was already added. rawBlock=" + rawBlock);
        } else {
            bsqStateService.onNewBlockWithEmptyTxs(block);
        }

        // Worst case is that all txs in a block are depending on another, so only one get resolved at each iteration.
        // Min tx size is 189 bytes (normally about 240 bytes), 1 MB can contain max. about 5300 txs (usually 2000).
        // Realistically we don't expect more then a few recursive calls.
        // There are some blocks with testing such dependency chains like block 130768 where at each iteration only
        // one get resolved.
        // Lately there is a patter with 24 iterations observed
        long startTs = System.currentTimeMillis();
        List<Tx> txList = block.getTxs();

        rawBlock.getRawTxs().forEach(rawTx ->
            txParser.findTx(rawTx,
                    genesisTxId,
                    genesisBlockHeight,
                    genesisTotalSupply)
                    .ifPresent(txList::add));
        log.debug("parseBsqTxs took {} ms", rawBlock.getRawTxs().size(), System.currentTimeMillis() - startTs);

        bsqStateService.onParseBlockComplete(block);
        return block;
    }

    private void validateIfBlockIsConnecting(RawBlock rawBlock) throws BlockNotConnectingException {
        LinkedList<Block> blocks = bsqStateService.getBlocks();
        if (!isBlockConnecting(rawBlock, blocks)) {
            final Block last = blocks.getLast();
            log.warn("addBlock called with a not connecting block. New block:\n" +
                            "height()={}, hash()={}, lastBlock.height()={}, lastBlock.hash()={}",
                    rawBlock.getHeight(),
                    rawBlock.getHash(),
                    last != null ? last.getHeight() : "null",
                    last != null ? last.getHash() : "null");
            throw new BlockNotConnectingException(rawBlock);
        }
    }

    private boolean isBlockAlreadyAdded(RawBlock rawBlock) {
        return bsqStateService.isBlockHashKnown(rawBlock.getHash());
    }

    private boolean isBlockConnecting(RawBlock rawBlock, LinkedList<Block> blocks) {
        // Case 1: blocks is empty
        // Case 2: blocks not empty. Last block must match new blocks getPreviousBlockHash and
        // height of last block +1 must be new blocks height
        return blocks.isEmpty() ||
                (blocks.getLast().getHash().equals(rawBlock.getPreviousBlockHash()) &&
                        blocks.getLast().getHeight() + 1 == rawBlock.getHeight());
    }
}
