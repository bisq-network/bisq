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
import io.bisq.common.util.Tuple2;
import org.bitcoinj.core.Coin;
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

    abstract ListenableFuture<Tuple2<BsqUTXOMap, Integer>> syncFromGenesis(int genesisBlockHeight, String genesisTxId);

    abstract void syncFromGenesisCompete(String genesisTxId, int genesisBlockHeight, Consumer<Block> onNewBlockHandler);

    abstract int requestChainHeadHeight() throws BitcoindException, CommunicationException;

    abstract Block requestBlock(int i) throws BitcoindException, CommunicationException;

    abstract Tx requestTransaction(String txId) throws BsqBlockchainException;

    @VisibleForTesting
    BsqUTXOMap parseAllBlocksFromGenesis(int chainHeadHeight,
                                         int genesisBlockHeight,
                                         String genesisTxId) throws BsqBlockchainException {
        try {
            BsqUTXOMap bsqUTXOMap = new BsqUTXOMap();
            log.info("blockCount=" + chainHeadHeight);
            long startTs = System.currentTimeMillis();
            for (int height = genesisBlockHeight; height <= chainHeadHeight; height++) {
                Block btcdBlock = requestBlock(height);
                log.info("height=" + height);
                parseBlock(new BsqBlock(btcdBlock.getTx(), btcdBlock.getHeight()),
                        genesisBlockHeight,
                        genesisTxId,
                        bsqUTXOMap);
            }
            printUtxoMap(bsqUTXOMap);
            log.info("Took {} ms", System.currentTimeMillis() - startTs);
            return bsqUTXOMap;
        } catch (Throwable t) {
            throw new BsqBlockchainException(t.getMessage(), t);
        }
    }

    void parseBlock(BsqBlock block,
                    int genesisBlockHeight,
                    String genesisTxId,
                    BsqUTXOMap bsqUTXOMap)
            throws BsqBlockchainException {
        int blockHeight = block.getHeight();
        log.debug("Parse block at height={} ", blockHeight);
        // We add all transactions to the block
        List<String> txIds = block.getTxIds();
        for (String txId : txIds) {
            block.addTx(requestTransaction(txId));
        }

        // First we check for the genesis tx
        // All outputs of genesis are valid BSQ UTXOs
        Map<String, Tx> txByTxIdMap = block.getTxByTxIdMap();
        if (blockHeight == genesisBlockHeight) {
            txByTxIdMap.entrySet().stream()
                    .filter(entry -> entry.getKey().equals(genesisTxId))
                    .forEach(entry -> parseGenesisTx(entry.getValue(), blockHeight, bsqUTXOMap));

            // We need to remove the genesis tx from further parsing (would be treated as intraBlockInputTx)
            txByTxIdMap.remove(genesisTxId);
        }

        // Worst case is that all txs in a block are depending on another, so only once get resolved at each iteration.
        // Min tx size is 189 bytes (normally about 240 bytes), 1 MB can contain max. about 5300 txs (usually 2000).
        // Realistically we don't expect more then a few recursive calls.
        updateBsqUtxoMapFromBlock(txByTxIdMap.values(), bsqUTXOMap, blockHeight, 0, 5300);
    }

    // Recursive method
    void updateBsqUtxoMapFromBlock(Collection<Tx> transactions,
                                   BsqUTXOMap bsqUTXOMap,
                                   int blockHeight,
                                   int recursionCounter,
                                   int maxRecursions) {

        if (recursionCounter > 10)
            log.warn("Unusual high recursive calls at resolveConnectedTxs. recursionCounter=" + recursionCounter);

        // The set of txIds of txs which are used for inputs of another tx in same block
        Set<String> intraBlockSpendingTxIdSet = getIntraBlockSpendingTxIdSet(transactions);

        Set<Tx> nonIntraBlockInputTxs = new HashSet<>();
        Set<Tx> intraBlockInputTxs = new HashSet<>();
        // First we find the txs which have no intra-block inputs
        outerLoop:
        for (Tx tx : transactions) {
            for (TxInput input : tx.getInputs()) {
                if (intraBlockSpendingTxIdSet.contains(input.getSpendingTxId())) {
                    // We have an input from one of the intra-block-transactions, so we cannot process that tx now.
                    // We add the tx for later parsing to the intraBlockInputTxs and move to the next 
                    // outerLoop iteration .
                    intraBlockInputTxs.add(tx);
                    continue outerLoop;
                }
            }
            // If we have not found any tx input pointing to anther tx in the same block we add it to our
            // nonIntraBlockInputTxs for first pass of BSQ validation
            nonIntraBlockInputTxs.add(tx);
        }

        log.debug("intraBlockInputTxs " + intraBlockInputTxs.size());
        log.debug("nonIntraBlockInputTxs " + nonIntraBlockInputTxs.size());

        // we check if we have any valid BSQ utxo from that tx set
        boolean utxoChanged = false;
        if (!nonIntraBlockInputTxs.isEmpty()) {
            for (Tx tx : nonIntraBlockInputTxs) {
                utxoChanged = utxoChanged || updateBsqUtxoMapFromTx(tx, blockHeight, bsqUTXOMap);
            }
        }

        // we check if we have any valid BSQ utxo from that tx set
        if (!intraBlockInputTxs.isEmpty()) {
            if (utxoChanged) {
                if (recursionCounter < maxRecursions) {
                    updateBsqUtxoMapFromBlock(intraBlockInputTxs, bsqUTXOMap, blockHeight, ++recursionCounter, maxRecursions);
                } else {
                    final String msg = "We exceeded our max. recursions for resolveConnectedTxs.\n" +
                            "intraBlockInputTxs=" + intraBlockInputTxs.toString() + "\n" +
                            "nonIntraBlockInputTxs=" + nonIntraBlockInputTxs;
                    log.warn(msg);
                    if (DevEnv.DEV_MODE)
                        throw new RuntimeException(msg);
                }
            } else {
                final String msg = "If we have intraBlockInputTxs we must have had got the utxoChanged, otherwise we cannot " +
                        "satisfy the open intraBlockInputTxs.\n" +
                        "intraBlockInputTxs=" + intraBlockInputTxs.toString() + "\n" +
                        "nonIntraBlockInputTxs=" + nonIntraBlockInputTxs;
                log.warn(msg);
                if (DevEnv.DEV_MODE)
                    throw new RuntimeException(msg);
            }
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
                                           BsqUTXOMap bsqUTXOMap) {
        String txId = tx.getId();
        List<TxOutput> outputs = tx.getOutputs();
        boolean utxoChanged = false;
        Coin availableValue = Coin.ZERO;
        for (TxInput input : tx.getInputs()) {
            String spendingTxId = input.getSpendingTxId();
            final int spendingTxOutputIndex = input.getSpendingTxOutputIndex();
            if (bsqUTXOMap.containsTuple(spendingTxId, spendingTxOutputIndex)) {
                BsqUTXO bsqUTXO = bsqUTXOMap.getByTuple(spendingTxId, spendingTxOutputIndex);
                availableValue = availableValue.add(bsqUTXO.getValue());

                bsqUTXOMap.removeByTuple(spendingTxId, spendingTxOutputIndex);
                utxoChanged = true;

                if (bsqUTXOMap.isEmpty())
                    break;
            }
        }

        // If we have an input spending tokens we iterate the outputs
        if (availableValue.isPositive()) {

            // We use order of output index. An output is a BSQ utxo as long there is enough input value
            for (int outputIndex = 0; outputIndex < outputs.size(); outputIndex++) {
                TxOutput txOutput = outputs.get(outputIndex);

                availableValue = availableValue.subtract(txOutput.getValue());
                if (!availableValue.isNegative()) {
                    // We are spending available tokens
                    BsqUTXO bsqUTXO = new BsqUTXO(txId,
                            blockHeight,
                            false,
                            txOutput);
                    bsqUTXOMap.putByTuple(txId, outputIndex, bsqUTXO);
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
            if (availableValue.isPositive()) {
                log.warn("BSQ have been left which was not spent. Burned BSQ amount={}, tx={}",
                        availableValue.value,
                        tx.toString());
            }
        }
        return utxoChanged;
    }


    @VisibleForTesting
    void parseGenesisTx(Tx tx, int blockHeight, BsqUTXOMap bsqUTXOMap) {
        String txId = tx.getId();
        List<TxOutput> outputs = tx.getOutputs();

        //TODO use BsqTXO not BsqUTXO as we dont know if they are unspent
        // Genesis tx uses all outputs as BSQ outputs
        for (int index = 0; index < outputs.size(); index++) {
            TxOutput txOutput = outputs.get(index);
            BsqUTXO bsqUTXO = new BsqUTXO(txId,
                    blockHeight,
                    true,
                    txOutput);
            bsqUTXOMap.putByTuple(txId, index, bsqUTXO);
        }
        checkArgument(!bsqUTXOMap.isEmpty(), "Genesis tx need to have BSQ utxo when parsing genesis block");
    }

    void printUtxoMap(BsqUTXOMap bsqUTXOMap) {
        if (log.isInfoEnabled() || log.isDebugEnabled() || log.isTraceEnabled()) {
            StringBuilder sb = new StringBuilder("bsqUTXOMap:\n");
            bsqUTXOMap.entrySet().stream().forEach(e -> {
                        sb.append("TxId:Index = ").append(e.getKey()).append("\n");
                        sb.append("      UTXO = ").append(e.getValue().toString()).append("\n");
                    }
            );
            log.error(sb.toString());
        }
    }
}
