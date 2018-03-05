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

import com.google.common.annotations.VisibleForTesting;
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


/**
 * Parses blocks and transactions for finding BSQ relevant transactions.
 * <p/>
 * We are in threaded context. Don't mix up with UserThread.
 */
@Slf4j
@Immutable
public class BsqParser {
    private final BsqBlockChain bsqBlockChain;
    private final OpReturnVerification opReturnVerification;
    private final IssuanceVerification issuanceVerification;
    private final RpcService rpcService;

    // Maybe we want to request fee at some point, leave it for now and disable it
    private boolean requestFee = false;
    private final Map<Integer, Long> feesByBlock = new HashMap<>();


    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqParser(RpcService rpcService,
                     BsqBlockChain bsqBlockChain,
                     OpReturnVerification opReturnVerification,
                     IssuanceVerification issuanceVerification) {
        this.rpcService = rpcService;
        this.bsqBlockChain = bsqBlockChain;
        this.opReturnVerification = opReturnVerification;
        this.issuanceVerification = issuanceVerification;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Parsing with data delivered with BsqBlock list
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO check with similar code at BsqLiteNodeExecutor
    void parseBsqBlocks(List<BsqBlock> bsqBlocks,
                        int genesisBlockHeight,
                        String genesisTxId,
                        Consumer<BsqBlock> newBlockHandler)
            throws BlockNotConnectingException {
        for (BsqBlock bsqBlock : bsqBlocks) {
            parseBsqBlock(bsqBlock,
                    genesisBlockHeight,
                    genesisTxId);
            bsqBlockChain.addBlock(bsqBlock);
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

    @VisibleForTesting
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

                bsqBlockChain.addBlock(bsqBlock);
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
        bsqBlockChain.addBlock(bsqBlock);
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
                bsqBlockChain.addUnspentTxOutput(txOutput);
            });
            tx.setTxType(TxType.GENESIS);

            bsqBlockChain.setGenesisTx(tx);
            bsqBlockChain.addTxToMap(tx);
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
                if (DevEnv.isDevMode())
                    throw new RuntimeException(msg);
            }
        } else {
            log.debug("We have no more txsWithInputsFromSameBlock.");
        }
    }

    @VisibleForTesting
    boolean isBsqTx(int blockHeight, Tx tx) {
        return bsqBlockChain.<Boolean>callFunctionWithWriteLock(() -> doIsBsqTx(blockHeight, tx));
    }

    // Not thread safe wrt bsqBlockChain
    // Check if any of the inputs are BSQ inputs and update BsqBlockChain state accordingly
    private boolean doIsBsqTx(int blockHeight, Tx tx) {
        boolean isBsqTx = false;
        long availableBsqFromInputs = 0;
        // For each input in tx
        for (int inputIndex = 0; inputIndex < tx.getInputs().size(); inputIndex++) {
            availableBsqFromInputs += getBsqFromInput(blockHeight, tx, inputIndex);
        }

        // If we have an input with BSQ we iterate the outputs
        if (availableBsqFromInputs > 0) {
            bsqBlockChain.addTxToMap(tx);
            isBsqTx = true;

            // We use order of output index. An output is a BSQ utxo as long there is enough input value
            final List<TxOutput> outputs = tx.getOutputs();
            TxOutput compRequestIssuanceOutputCandidate = null;
            TxOutput bsqOutput = null;
            for (int index = 0; index < outputs.size(); index++) {
                TxOutput txOutput = outputs.get(index);
                final long txOutputValue = txOutput.getValue();

                // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
                if (txOutput.getOpReturnData() == null) {
                    if (availableBsqFromInputs >= txOutputValue && txOutputValue != 0) {
                        // We are spending available tokens
                        markOutputAsBsq(txOutput, tx);
                        availableBsqFromInputs -= txOutputValue;
                        bsqOutput = txOutput;
                        if (availableBsqFromInputs == 0)
                            log.debug("We don't have anymore BSQ to spend");
                    } else if (availableBsqFromInputs > 0 && compRequestIssuanceOutputCandidate == null) {
                        // availableBsq must be > 0 as we expect a bsqFee for an compRequestIssuanceOutput
                        // We store the btc output as it might be the issuance output from a compensation request which might become BSQ after voting.
                        compRequestIssuanceOutputCandidate = txOutput;
                        // As we have not verified the OP_RETURN yet we set it temporary to BTC_OUTPUT
                        txOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);

                        // The other outputs cannot be BSQ outputs so we ignore them.
                        // We set the index directly to the last output as that might be an OP_RETURN with DAO data
                        //TODO remove because its premature optimisation....
                        // index = Math.max(index, outputs.size() - 2);
                    } else {
                        log.debug("We got another BTC output. We ignore it.");
                    }
                } else {
                    // availableBsq is used as bsqFee paid to miners (burnt) if OP-RETURN is used
                    opReturnVerification.processDaoOpReturnData(tx, index, availableBsqFromInputs, blockHeight, compRequestIssuanceOutputCandidate, bsqOutput);
                }
            }

            if (availableBsqFromInputs > 0) {
                log.debug("BSQ have been left which was not spent. Burned BSQ amount={}, tx={}",
                        availableBsqFromInputs,
                        tx.toString());
                tx.setBurntFee(availableBsqFromInputs);
                if (tx.getTxType() == null)
                    tx.setTxType(TxType.PAY_TRADE_FEE);
            }
        } else if (issuanceVerification.maybeProcessData(tx)) {
            // We don't have any BSQ input, so we test if it is a sponsor/issuance tx
            log.debug("We got a issuance tx and process the data");
        }

        return isBsqTx;
    }

    // Not thread safe wrt bsqBlockChain
    private long getBsqFromInput(int blockHeight, Tx tx, int inputIndex) {
        long bsqFromInput = 0;
        TxInput input = tx.getInputs().get(inputIndex);
        // TODO check if Tuple indexes of inputs outputs are not messed up...
        // Get spendable BSQ output for txidindextuple... (get output used as input in tx if it's spendable BSQ)
        Optional<TxOutput> spendableTxOutput = bsqBlockChain.getSpendableTxOutput(input.getTxIdIndexTuple());
        if (spendableTxOutput.isPresent()) {
            // The output is BSQ, set it as spent, update bsqBlockChain and add to available BSQ for this tx
            final TxOutput spentTxOutput = spendableTxOutput.get();
            spentTxOutput.setUnspent(false);
            bsqBlockChain.removeUnspentTxOutput(spentTxOutput);
            spentTxOutput.setSpentInfo(new SpentInfo(blockHeight, tx.getId(), inputIndex));
            input.setConnectedTxOutput(spentTxOutput);
            bsqFromInput = spentTxOutput.getValue();
        }
        return bsqFromInput;
    }

    // Not thread safe wrt bsqBlockChain
    private void markOutputAsBsq(TxOutput txOutput, Tx tx) {
        // We are spending available tokens
        txOutput.setVerified(true);
        txOutput.setUnspent(true);
        txOutput.setTxOutputType(TxOutputType.BSQ_OUTPUT);
        tx.setTxType(TxType.TRANSFER_BSQ);
        bsqBlockChain.addUnspentTxOutput(txOutput);
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
