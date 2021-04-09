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

import bisq.core.trade.DonationAddressValidation;
import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.atomic.messages.CreateAtomicTxResponse;
import bisq.core.trade.protocol.tasks.AtomicTradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.TransactionInput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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

            checkArgument(!atomicProcessModel.getOffer().isMyOffer(atomicProcessModel.getKeyRing()), "must not take own offer");
            var isBuyer = !atomicProcessModel.getOffer().isBuyOffer();

            if (!(atomicProcessModel.getTradeMessage() instanceof CreateAtomicTxResponse))
                failed("Expected CreateAtomicTxResponse");


            var serializedAtomicTx = ((CreateAtomicTxResponse) atomicProcessModel.getTradeMessage()).getAtomicTx();
            var atomicTx = atomicProcessModel.getBtcWalletService().getTxFromSerializedTx(serializedAtomicTx);

            var inputsSize = atomicTx.getInputs().size();
            var outputsSize = atomicTx.getOutputs().size();

            // Verify taker inputs are added as expected
            int inputIndex = 0;
            checkNotNull(atomicProcessModel.getRawTakerBsqInputs(), "Taker BSQ inputs must not be null");
            List<TransactionInput> atomicTxInputs = new ArrayList<>();
            for (var rawInput : atomicProcessModel.getRawTakerBsqInputs()) {
                var verifiedInput = atomicProcessModel.getBsqWalletService().verifyTransactionInput(
                        atomicTx.getInput(inputIndex), rawInput);
                if (verifiedInput == null)
                    failed("Taker BSQ input mismatch");
                atomicTxInputs.add(verifiedInput);
                inputIndex++;
                if (inputIndex > inputsSize)
                    failed("Not enough inputs");
            }
            if (atomicProcessModel.getRawTakerBtcInputs() != null) {
                for (var rawInput : atomicProcessModel.getRawTakerBtcInputs()) {
                    var verifiedInput = atomicProcessModel.getTradeWalletService().verifyTransactionInput(
                            atomicTx.getInput(inputIndex), rawInput);
                    if (verifiedInput == null)
                        failed("Taker BTC input mismatch");
                    atomicTxInputs.add(verifiedInput);
                    inputIndex++;
                    if (inputIndex > inputsSize)
                        failed("Not enough inputs");
                }
            }
            List<TransactionInput> makerInputs = new ArrayList<>();
            for (; inputIndex < atomicTx.getInputs().size(); inputIndex++) {
                makerInputs.add(atomicTx.getInput(inputIndex));
                atomicTxInputs.add(atomicTx.getInput(inputIndex));
            }

            atomicTx.clearInputs();
            atomicTxInputs.forEach(atomicTx::addInput);

            // Verify makerInputs are not mine
            if (makerInputs.stream().anyMatch(this::isMine))
                failed("Maker input must not me mine");

            var makerBsqInputAmount =
                    atomicProcessModel.getBsqWalletService().getConfirmedBsqInputAmount(makerInputs, atomicProcessModel.getDaoFacade());

            var expectedBsqTradeAmount = atomicProcessModel.getBsqTradeAmount();
            var expectedMakerBsqOutAmount = isBuyer ? atomicProcessModel.getBsqTradeAmount() :
                    makerBsqInputAmount - expectedBsqTradeAmount;
            checkArgument(expectedMakerBsqOutAmount >= 0, "Maker BSQ input amount too low");
            var expectedTakerBsqOutAmount = atomicProcessModel.getTakerBsqOutputAmount();
            var expectedBsqTradeFeeAmount = atomicProcessModel.getBsqTradeFee();
            var expectedBtcTradeFee = atomicProcessModel.getBtcTradeFee();
            var expectedTakerBtcAmount = atomicProcessModel.getTakerBtcOutputAmount();

            // Get BSQ and BTC input amounts
            var bsqInputAmount = atomicProcessModel.getBsqWalletService().getConfirmedBsqInputAmount(
                    atomicTx.getInputs(), atomicProcessModel.getDaoFacade());
            if (expectedBsqTradeFeeAmount + expectedTakerBsqOutAmount + expectedMakerBsqOutAmount != bsqInputAmount)
                failed("Unexpected BSQ input amount");

            var takerBtcOutputIndex = 0;
            if (expectedMakerBsqOutAmount > 0)
                takerBtcOutputIndex++;
            if (expectedTakerBsqOutAmount > 0) {
                takerBtcOutputIndex++;
                // Verify taker BSQ output, always the output before taker BTC output
                var takerBsqOutput = atomicTx.getOutput(takerBtcOutputIndex - 1);
                var takerBsqOutputAddressInBtcFormat = Objects.requireNonNull(
                        takerBsqOutput.getAddressFromP2PKHScript(atomicProcessModel.getBtcWalletService().getParams()));
                var takerBsqOutputAddress = atomicProcessModel.getBsqWalletService().getBsqFormatter().
                        getBsqAddressStringFromAddress(takerBsqOutputAddressInBtcFormat);
                if (!takerBsqOutputAddress.equals(atomicProcessModel.getTakerBsqAddress()))
                    failed("Taker BSQ address mismatch");
                if (expectedTakerBsqOutAmount != takerBsqOutput.getValue().getValue())
                    failed("Taker BSQ amount mismatch");
            }

            // Verify taker BTC output (vout index depends on the number of BSQ outputs, as calculated above)
            var takerBtcOutput = atomicTx.getOutput(takerBtcOutputIndex);
            var takerBtcOutputAddress = Objects.requireNonNull(takerBtcOutput.getAddressFromP2PKHScript(
                    atomicProcessModel.getBtcWalletService().getParams())).toString();
            if (!takerBtcOutputAddress.equals(atomicProcessModel.getTakerBtcAddress()))
                failed("Taker BTC output address mismatch");
            if (expectedTakerBtcAmount != takerBtcOutput.getValue().getValue())
                failed("Taker BTC output amount mismatch");

            if (expectedBtcTradeFee > 0) {
                var tradeFeeOutput = atomicTx.getOutput(outputsSize - 1);
                DonationAddressValidation.validateDonationAddress(tradeFeeOutput, atomicTx, atomicProcessModel.getDaoFacade(),
                        atomicProcessModel.getBtcWalletService());
                if (expectedBtcTradeFee != tradeFeeOutput.getValue().getValue())
                    failed("Unexpected trade fee amount");
            }

            atomicProcessModel.setVerifiedAtomicTx(atomicTx);
            atomicProcessModel.setAtomicTx(atomicTx.bitcoinSerialize());
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private boolean isMine(TransactionInput input) {
        var result = new AtomicBoolean(false);
        var walletServices = Arrays.asList(atomicProcessModel.getBtcWalletService(), atomicProcessModel.getBsqWalletService());

        walletServices.forEach(walletService ->
                walletService.getWallet().getWalletTransactions().forEach(tx -> {
                    if (input.getOutpoint().getHash().toString().equals(tx.getTransaction().getHashAsString())) {
                        var connectedOutput = tx.getTransaction().getOutput(input.getOutpoint().getIndex());
                        if (walletService.isMine(connectedOutput))
                            result.set(true);
                    }
                })
        );
        return result.get();
    }
}
