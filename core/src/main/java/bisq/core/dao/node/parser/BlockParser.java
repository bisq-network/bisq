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

import bisq.core.dao.node.full.RawBlock;
import bisq.core.dao.node.parser.exceptions.BlockHashNotConnectingException;
import bisq.core.dao.node.parser.exceptions.BlockHeightNotConnectingException;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * Parse a rawBlock and creates a block from it with an empty tx list.
 * Iterates all rawTx and if the tx is a BSQ tx it gets added to the tx list.
 */
@Slf4j
@Immutable
public class BlockParser {
    private final TxParser txParser;
    private final DaoStateService daoStateService;
    private final String genesisTxId;
    private final int genesisBlockHeight;
    private final Coin genesisTotalSupply;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlockParser(TxParser txParser,
                       DaoStateService daoStateService) {
        this.txParser = txParser;
        this.daoStateService = daoStateService;
        this.genesisTxId = daoStateService.getGenesisTxId();
        this.genesisBlockHeight = daoStateService.getGenesisBlockHeight();
        this.genesisTotalSupply = daoStateService.getGenesisTotalSupply();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     *
     * @param rawBlock  Contains all transactions of a bitcoin block without any BSQ specific data
     * @return Block: Gets created from the rawBlock but contains only BSQ specific transactions.
     * @throws BlockHashNotConnectingException If new block does not connect to previous block
     * @throws BlockHeightNotConnectingException If new block height is not current chain Height + 1
     */
    public Block parseBlock(RawBlock rawBlock) throws BlockHashNotConnectingException, BlockHeightNotConnectingException {
        long startTs = System.currentTimeMillis();
        int blockHeight = rawBlock.getHeight();
        log.trace("Parse block at height={} ", blockHeight);

        validateIfBlockIsConnecting(rawBlock);

        daoStateService.onNewBlockHeight(blockHeight);

        // We create a block from the rawBlock but the transaction list is not set yet (is empty)
        final Block block = new Block(blockHeight,
                rawBlock.getTime(),
                rawBlock.getHash(),
                rawBlock.getPreviousBlockHash());

        if (isBlockAlreadyAdded(rawBlock)) {
            log.warn("Block was already added.");
            DevEnv.logErrorAndThrowIfDevMode("Block was already added. rawBlock=" + rawBlock);
        } else {
            daoStateService.onNewBlockWithEmptyTxs(block);
        }

        // Worst case is that all txs in a block are depending on another, so only one get resolved at each iteration.
        // Min tx size is 189 bytes (normally about 240 bytes), 1 MB can contain max. about 5300 txs (usually 2000).
        // Realistically we don't expect more than a few recursive calls.
        // There are some blocks with testing such dependency chains like block 130768 where at each iteration only
        // one get resolved.
        // Lately there is a patter with 24 iterations observed

        rawBlock.getRawTxs().forEach(rawTx ->
                txParser.findTx(rawTx,
                        genesisTxId,
                        genesisBlockHeight,
                        genesisTotalSupply)
                        .ifPresent(tx -> daoStateService.onNewTxForLastBlock(block, tx)));

        daoStateService.onParseBlockComplete(block);
        long duration = System.currentTimeMillis() - startTs;
        if (duration > 10) {
            log.info("Parsing {} transactions at block height {} took {} ms", rawBlock.getRawTxs().size(),
                    blockHeight, duration);
        }

        return block;
    }

    private void validateIfBlockIsConnecting(RawBlock rawBlock) throws BlockHashNotConnectingException, BlockHeightNotConnectingException {
        List<Block> blocks = daoStateService.getBlocks();

        if (blocks.isEmpty())
            return;

        if (daoStateService.getBlockHeightOfLastBlock() + 1 != rawBlock.getHeight())
            throw new BlockHeightNotConnectingException(rawBlock);

        if (!daoStateService.getBlockHashOfLastBlock().equals(rawBlock.getPreviousBlockHash()))
            throw new BlockHashNotConnectingException(rawBlock);
    }

    private boolean isBlockAlreadyAdded(RawBlock rawBlock) {
        return daoStateService.getBlockAtHeight(rawBlock.getHeight())
                .map(block -> block.getHash().equals(rawBlock.getHash()))
                .orElse(false);
    }
}
