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

package io.bisq.core.dao.node;

import io.bisq.common.app.DevEnv;
import io.bisq.core.dao.blockchain.BsqBlockChain;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxInput;
import io.bisq.core.dao.node.consensus.BsqTxVerification;
import io.bisq.core.dao.node.consensus.GenesisTxVerification;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;


/**
 * Base class for lite node parser and full node parser. Iterates blocks to find BSQ relevant transactions.
 *
 * We are in threaded context. Don't mix up with UserThread.
 */
@Slf4j
@Immutable
public abstract class BsqParser {
    protected final BsqBlockChain bsqBlockChain;
    private final GenesisTxVerification genesisTxVerification;
    private final BsqTxVerification bsqTxVerification;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqParser(BsqBlockChain bsqBlockChain,
                     GenesisTxVerification genesisTxVerification,
                     BsqTxVerification bsqTxVerification) {
        this.bsqBlockChain = bsqBlockChain;
        this.genesisTxVerification = genesisTxVerification;
        this.bsqTxVerification = bsqTxVerification;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void checkForGenesisTx(int blockHeight,
                                     List<Tx> bsqTxsInBlock,
                                     Tx tx) {
        if (genesisTxVerification.isGenesisTx(tx, blockHeight)) {
            genesisTxVerification.applyStateChange(tx);
            bsqTxsInBlock.add(tx);
        }
    }

    // Performance-wise the recursion does not hurt (e.g. 5-20 ms).
    // The RPC requestTransaction is the bottleneck.
    protected void recursiveFindBsqTxs(List<Tx> bsqTxsInBlock,
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
        // There are some blocks where it seems developers have tested graphs of many depending txs, but even
        // those don't exceed 200 recursions and are mostly old blocks from 2012 when fees have been low ;-).
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
                .filter(tx -> bsqTxVerification.isBsqTx(blockHeight, tx))
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

    private Set<String> getIntraBlockSpendingTxIdSet(List<Tx> txs) {
        Set<String> txIdSet = txs.stream().map(Tx::getId).collect(Collectors.toSet());
        Set<String> intraBlockSpendingTxIdSet = new HashSet<>();
        txs.forEach(tx -> tx.getInputs().stream()
                .filter(input -> txIdSet.contains(input.getTxId()))
                .forEach(input -> intraBlockSpendingTxIdSet.add(input.getTxId())));
        return intraBlockSpendingTxIdSet;
    }
}
