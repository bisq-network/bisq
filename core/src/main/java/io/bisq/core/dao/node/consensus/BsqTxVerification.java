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

package io.bisq.core.dao.node.consensus;

import io.bisq.core.dao.blockchain.BsqBlockChain;
import io.bisq.core.dao.blockchain.vo.*;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

/**
 * Verifies if a given transaction is a BSQ transaction.
 */
@Slf4j
public class BsqTxVerification {

    private final BsqBlockChain bsqBlockChain;
    private final OpReturnVerification opReturnVerification;
    private final IssuanceVerification issuanceVerification;

    @Inject
    public BsqTxVerification(BsqBlockChain bsqBlockChain,
                             OpReturnVerification opReturnVerification,
                             IssuanceVerification issuanceVerification) {
        this.bsqBlockChain = bsqBlockChain;
        this.opReturnVerification = opReturnVerification;
        this.issuanceVerification = issuanceVerification;
    }

    public boolean verify(int blockHeight, Tx tx) {
        return bsqBlockChain.<Boolean>callFunctionWithWriteLock(() -> doVerify(blockHeight, tx));
    }

    // Not thread safe wrt bsqBlockChain
    // Check if any of the inputs are BSQ inputs and update BsqBlockChain state accordingly
    private boolean doVerify(int blockHeight, Tx tx) {
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
                    opReturnVerification.process(tx, index, availableBsqFromInputs, blockHeight, compRequestIssuanceOutputCandidate, bsqOutput);
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
        // Get spendable BSQ output for txIdIndexTuple... (get output used as input in tx if it's spendable BSQ)
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
}
