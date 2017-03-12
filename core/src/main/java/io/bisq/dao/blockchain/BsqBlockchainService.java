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

package io.bisq.dao.blockchain;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.domain.Block;
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

    protected Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap;


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

    abstract ListenableFuture<Tuple2<Map<String, Map<Integer, BsqUTXO>>, Integer>> syncFromGenesis(int genesisBlockHeight, String genesisTxId);

    abstract void syncFromGenesisCompete(String genesisTxId, int genesisBlockHeight, Consumer<Block> onNewBlockHandler);

    abstract int requestChainHeadHeight() throws BitcoindException, CommunicationException;

    abstract Block requestBlock(int i) throws BitcoindException, CommunicationException;

    abstract BsqTransaction requestTransaction(String txId) throws BsqBlockchainException;

    Map<String, Map<Integer, BsqUTXO>> parseBlockchain(Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap,
                                                       int chainHeadHeight,
                                                       int genesisBlockHeight,
                                                       String genesisTxId)
            throws BsqBlockchainException {
        try {
            //log.info("blockCount=" + chainHeadHeight);
            long startTs = System.currentTimeMillis();
            for (int blockHeight = genesisBlockHeight; blockHeight <= chainHeadHeight; blockHeight++) {
                Block block = requestBlock(blockHeight);
                //log.info("blockHeight=" + blockHeight);
                parseBlock(new BsqBlock(block.getTx(), block.getHeight()),
                        genesisBlockHeight,
                        genesisTxId,
                        utxoByTxIdMap);
            }
            printUtxoMap(utxoByTxIdMap);
            // log.info("Took {} ms", System.currentTimeMillis() - startTs);
        } catch (BitcoindException | CommunicationException e) {
            throw new BsqBlockchainException(e.getMessage(), e);
        }
        return utxoByTxIdMap;
    }

    void parseBlock(BsqBlock block,
                    int genesisBlockHeight,
                    String genesisTxId,
                    Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap)
            throws BsqBlockchainException {
        int blockHeight = block.blockHeight;
        log.debug("Parse block at height={} ", blockHeight);
        // We add all transactions to the block
        List<String> txIds = block.txIds;
        for (String txId : txIds) {
            block.addBsqTransaction(requestTransaction(txId));
        }

        // First we check for the genesis tx
        Map<String, BsqTransaction> transactionsMap = block.getTransactions();
        if (blockHeight == genesisBlockHeight) {
            transactionsMap.entrySet().stream()
                    .filter(entry -> entry.getKey().equals(genesisTxId))
                    .forEach(entry -> parseGenesisTx(entry.getValue(), blockHeight, utxoByTxIdMap));
        }

        resolveConnectedTxs(transactionsMap.values(), utxoByTxIdMap, blockHeight, 0, 100);
    }

    void resolveConnectedTxs(Collection<BsqTransaction> transactions,
                             Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap,
                             int blockHeight,
                             int recursions,
                             int maxRecursions) {
        // The set of txIds of txs which are used for inputs in a tx in that block
        Set<String> spendingTxIdSet = getSpendingTxIdSet(transactions);

        // We check if the tx has only connections to the UTXO set, if so we add it to the connectedTxs, otherwise it
        // is an orphaned tx.
        // connectedTxs: Those who have inputs in the UTXO set
        // orphanTxs: Those who have inputs from other txs in the same block
        Set<BsqTransaction> connectedTxs = new HashSet<>();
        Set<BsqTransaction> orphanTxs = new HashSet<>();
        outerLoop:
        for (BsqTransaction transaction : transactions) {
            boolean isConnected = false;
            for (BsqTxInput input : transaction.inputs) {
                String spendingTxId = input.spendingTxId;
                if (spendingTxIdSet.contains(spendingTxId)) {
                    // We have an input in one of the blocks transactions, so we cannot process that tx now.
                    // We break out here if at least 1 input points to a tx in the same block
                    orphanTxs.add(transaction);
                    continue outerLoop;
                } else if (utxoByTxIdMap.containsKey(spendingTxId)) {
                    // If we find the tx in the utxo set we set the isConnected flag.
                    Map<Integer, BsqUTXO> utxoByIndexMap = utxoByTxIdMap.get(spendingTxId);
                    if (utxoByIndexMap != null && utxoByIndexMap.containsKey(input.spendingOuptuIndex)) {
                        // Our input has a connection to an tx from the utxo set
                        isConnected = true;
                    }
                }
            }

            if (isConnected)
                connectedTxs.add(transaction);
        }

        // Now we check if our connected txs are valid BSQ transactions
        for (BsqTransaction transaction : connectedTxs) {
            verifyTransaction(transaction, blockHeight, utxoByTxIdMap);
        }
        //log.info("orphanTxs " + orphanTxs);
        if (!orphanTxs.isEmpty() && recursions < maxRecursions)
            resolveConnectedTxs(orphanTxs, utxoByTxIdMap, blockHeight, ++recursions, maxRecursions);
    }

    private Set<String> getSpendingTxIdSet(Collection<BsqTransaction> transactions) {
        Set<String> txIdSet = transactions.stream().map(tx -> tx.txId).collect(Collectors.toSet());
        Set<String> spendingTxIdSet = new HashSet<>();
        transactions.stream()
                .forEach(transaction -> transaction.inputs.stream()
                        .forEach(input -> {
                            String spendingTxId = input.spendingTxId;
                            if (txIdSet.contains(spendingTxId))
                                spendingTxIdSet.add(spendingTxId);
                        }));
        return spendingTxIdSet;
    }

    private void verifyTransaction(BsqTransaction bsqTransaction,
                                   int blockHeight,
                                   Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap
    ) {
        String txId = bsqTransaction.txId;
        List<BsqTxOutput> outputs = bsqTransaction.outputs;

        Coin availableValue = Coin.ZERO;
        for (BsqTxInput input : bsqTransaction.inputs) {
            String spendingTxId = input.spendingTxId;
            if (utxoByTxIdMap.containsKey(spendingTxId)) {
                Map<Integer, BsqUTXO> utxoByIndexMap = utxoByTxIdMap.get(spendingTxId);
                Integer index = input.spendingOuptuIndex;
                if (utxoByIndexMap.containsKey(index)) {
                    BsqUTXO utxo = utxoByIndexMap.get(index);

                    utxoByIndexMap.remove(index);
                    availableValue = availableValue.add(utxo.getValue());
                    if (utxoByIndexMap.isEmpty()) {
                        // If no more entries by index we can remove the whole entry by txId
                        utxoByTxIdMap.remove(spendingTxId);
                    }
                }
            }
        }
        // If we have an input spending tokens we iterate the outputs
        if (availableValue.isPositive()) {
            Map<Integer, BsqUTXO> utxoByIndexMap = utxoByTxIdMap.containsKey(txId) ?
                    utxoByTxIdMap.get(txId) :
                    new HashMap<>();
            // We sort by index, inputs are tokens as long there is enough input value
            for (int i = 0; i < outputs.size(); i++) {
                BsqTxOutput squOutput = outputs.get(i);
                List<String> addresses = squOutput.addresses;
                // Only at raw MS outputs addresses have more then 1 entry 
                // We do not support raw MS for BSQ
                if (addresses.size() == 1) {
                    String address = addresses.get(0);
                    availableValue = availableValue.subtract(squOutput.value);
                    if (!availableValue.isNegative()) {
                        // We are spending available tokens
                        BsqUTXO utxo = new BsqUTXO(txId,
                                squOutput.index,
                                squOutput.value,
                                blockHeight,
                                false,
                                squOutput.script,
                                address);
                        utxoByIndexMap.put(i, utxo);
                    } else {
                        log.warn("We tried to spend more BSQ as we have in our inputs");
                        break;
                    }
                } else {
                    log.warn("addresses.size() is not 1.");
                }
            }

            //TODO write that warning to a handler
            if (availableValue.isPositive()) {
                log.warn("BSQ have been left which was not spent. Burned BSQ amount={}, tx={}",
                        availableValue.value,
                        bsqTransaction.toString());
            }
            if (!utxoByIndexMap.isEmpty() && !utxoByTxIdMap.containsKey(txId)) {
                boolean wasEmpty = utxoByTxIdMap.put(txId, utxoByIndexMap) == null;
                checkArgument(wasEmpty, "We must not have that tx in the map. txId=" + txId);
            }
        }
    }


    @VisibleForTesting
    void parseGenesisTx(BsqTransaction bsqTransaction, int blockHeight, Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap) {
        String txId = bsqTransaction.txId;
        List<BsqTxOutput> outputs = bsqTransaction.outputs;

        // Genesis tx uses all outputs as BSQ outputs
        Map<Integer, BsqUTXO> utxoByIndexMap = new HashMap<>();
        for (int i = 0; i < outputs.size(); i++) {
            BsqTxOutput output = outputs.get(i);
            List<String> addresses = output.addresses;
            // Only at raw MS outputs addresses have more then 1 entry 
            // We do not support raw MS for BSQ
            if (addresses.size() == 1) {
                String address = addresses.get(0);
                //TODO set coinbase to true after testing
                BsqUTXO utxo = new BsqUTXO(txId,
                        output.index,
                        output.value,
                        blockHeight,
                        false,
                        output.script,
                        address);
                utxoByIndexMap.put(i, utxo);
            }
        }
        checkArgument(!utxoByIndexMap.isEmpty(), "Genesis tx must have squ utxo");
        boolean wasEmpty = utxoByTxIdMap.put(txId, utxoByIndexMap) == null;
        checkArgument(wasEmpty, "We must not have that tx in the map. txId=" + txId);
    }

    void printUtxoMap(Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap) {
        StringBuilder sb = new StringBuilder("utxoByTxIdMap:\n");
        utxoByTxIdMap.entrySet().stream().forEach(e -> {
            sb.append("TxId: ").append(e.getKey()).append("\n");
            e.getValue().entrySet().stream().forEach(a -> {
                sb.append("    [").append(a.getKey()).append("] {")
                        .append(a.getValue().toString()).append("}\n");
            });
        });
        //log.info(sb.toString());
    }
}
