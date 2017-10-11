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

package io.bisq.core.dao.blockchain.parse;

import com.google.common.collect.ImmutableList;
import com.neemre.btcdcli4j.core.domain.Block;
import io.bisq.common.app.DevEnv;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.exceptions.BsqBlockchainException;
import io.bisq.core.dao.blockchain.vo.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

// We are in threaded context. Don't mix up with UserThread.
@Slf4j
@Immutable
public class BsqParser {
    private final BsqChainState bsqChainState;
    private final OpReturnVerification opReturnVerification;
    private final IssuanceVerification issuanceVerification;
    private final RpcService rpcService;

    // Maybe we want to request fee at some point, leave it for now and disable it
    private boolean requestFee = false;
    private final Map<Integer, Long> feesByBlock = new HashMap<>();
    

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqParser(RpcService rpcService,
                     BsqChainState bsqChainState,
                     OpReturnVerification opReturnVerification,
                     IssuanceVerification issuanceVerification) {
        this.rpcService = rpcService;
        this.bsqChainState = bsqChainState;
        this.opReturnVerification = opReturnVerification;
        this.issuanceVerification = issuanceVerification;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Parsing with data delivered with BsqBlock list
    ///////////////////////////////////////////////////////////////////////////////////////////

    void parseBsqBlocks(List<BsqBlock> bsqBlocks,
                        int genesisBlockHeight,
                        String genesisTxId,
                        Consumer<BsqBlock> newBlockHandler)
            throws BlockNotConnectingException {
        for (BsqBlock bsqBlock : bsqBlocks) {
            parseBsqBlock(bsqBlock,
                    genesisBlockHeight,
                    genesisTxId);
            bsqChainState.addBlock(bsqBlock);
            newBlockHandler.accept(bsqBlock);
        }
    }

    void parseBsqBlock(BsqBlock bsqBlock,
                       int genesisBlockHeight,
                       String genesisTxId) {
        int blockHeight = bsqBlock.getHeight();
        log.info("Parse block at height={} ", blockHeight);
        List<Tx> txList = new ArrayList<>(bsqBlock.getTxs());
        List<Tx> bsqTxsInBlock = new ArrayList<>();
        bsqBlock.getTxs().stream()
                .forEach(tx -> checkForGenesisTx(genesisBlockHeight, genesisTxId, blockHeight, bsqTxsInBlock, tx));
        recursiveFindBsqTxs(bsqTxsInBlock, txList, blockHeight, 0, 5300);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Parsing with data requested from bsqBlockchainService
    ///////////////////////////////////////////////////////////////////////////////////////////

    void parseBlocks(int startBlockHeight,
                     int chainHeadHeight,
                     int genesisBlockHeight,
                     String genesisTxId,
                     Consumer<BsqBlock> newBlockHandler)
            throws BsqBlockchainException, BlockNotConnectingException {
        try {
            for (int blockHeight = startBlockHeight; blockHeight <= chainHeadHeight; blockHeight++) {
                long startTs = System.currentTimeMillis();
                Block btcdBlock = rpcService.requestBlock(blockHeight);
                List<Tx> bsqTxsInBlock = findBsqTxsInBlock(btcdBlock,
                        genesisBlockHeight,
                        genesisTxId);
                final BsqBlock bsqBlock = new BsqBlock(btcdBlock.getHeight(),
                        btcdBlock.getHash(),
                        btcdBlock.getPreviousBlockHash(),
                        ImmutableList.copyOf(bsqTxsInBlock));

                bsqChainState.addBlock(bsqBlock);
                newBlockHandler.accept(bsqBlock);
                log.info("parseBlock took {} ms at blockHeight {}; bsqTxsInBlock.size={}",
                        System.currentTimeMillis() - startTs, blockHeight, bsqTxsInBlock.size());
            }
        } catch (BlockNotConnectingException e) {
            throw e;
        } catch (Throwable t) {
            log.error(t.toString());
            t.printStackTrace();
            throw new BsqBlockchainException(t);
        }
    }

    private List<Tx> findBsqTxsInBlock(Block btcdBlock,
                                       int genesisBlockHeight,
                                       String genesisTxId)
            throws BsqBlockchainException {

        int blockHeight = btcdBlock.getHeight();
        log.debug("Parse block at height={} ", blockHeight);

        // check if the new block is the same chain we have built on.
        List<Tx> txList = new ArrayList<>();
        // We use a list as we want to maintain sorting of tx intra-block dependency
        List<Tx> bsqTxsInBlock = new ArrayList<>();
        // We add all transactions to the block
        long startTs = System.currentTimeMillis();
        for (String txId : btcdBlock.getTx()) {
            if (requestFee)
                rpcService.requestFees(txId, blockHeight, feesByBlock);

            final Tx tx = rpcService.requestTx(txId, blockHeight);
            txList.add(tx);
            checkForGenesisTx(genesisBlockHeight, genesisTxId, blockHeight, bsqTxsInBlock, tx);
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Parse when requested from new block arrived handler (rpc) 
    ///////////////////////////////////////////////////////////////////////////////////////////

    BsqBlock parseBlock(Block btcdBlock, int genesisBlockHeight, String genesisTxId)
            throws BsqBlockchainException, BlockNotConnectingException {
        List<Tx> bsqTxsInBlock = findBsqTxsInBlock(btcdBlock,
                genesisBlockHeight,
                genesisTxId);
        final BsqBlock bsqBlock = new BsqBlock(btcdBlock.getHeight(),
                btcdBlock.getHash(),
                btcdBlock.getPreviousBlockHash(),
                ImmutableList.copyOf(bsqTxsInBlock));
        bsqChainState.addBlock(bsqBlock);
        return bsqBlock;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Generic 
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void checkForGenesisTx(int genesisBlockHeight,
                                   String genesisTxId,
                                   int blockHeight,
                                   List<Tx> bsqTxsInBlock,
                                   Tx tx) {
        if (tx.getId().equals(genesisTxId) && blockHeight == genesisBlockHeight) {
            tx.getOutputs().stream().forEach(txOutput -> {
                txOutput.setUnspent(true);
                txOutput.setVerified(true);
                bsqChainState.addUnspentTxOutput(txOutput);
            });
            tx.setTxType(TxType.GENESIS);

            bsqChainState.setGenesisTx(tx);
            bsqChainState.addTxToMap(tx);
            bsqTxsInBlock.add(tx);
        }
    }

    // Performance-wise the recursion does not hurt (e.g. 5-20 ms). 
    // The RPC requestTransaction is the bottleneck.  
    private void recursiveFindBsqTxs(List<Tx> bsqTxsInBlock,
                                     List<Tx> transactions,
                                     int blockHeight,
                                     int recursionCounter,
                                     int maxRecursions) {
        // The set of txIds of txs which are used for inputs of another tx in same block
        Set<String> intraBlockSpendingTxIdSet = getIntraBlockSpendingTxIdSet(transactions);

        List<Tx> txsWithoutInputsFromSameBlock = new ArrayList<>();
        List<Tx> txsWithInputsFromSameBlock = new ArrayList<>();

        // First we find the txs which have no intra-block inputs
        outerLoop:
        for (Tx tx : transactions) {
            for (TxInput input : tx.getInputs()) {
                if (intraBlockSpendingTxIdSet.contains(input.getTxId())) {
                    // We have an input from one of the intra-block-transactions, so we cannot process that tx now.
                    // We add the tx for later parsing to the txsWithInputsFromSameBlock and move to the next tx.
                    txsWithInputsFromSameBlock.add(tx);
                    continue outerLoop;
                }
            }
            // If we have not found any tx input pointing to anther tx in the same block we add it to our
            // txsWithoutInputsFromSameBlock.
            txsWithoutInputsFromSameBlock.add(tx);
        }
        checkArgument(txsWithInputsFromSameBlock.size() + txsWithoutInputsFromSameBlock.size() == transactions.size(),
                "txsWithInputsFromSameBlock.size + txsWithoutInputsFromSameBlock.size != transactions.size");

        // Usual values is up to 25
        // There are some blocks where it seems devs have tested graphs of many depending txs, but even 
        // those dont exceed 200 recursions and are mostly old blocks from 2012 when fees have been low ;-).
        // TODO check strategy btc core uses (sorting the dependency graph would be an optimisation)
        // Seems btc core delivers tx list sorted by dependency graph. -> TODO verify and test
        if (recursionCounter > 1000) {
            log.warn("Unusual high recursive calls at resolveConnectedTxs. recursionCounter=" + recursionCounter);
            log.warn("blockHeight=" + blockHeight);
            log.warn("txsWithoutInputsFromSameBlock.size " + txsWithoutInputsFromSameBlock.size());
            log.warn("txsWithInputsFromSameBlock.size " + txsWithInputsFromSameBlock.size());
            //  log.warn("txsWithInputsFromSameBlock " + txsWithInputsFromSameBlock.stream().map(e->e.getId()).collect(Collectors.toList()));
        }

        // we check if we have any valid BSQ from that tx set
        bsqTxsInBlock.addAll(txsWithoutInputsFromSameBlock.stream()
                .filter(tx -> isBsqTx(blockHeight, tx))
                .collect(Collectors.toList()));

        log.debug("Parsing of all txsWithoutInputsFromSameBlock is done.");

        // we check if we have any valid BSQ utxo from that tx set
        // We might have InputsFromSameBlock which are BTC only but not BSQ, so we cannot 
        // optimize here and need to iterate further.
        if (!txsWithInputsFromSameBlock.isEmpty()) {
            if (recursionCounter < maxRecursions) {
                recursiveFindBsqTxs(bsqTxsInBlock, txsWithInputsFromSameBlock, blockHeight,
                        ++recursionCounter, maxRecursions);
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

    private boolean isBsqTx(int blockHeight, Tx tx) {
        boolean isBsqTx = false;
        long availableValue = 0;
        for (int inputIndex = 0; inputIndex < tx.getInputs().size(); inputIndex++) {
            TxInput input = tx.getInputs().get(inputIndex);
            Optional<TxOutput> spendableTxOutput = bsqChainState.getSpendableTxOutput(input.getTxIdIndexTuple());
            if (spendableTxOutput.isPresent()) {
                final TxOutput spentTxOutput = spendableTxOutput.get();
                spentTxOutput.setUnspent(false);
                bsqChainState.removeUnspentTxOutput(spentTxOutput);
                spentTxOutput.setSpentInfo(new SpentInfo(blockHeight, tx.getId(), inputIndex));
                input.setConnectedTxOutput(spentTxOutput);
                availableValue = availableValue + spentTxOutput.getValue();
            }
        }
        // If we have an input with BSQ we iterate the outputs
        if (availableValue > 0) {
            bsqChainState.addTxToMap(tx);
            isBsqTx = true;

            // We use order of output index. An output is a BSQ utxo as long there is enough input value
            final List<TxOutput> outputs = tx.getOutputs();
            TxOutput btcOutput = null;
            TxOutput bsqOutput = null;
            for (int index = 0; index < outputs.size(); index++) {
                TxOutput txOutput = outputs.get(index);
                final long txOutputValue = txOutput.getValue();
                // We ignore OP_RETURN outputs with txOutputValue 0
                if (availableValue >= txOutputValue && txOutputValue != 0) {
                    // We are spending available tokens
                    txOutput.setVerified(true);
                    txOutput.setUnspent(true);
                    bsqChainState.addUnspentTxOutput(txOutput);
                    tx.setTxType(TxType.TRANSFER_BSQ);
                    txOutput.setTxOutputType(TxOutputType.BSQ_OUTPUT);
                    bsqOutput = txOutput;

                    availableValue -= txOutputValue;
                    if (availableValue == 0) {
                        log.debug("We don't have anymore BSQ to spend");
                    }
                } else if (opReturnVerification.maybeProcessOpReturnData(tx, index, availableValue, blockHeight, btcOutput, bsqOutput)) {
                    log.debug("We processed valid DAO OP_RETURN data");
                } else {
                    btcOutput = txOutput;
                    txOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
                    // The other outputs are not BSQ outputs so we skip them but we
                    // jump to the last output as that might be an OP_RETURN with DAO data
                    index = Math.max(index, outputs.size() - 2);
                }
            }

            if (availableValue > 0) {
                log.debug("BSQ have been left which was not spent. Burned BSQ amount={}, tx={}",
                        availableValue,
                        tx.toString());
                tx.setBurntFee(availableValue);
                if (tx.getTxType() == null)
                    tx.setTxType(TxType.PAY_TRADE_FEE);
            }
        } else if (issuanceVerification.maybeProcessData(tx)) {
            // We don't have any BSQ input, so we test if it is a sponsor/issuance tx
            log.debug("We got a issuance tx and process the data");
        }

        return isBsqTx;
    }

    private Set<String> getIntraBlockSpendingTxIdSet(List<Tx> txs) {
        Set<String> txIdSet = txs.stream().map(Tx::getId).collect(Collectors.toSet());
        Set<String> intraBlockSpendingTxIdSet = new HashSet<>();
        txs.stream()
                .forEach(tx -> tx.getInputs().stream()
                        .filter(input -> txIdSet.contains(input.getTxId()))
                        .forEach(input -> intraBlockSpendingTxIdSet.add(input.getTxId())));
        return intraBlockSpendingTxIdSet;
    }
}
