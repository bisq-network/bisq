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

import io.bisq.core.dao.blockchain.WriteModel;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import io.bisq.core.dao.blockchain.vo.TxOutputType;
import io.bisq.core.dao.blockchain.vo.TxType;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Checks if an output is a BSQ output and apply state change.
 */

@Slf4j
public class TxOutputVerification {
    private final WriteModel writeModel;
    private final OpReturnVerification opReturnVerification;

    @Inject
    public TxOutputVerification(WriteModel writeModel, OpReturnVerification opReturnVerification) {
        this.writeModel = writeModel;
        this.opReturnVerification = opReturnVerification;
    }

    void verify(Tx tx,
                TxOutput txOutput,
                int index,
                int blockHeight,
                BsqTxVerification.BsqInputBalance bsqInputBalance,
                TxOutputsVerification.MutableState mutableState) {

        final long txOutputValue = txOutput.getValue();
        final long bsqInputBalanceValue = bsqInputBalance.getValue();
        if (bsqInputBalance.isPositive()) {
            // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
            if (txOutput.getOpReturnData() == null) {
                if (bsqInputBalanceValue >= txOutputValue) {
                    // We have enough BSQ in the inputs to fund that output. Update the input balance.
                    bsqInputBalance.subtract(txOutputValue);

                    // We apply the state change for the output.
                    applyStateChangeForBsqOutput(txOutput);

                    // We don't know for sure the tx type before we are finished with the iterations. It might get changed in
                    // the OP_RETURN verification or after iteration if we have left over remaining BSQ which gets
                    // burned. That would mark a PAY_TRADE_FEE. A normal TRANSFER_BSQ tx will not have a fee and no
                    // OP_RETURN, so we set that and if not overwritten later it will stay.
                    tx.setTxType(TxType.TRANSFER_BSQ);

                    // We store the output as BSQ output for use in further iterations.
                    mutableState.setBsqOutput(txOutput);
                } else if (bsqInputBalance.isPositive() && mutableState.getCompRequestIssuanceOutputCandidate() == null) {
                    // We don't know yes if the tx is a compensation request tx as that will be detected in the last
                    // output which is a OP_RETURN output. We store that output for later use at the OP_RETURN
                    // verification.
                    mutableState.setCompRequestIssuanceOutputCandidate(txOutput);
                    log.debug("BTC output of BSQ issuance output from a compensation request tx");

                    // As we have not verified the OP_RETURN yet we set it temporary to BTC_OUTPUT so we cover the case
                    // if it was normal tx with a non BSQ OP_RETURN output.
                    applyStateChangeForBtcOutput(txOutput);
                } else {
                    applyStateChangeForBtcOutput(txOutput);
                    log.debug("We got a BTC output.");
                }
            } else {
                // We got a OP_RETURN output.
                opReturnVerification.process(txOutput, tx, index, bsqInputBalanceValue, blockHeight, mutableState);
            }

        } else {
            log.debug("We don't have any BSQ available anymore.");
            checkArgument(bsqInputBalance.isZero(), "bsqInputBalanceValue must not be negative");
        }
    }

    private void applyStateChangeForBsqOutput(TxOutput txOutput) {
        txOutput.setVerified(true);
        txOutput.setUnspent(true);
        txOutput.setTxOutputType(TxOutputType.BSQ_OUTPUT);
        writeModel.addUnspentTxOutput(txOutput);
    }

    private void applyStateChangeForBtcOutput(TxOutput txOutput) {
        txOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
    }
}
