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

package bisq.core.trade.protocol.bsqswap.tasks.taker;

import bisq.core.trade.messages.bsqswap.CreateBsqSwapTxResponse;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.tasks.BsqSwapTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.TransactionInput;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class TakerVerifyBsqSwapTx extends BsqSwapTask {

    @SuppressWarnings({"unused"})
    public TakerVerifyBsqSwapTx(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
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

            checkArgument(!bsqSwapProtocolModel.getOffer().isMyOffer(bsqSwapProtocolModel.getKeyRing()),
                    "must not take own offer");
            checkArgument(bsqSwapProtocolModel.getTradeMessage() instanceof CreateBsqSwapTxResponse,
                    "Expected CreateAtomicTxResponse");

            var message = (CreateBsqSwapTxResponse) bsqSwapProtocolModel.getTradeMessage();
            bsqSwapProtocolModel.updateFromMessage(message);

            var myTx = bsqSwapProtocolModel.createBsqSwapTx();
            var makerTx = bsqSwapProtocolModel.getBtcWalletService().getTxFromSerializedTx(message.getTx());
            // Strip sigs from maker tx and compare with myTx to make sure they are the same
            makerTx.getInputs().forEach(TransactionInput::clearScriptBytes);
            checkArgument(myTx.equals(makerTx), "Maker tx doesn't match my tx");

            // Verify BSQ input and output amounts match
            var bsqInputAmount = bsqSwapProtocolModel.getBsqWalletService().getConfirmedBsqInputAmount(
                    myTx.getInputs(), bsqSwapProtocolModel.getDaoFacade());
            checkArgument(bsqInputAmount ==
                            bsqSwapProtocolModel.getMakerBsqOutputAmount() +
                                    bsqSwapProtocolModel.getTakerBsqOutputAmount() +
                                    bsqSwapProtocolModel.getBsqMakerTradeFee() +
                                    bsqSwapProtocolModel.getBsqTakerTradeFee(),
                    "BSQ input doesn't match BSQ output amount");

            // Create signed tx and verify tx fee is not too low
            // Sign my inputs on myTx
            bsqSwapProtocolModel.getTradeWalletService().signInputs(myTx, bsqSwapProtocolModel.getRawTakerBtcInputs());
            bsqSwapProtocolModel.getBsqWalletService().signInputs(myTx, bsqSwapProtocolModel.getRawTakerBsqInputs());

            // Create fully signed atomic tx by combining signed inputs from maker tx and my tx
            makerTx = bsqSwapProtocolModel.getBtcWalletService().getTxFromSerializedTx(message.getTx());
            var signedTx = bsqSwapProtocolModel.createBsqSwapTx();
            var txFee = signedTx.getFee().getValue();
            signedTx.clearInputs();
            int index = 0;
            for (; index < bsqSwapProtocolModel.numTakerInputs(); ++index) {
                signedTx.addInput(myTx.getInput(index));
            }
            for (; index < bsqSwapProtocolModel.numTakerInputs() + bsqSwapProtocolModel.numMakerInputs(); ++index) {
                signedTx.addInput(makerTx.getInput(index));
            }

            checkArgument(txFee >= signedTx.getVsize() * bsqSwapProtocolModel.getTxFeePerVbyte(),
                    "Tx fee too low txFee={} vsize*fee={}", txFee,
                    signedTx.getVsize() * bsqSwapProtocolModel.getTxFeePerVbyte());

            bsqSwapProtocolModel.setVerifiedTransaction(signedTx);
            bsqSwapProtocolModel.setRawTx(signedTx.bitcoinSerialize());

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
