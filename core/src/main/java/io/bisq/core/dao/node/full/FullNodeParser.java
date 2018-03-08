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

package io.bisq.core.dao.node.full;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.neemre.btcdcli4j.core.domain.Block;
import io.bisq.core.dao.blockchain.BsqBlockChain;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.exceptions.BsqBlockchainException;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.node.BsqParser;
import io.bisq.core.dao.node.consensus.BsqTxVerification;
import io.bisq.core.dao.node.consensus.GenesisTxVerification;
import io.bisq.core.dao.node.full.rpc.RpcService;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public class FullNodeParser extends BsqParser {

    private final RpcService rpcService;
    // Maybe we want to request fee at some point, leave it for now and disable it
    private final boolean requestFee = false;
    private final Map<Integer, Long> feesByBlock = new HashMap<>();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FullNodeParser(RpcService rpcService,
                          BsqBlockChain bsqBlockChain,
                          GenesisTxVerification genesisTxVerification,
                          BsqTxVerification bsqTxVerification) {
        super(bsqBlockChain, genesisTxVerification, bsqTxVerification);
        this.rpcService = rpcService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package private
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    void parseBlocks(int startBlockHeight,
                     int chainHeadHeight,
                     Consumer<BsqBlock> newBlockHandler) throws BsqBlockchainException, BlockNotConnectingException {
        try {
            for (int blockHeight = startBlockHeight; blockHeight <= chainHeadHeight; blockHeight++) {
                Block btcdBlock = rpcService.requestBlock(blockHeight);
                final BsqBlock bsqBlock = parseBlock(btcdBlock);
                newBlockHandler.accept(bsqBlock);
            }
        } catch (BlockNotConnectingException e) {
            throw e;
        } catch (Throwable t) {
            log.error(t.toString());
            t.printStackTrace();
            throw new BsqBlockchainException(t);
        }
    }

    BsqBlock parseBlock(Block btcdBlock) throws BsqBlockchainException, BlockNotConnectingException {
        long startTs = System.currentTimeMillis();
        List<Tx> bsqTxsInBlock = findBsqTxsInBlock(btcdBlock);
        final BsqBlock bsqBlock = new BsqBlock(btcdBlock.getHeight(),
                btcdBlock.getHash(),
                btcdBlock.getPreviousBlockHash(),
                ImmutableList.copyOf(bsqTxsInBlock));
        bsqBlockChain.addBlock(bsqBlock);
        log.info("parseBlock took {} ms at blockHeight {}; bsqTxsInBlock.size={}",
                System.currentTimeMillis() - startTs, bsqBlock.getHeight(), bsqTxsInBlock.size());
        return bsqBlock;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private List<Tx> findBsqTxsInBlock(Block btcdBlock) throws BsqBlockchainException {
        int blockHeight = btcdBlock.getHeight();
        log.debug("Parse block at height={} ", blockHeight);

        // Check if the new block is the same chain we have built on.
        List<Tx> txList = new ArrayList<>();
        // We use a list as we want to maintain sorting of tx intra-block dependency
        List<Tx> bsqTxsInBlock = new ArrayList<>();
        // We add all transactions to the block
        long startTs = System.currentTimeMillis();

        // We don't user foreach because scope for exception would not be in method body...
        for (String txId : btcdBlock.getTx()) {

            // TODO if we use requestFee move code to later point once we found our bsq txs, so we only request it for bsq txs
            if (requestFee)
                rpcService.requestFees(txId, blockHeight, feesByBlock);

            final Tx tx = rpcService.requestTx(txId, blockHeight);
            txList.add(tx);
            checkForGenesisTx(blockHeight, bsqTxsInBlock, tx);
        }
        log.info("Requesting {} transactions took {} ms",
                btcdBlock.getTx().size(), System.currentTimeMillis() - startTs);
        // Worst case is that all txs in a block are depending on another, so only one get resolved at each iteration.
        // Min tx size is 189 bytes (normally about 240 bytes), 1 MB can contain max. about 5300 txs (usually 2000).
        // Realistically we don't expect more then a few recursive calls.
        // There are some blocks with testing such dependency chains like block 130768 where at each iteration only
        // one get resolved.
        // Lately there is a patter with 24 iterations observed
        recursiveFindBsqTxs(bsqTxsInBlock, txList, blockHeight, 0, 5300);

        return bsqTxsInBlock;
    }
}
