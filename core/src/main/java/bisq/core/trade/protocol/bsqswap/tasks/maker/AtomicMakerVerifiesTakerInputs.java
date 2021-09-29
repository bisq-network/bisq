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

package bisq.core.trade.protocol.bsqswap.tasks.maker;

import bisq.core.trade.messages.bsqswap.CreateAtomicTxRequest;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.tasks.AtomicTradeTask;

import bisq.asset.BitcoinAddressValidator;

import bisq.common.config.Config;
import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class AtomicMakerVerifiesTakerInputs extends AtomicTradeTask {
    @SuppressWarnings({"unused"})
    public AtomicMakerVerifiesTakerInputs(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
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
             */

            var message = (CreateAtomicTxRequest) bsqSwapProtocolModel.getTradeMessage();

            checkArgument(new BitcoinAddressValidator(Config.baseCurrencyNetworkParameters()).validate(
                    message.getTakerBsqOutputAddress()).isValid(),
                    "Failed to validate taker BSQ outputs address");
            checkArgument(bsqSwapProtocolModel.makerPreparesMakerSide(), "Failed to prepare maker inputs");

            // Inputs
            var makerBsqInputAmount = bsqSwapProtocolModel.getBsqWalletService().getBsqRawInputAmount(
                    bsqSwapProtocolModel.getRawMakerBsqInputs(), bsqSwapProtocolModel.getDaoFacade());
            var takerBsqInputAmount = bsqSwapProtocolModel.getBsqWalletService().getBsqRawInputAmount(
                    message.getTakerBsqInputs(), bsqSwapProtocolModel.getDaoFacade());
            var makerBtcInputAmount = bsqSwapProtocolModel.getBtcWalletService().getBtcRawInputAmount(
                    bsqSwapProtocolModel.getRawMakerBtcInputs());
            var takerBtcInputAmount = bsqSwapProtocolModel.getBtcWalletService().getBtcRawInputAmount(
                    bsqSwapProtocolModel.getRawTakerBtcInputs());

            // Outputs
            var makerBsqOutputAmount = bsqSwapProtocolModel.getMakerBsqOutputAmount();
            var takerBsqOutputAmount = bsqSwapProtocolModel.getTakerBsqOutputAmount();
            var makerBtcOutputAmount = bsqSwapProtocolModel.getMakerBtcOutputAmount();
            var takerBtcOutputAmount = bsqSwapProtocolModel.getTakerBtcOutputAmount();

            // Trade fee
            var bsqTradeFeeAmount = bsqSwapProtocolModel.getBsqMakerTradeFee() + bsqSwapProtocolModel.getBsqTakerTradeFee();

            // Verify input sum equals output sum
            var bsqIn = takerBsqInputAmount + makerBsqInputAmount;
            var bsqOut = takerBsqOutputAmount + makerBsqOutputAmount;
            checkArgument(bsqIn - bsqOut == bsqTradeFeeAmount, "BSQ in does not match BSQ out");

            var btcIn = takerBtcInputAmount + makerBtcInputAmount + bsqTradeFeeAmount;
            var btcOut = takerBtcOutputAmount + makerBtcOutputAmount;
            bsqSwapProtocolModel.setTxFee(btcIn - btcOut);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
