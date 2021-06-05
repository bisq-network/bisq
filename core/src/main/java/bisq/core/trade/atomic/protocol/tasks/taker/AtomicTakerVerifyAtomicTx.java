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

package bisq.core.trade.atomic.protocol.tasks.taker;

import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.atomic.messages.CreateAtomicTxResponse;
import bisq.core.trade.protocol.tasks.AtomicTradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.TransactionInput;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class AtomicTakerVerifyAtomicTx extends AtomicTradeTask {

    @SuppressWarnings({"unused"})
    public AtomicTakerVerifyAtomicTx(TaskRunner<AtomicTrade> taskHandler, AtomicTrade atomicTrade) {
        super(taskHandler, atomicTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            /* Tx output format:
             * At a minimum there will be 1 BSQ out and 1 BTC out
             * [0-1]   (Maker BSQ)
             * [0-1]   (Taker BSQ)
             * [0-1]   (Taker BTC)
             * [0-1]   (Maker BTC)
             * [0-1]   (BTCtradeFee)

             * Taker verifies:
             *   - Number of outputs
             *   - input vs output BSQ
             *   - first inputs are taker BSQ inputs
             *   - other inputs are not owned by taker
             *   - txFee
             *   - tradeFee
             *   - BSQ output to taker BSQ address
             *   - BTC output to taker BTC address
             */

            checkArgument(!atomicProcessModel.getOffer().isMyOffer(atomicProcessModel.getKeyRing()),
                    "must not take own offer");
            checkArgument(atomicProcessModel.getTradeMessage() instanceof CreateAtomicTxResponse,
                    "Expected CreateAtomicTxResponse");

            var message = (CreateAtomicTxResponse) atomicProcessModel.getTradeMessage();
            atomicProcessModel.updateFromMessage(message);

            var myTx = atomicProcessModel.createAtomicTx();
            var makerTx = atomicProcessModel.getBtcWalletService().getTxFromSerializedTx(message.getAtomicTx());
            // Strip sigs from maker tx and compare with myTx to make sure they are the same
            makerTx.getInputs().forEach(TransactionInput::clearScriptBytes);
            checkArgument(myTx.equals(makerTx), "Maker tx doesn't match my tx");

            // Verify BSQ input and output amounts match
            var bsqInputAmount = atomicProcessModel.getBsqWalletService().getConfirmedBsqInputAmount(
                    myTx.getInputs(), atomicProcessModel.getDaoFacade());
            checkArgument(bsqInputAmount ==
                            atomicProcessModel.getMakerBsqOutputAmount() +
                                    atomicProcessModel.getTakerBsqOutputAmount() +
                                    atomicProcessModel.getBsqMakerTradeFee() +
                                    atomicProcessModel.getBsqTakerTradeFee(),
                    "BSQ input doesn't match BSQ output amount");

            // Create signed tx and verify tx fee is not too low
            // Sign my inputs on myTx
            atomicProcessModel.getTradeWalletService().signInputs(myTx, atomicProcessModel.getRawTakerBtcInputs());
            atomicProcessModel.getBsqWalletService().signInputs(myTx, atomicProcessModel.getRawTakerBsqInputs());

            // Create fully signed atomic tx by combining signed inputs from maker tx and my tx
            makerTx = atomicProcessModel.getBtcWalletService().getTxFromSerializedTx(message.getAtomicTx());
            var signedTx = atomicProcessModel.createAtomicTx();
            var txFee = signedTx.getFee().getValue();
            signedTx.clearInputs();
            int index = 0;
            for (; index < atomicProcessModel.numTakerInputs(); ++index) {
                signedTx.addInput(myTx.getInput(index));
            }
            for (; index < atomicProcessModel.numTakerInputs() + atomicProcessModel.numMakerInputs(); ++index) {
                signedTx.addInput(makerTx.getInput(index));
            }

            checkArgument(txFee >= signedTx.getVsize() * atomicProcessModel.getTxFeePerVbyte(),
                    "Tx fee too low txFee={} vsize*fee={}", txFee,
                    signedTx.getVsize() * atomicProcessModel.getTxFeePerVbyte());

            atomicProcessModel.setVerifiedAtomicTx(signedTx);
            atomicProcessModel.setAtomicTx(signedTx.bitcoinSerialize());

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
