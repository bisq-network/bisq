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

package io.bisq.core.dao.blockchain;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.domain.Block;
import io.bisq.common.app.DevEnv;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.util.Profiler;
import io.bisq.common.util.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

abstract public class BsqBlockchainService {
    private static final Logger log = LoggerFactory.getLogger(BsqBlockchainService.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqBlockchainService() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract void setup(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler);

    abstract ListenableFuture<Tuple2<BsqUTXOMap, Integer>> syncFromGenesis(BsqUTXOMap bsqUTXOMap, BsqTXOMap bsqTXOMap, int genesisBlockHeight, String genesisTxId);

    abstract void syncFromGenesisCompete(BsqUTXOMap bsqUTXOMap, BsqTXOMap bsqTXOMap, String genesisTxId, int genesisBlockHeight, Consumer<Block> onNewBlockHandler);

    abstract int requestChainHeadHeight() throws BitcoindException, CommunicationException;

    abstract Block requestBlock(int i) throws BitcoindException, CommunicationException;

    abstract Tx requestTransaction(String txId) throws BsqBlockchainException;

    @VisibleForTesting
    BsqUTXOMap parseAllBlocksFromGenesis(BsqUTXOMap bsqUTXOMap,
                                         BsqTXOMap bsqTXOMap,
                                         int chainHeadHeight,
                                         int genesisBlockHeight,
                                         String genesisTxId) throws BsqBlockchainException {
        try {
            log.info("chainHeadHeight=" + chainHeadHeight);
            long startTotalTs = System.currentTimeMillis();
            for (int height = genesisBlockHeight; height <= chainHeadHeight; height++) {
                long startBlockTs = System.currentTimeMillis();
                Block btcdBlock = requestBlock(height);
                log.info("Current block height=" + height);

                // 1 block has about 3 MB
                final BsqBlock bsqBlock = new BsqBlock(btcdBlock.getTx(), btcdBlock.getHeight());

                String oldBsqUTXOMap = bsqUTXOMap.toString();
                parseBlock(bsqBlock,
                        genesisBlockHeight,
                        genesisTxId,
                        bsqUTXOMap,
                        bsqTXOMap);
                String newBsqUTXOMap = bsqUTXOMap.toString();
                if (!oldBsqUTXOMap.equals(newBsqUTXOMap))
                    log.info(bsqUTXOMap.toString());

                log.info("Parsing for block {} took {} ms. Total: {} ms for {} blocks",
                        height,
                        (System.currentTimeMillis() - startBlockTs),
                        (System.currentTimeMillis() - startTotalTs),
                        (height - genesisBlockHeight));
                Profiler.printSystemLoad(log);
            }
            log.info("Parsing for all blocks since genesis took {} ms", System.currentTimeMillis() - startTotalTs);
            return bsqUTXOMap;
        } catch (Throwable t) {
            log.error(t.toString());
            t.printStackTrace();
            throw new BsqBlockchainException(t);
        }
    }

    void parseBlock(BsqBlock block,
                    int genesisBlockHeight,
                    String genesisTxId,
                    BsqUTXOMap bsqUTXOMap,
                    BsqTXOMap bsqTXOMap)
            throws BsqBlockchainException {
        int blockHeight = block.getHeight();
        log.debug("Parse block at height={} ", blockHeight);
        // We add all transactions to the block

        // TODO here we hve the performance bottleneck. takes about 4 sec.
        // check if there is more efficient rpc calls for tx ranges or all txs in a block with btcd 14
        List<String> txIds = block.getTxIds();
        //long startTs = System.currentTimeMillis();
        for (String txId : txIds) {
            block.addTx(requestTransaction(txId));
        }
        //log.info("requestTransaction took {} ms for {} txs", System.currentTimeMillis() - startTs, txIds.size());

        // First we check for the genesis tx
        // All outputs of genesis are valid BSQ UTXOs
        Map<String, Tx> txByTxIdMap = block.getTxByTxIdMap();
        if (blockHeight == genesisBlockHeight) {
            txByTxIdMap.entrySet().stream()
                    .filter(entry -> entry.getKey().equals(genesisTxId))
                    .forEach(entry -> parseGenesisTx(entry.getValue(), blockHeight, bsqUTXOMap, bsqTXOMap));
        }

        // Worst case is that all txs in a block are depending on another, so only once get resolved at each iteration.
        // Min tx size is 189 bytes (normally about 240 bytes), 1 MB can contain max. about 5300 txs (usually 2000).
        // Realistically we don't expect more then a few recursive calls.
        updateBsqUtxoMapFromBlock(txByTxIdMap.values(), bsqUTXOMap, bsqTXOMap, blockHeight, 0, 5300);
    }

    // Recursive method
    // Performance-wise the recursion does not hurt (e.g. 5-20 ms).  
    void updateBsqUtxoMapFromBlock(Collection<Tx> transactions,
                                   BsqUTXOMap bsqUTXOMap,
                                   BsqTXOMap bsqTXOMap,
                                   int blockHeight,
                                   int recursionCounter,
                                   int maxRecursions) {
        // The set of txIds of txs which are used for inputs of another tx in same block
        Set<String> intraBlockSpendingTxIdSet = getIntraBlockSpendingTxIdSet(transactions);

        Set<Tx> txsWithoutInputsFromSameBlock = new HashSet<>();
        Set<Tx> txsWithInputsFromSameBlock = new HashSet<>();

        // First we find the txs which have no intra-block inputs
        outerLoop:
        for (Tx tx : transactions) {
            for (TxInput input : tx.getInputs()) {
                if (intraBlockSpendingTxIdSet.contains(input.getSpendingTxId())) {
                    // We have an input from one of the intra-block-transactions, so we cannot process that tx now.
                    // We add the tx for later parsing to the txsWithInputsFromSameBlock and move to the next 
                    // outerLoop iteration .
                    txsWithInputsFromSameBlock.add(tx);

                    continue outerLoop;
                }
            }
            // If we have not found any tx input pointing to anther tx in the same block we add it to our
            // txsWithoutInputsFromSameBlock for first pass of BSQ validation
            txsWithoutInputsFromSameBlock.add(tx);
        }

        // Usual values is up to 25
        // There are soem blocks where it seems devs have tested many depending txs, but even 
        // those dont exceed 200 recursions and are old blocks from 2012 or so...
        // TOD) check strategy btc core uses (sorting the dependency graph would be an optimisation)
        if (recursionCounter > 30) {
            log.warn("Unusual high recursive calls at resolveConnectedTxs. recursionCounter=" + recursionCounter);
            log.warn("txsWithoutInputsFromSameBlock " + txsWithoutInputsFromSameBlock.size());
            log.warn("txsWithInputsFromSameBlock " + txsWithInputsFromSameBlock.size());
        }
        // we check if we have any valid BSQ utxo from that tx set
        if (!txsWithoutInputsFromSameBlock.isEmpty()) {
            for (Tx tx : txsWithoutInputsFromSameBlock) {
                updateBsqUtxoMapFromTx(tx, blockHeight, bsqUTXOMap, bsqTXOMap);
            }
        }

        // we check if we have any valid BSQ utxo from that tx set
        // We might have InputsFromSameBlock whcih are BTC only but not BSQ, so we cannot 
        // optimize here and need to iterate further.
        // TODO recursion risk?
        if (!txsWithInputsFromSameBlock.isEmpty()) {
            if (recursionCounter < maxRecursions) {
                updateBsqUtxoMapFromBlock(txsWithInputsFromSameBlock, bsqUTXOMap, bsqTXOMap, blockHeight, ++recursionCounter, maxRecursions);
            } else {
                final String msg = "We exceeded our max. recursions for resolveConnectedTxs.\n" +
                        "txsWithInputsFromSameBlock=" + txsWithInputsFromSameBlock.toString() + "\n" +
                        "txsWithoutInputsFromSameBlock=" + txsWithoutInputsFromSameBlock;
                log.warn(msg);
                if (DevEnv.DEV_MODE)
                    throw new RuntimeException(msg);
            }
        } else {
            log.debug("We have no more txsWithInputsFromSameBlock.");
        }
    }

    private Set<String> getIntraBlockSpendingTxIdSet(Collection<Tx> transactions) {
        Set<String> txIdSet = transactions.stream().map(Tx::getId).collect(Collectors.toSet());
        Set<String> intraBlockSpendingTxIdSet = new HashSet<>();
        transactions.stream()
                .forEach(tx -> tx.getInputs().stream()
                        .filter(input -> txIdSet.contains(input.getSpendingTxId()))
                        .forEach(input -> intraBlockSpendingTxIdSet.add(input.getSpendingTxId())));
        return intraBlockSpendingTxIdSet;
    }

    private boolean updateBsqUtxoMapFromTx(Tx tx,
                                           int blockHeight,
                                           BsqUTXOMap bsqUTXOMap,
                                           BsqTXOMap bsqTXOMap) {
        String txId = tx.getId();
        List<TxOutput> outputs = tx.getOutputs();
        boolean utxoChanged = false;
        long availableValue = 0;
        for (TxInput input : tx.getInputs()) {
            String spendingTxId = input.getSpendingTxId();
            final int spendingTxOutputIndex = input.getSpendingTxOutputIndex();
            if (bsqUTXOMap.containsTuple(spendingTxId, spendingTxOutputIndex)) {
                BsqUTXO bsqUTXO = bsqUTXOMap.getByTuple(spendingTxId, spendingTxOutputIndex);
                log.debug("input value " + bsqUTXO.getValue());
                availableValue = availableValue + bsqUTXO.getValue();

                bsqUTXOMap.removeByTuple(spendingTxId, spendingTxOutputIndex);
                bsqUTXOMap.setLastBlockHeight(blockHeight);
                utxoChanged = true;

                if (bsqUTXOMap.isEmpty())
                    break;
            }
        }

        // If we have an input spending tokens we iterate the outputs
        if (availableValue > 0) {

            // We use order of output index. An output is a BSQ utxo as long there is enough input value
            for (int outputIndex = 0; outputIndex < outputs.size(); outputIndex++) {
                TxOutput txOutput = outputs.get(outputIndex);
                availableValue = availableValue - txOutput.getValue();
                if (availableValue >= 0) {
                    if (txOutput.getAddresses().size() != 1) {
                        final String msg = "We got a address list with more or less than 1 address for a BsqUTXO. " +
                                "Seems to be a raw MS. Raw MS are not supported with BSQ.\n" + this.toString();
                        log.warn(msg);
                        if (DevEnv.DEV_MODE)
                            throw new RuntimeException(msg);
                    }
                    // We are spending available tokens
                    bsqUTXOMap.add(new BsqUTXO(txOutput, blockHeight, false));
                    bsqUTXOMap.setLastBlockHeight(blockHeight);
                    bsqTXOMap.add(txOutput);
                    bsqTXOMap.setLastBlockHeight(blockHeight);
                    
                    if (availableValue == 0) {
                        log.debug("We don't have anymore BSQ to spend");
                        break;
                    }
                } else {
                    log.warn("We tried to spend more BSQ as we have in our inputs");
                    // TODO report burnt BSQ
                    // TODO: check if we should be more tolerant and use 
                    // availableValue = availableValue.subtract(txOutput.getValue());
                    // only temp and allow follow up outputs to use the left input.
                    break;
                }
            }

            //TODO write that warning to a handler
            if (availableValue > 0) {
                log.warn("BSQ have been left which was not spent. Burned BSQ amount={}, tx={}",
                        availableValue,
                        tx.toString());
            }
        }
        return utxoChanged;
    }


    @VisibleForTesting
    void parseGenesisTx(Tx tx,
                        int blockHeight,
                        BsqUTXOMap bsqUTXOMap,
                        BsqTXOMap bsqTXOMap) {
        String txId = tx.getId();
        List<TxOutput> outputs = tx.getOutputs();

        //TODO use BsqTXO not BsqUTXO as we dont know if they are unspent
        // Genesis tx uses all outputs as BSQ outputs
        for (int index = 0; index < outputs.size(); index++) {
            TxOutput txOutput = outputs.get(index);
            if (txOutput.getAddresses().size() != 1) {
                final String msg = "We got a address list with more or less than 1 address. " +
                        "Seems to be a raw MS. Raw MS are not supported with BSQ.\n" + this.toString();
                log.warn(msg);
                if (DevEnv.DEV_MODE)
                    throw new RuntimeException(msg);
            }
            bsqUTXOMap.add(new BsqUTXO(txOutput, blockHeight, true));
            bsqUTXOMap.setLastBlockHeight(blockHeight);
            bsqTXOMap.add(txOutput);
            bsqTXOMap.setLastBlockHeight(blockHeight);
        }
        checkArgument(!bsqUTXOMap.isEmpty(), "Genesis tx need to have BSQ utxo when parsing genesis block");
    }
}
