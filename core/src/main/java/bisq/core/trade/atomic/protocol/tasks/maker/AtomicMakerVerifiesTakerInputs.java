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

package bisq.core.trade.atomic.protocol.tasks.maker;

import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.atomic.messages.CreateAtomicTxRequest;
import bisq.core.trade.protocol.tasks.AtomicTradeTask;

import bisq.asset.BitcoinAddressValidator;

import bisq.common.config.Config;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class AtomicMakerVerifiesTakerInputs extends AtomicTradeTask {
    @SuppressWarnings({"unused"})
    public AtomicMakerVerifiesTakerInputs(TaskRunner<AtomicTrade> taskHandler, AtomicTrade atomicTrade) {
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
             */

            var message = (CreateAtomicTxRequest) atomicProcessModel.getTradeMessage();

            atomicProcessModel.getAtomicTxBuilder().setMyTradeFee(atomicTrade.isCurrencyForMakerFeeBtc(),
                    Coin.valueOf(atomicTrade.getMakerFee()));
            atomicProcessModel.getAtomicTxBuilder().setPeerTradeFee(atomicTrade.isCurrencyForTakerFeeBtc(),
                    Coin.valueOf(atomicTrade.getTakerFee()));

            checkArgument(new BitcoinAddressValidator(Config.baseCurrencyNetworkParameters()).validate(
                    message.getTakerBsqOutputAddress()).isValid(),
                    "Failed to validate taker BSQ outputs address");
            checkArgument(atomicProcessModel.makerPreparesMakerSide(), "Failed to prepare maker inputs");

            // Inputs
            var makerBsqInputAmount = atomicProcessModel.getBsqWalletService().getBsqRawInputAmount(
                    atomicProcessModel.getRawMakerBsqInputs(), atomicProcessModel.getDaoFacade());
            var takerBsqInputAmount = atomicProcessModel.getBsqWalletService().getBsqRawInputAmount(
                    message.getTakerBsqInputs(), atomicProcessModel.getDaoFacade());
            var makerBtcInputAmount = atomicProcessModel.getBtcWalletService().getBtcRawInputAmount(
                    atomicProcessModel.getRawMakerBtcInputs());
            var takerBtcInputAmount = atomicProcessModel.getBtcWalletService().getBtcRawInputAmount(
                    atomicProcessModel.getRawTakerBtcInputs());

            // Outputs
            var makerBsqOutputAmount = atomicProcessModel.getMakerBsqOutputAmount();
            var takerBsqOutputAmount = atomicProcessModel.getTakerBsqOutputAmount();
            var makerBtcOutputAmount = atomicProcessModel.getMakerBtcOutputAmount();
            var takerBtcOutputAmount = atomicProcessModel.getTakerBtcOutputAmount();

            // Trade fee
            var btcTradeFeeAmount = atomicProcessModel.getBtcMakerTradeFee() + atomicProcessModel.getBtcTakerTradeFee();
            var bsqTradeFeeAmount = atomicProcessModel.getBsqMakerTradeFee() + atomicProcessModel.getBsqTakerTradeFee();

            // Verify input sum equals output sum
            var bsqIn = takerBsqInputAmount + makerBsqInputAmount;
            var bsqOut = takerBsqOutputAmount + makerBsqOutputAmount;
            checkArgument(bsqIn - bsqOut == bsqTradeFeeAmount, "BSQ in does not match BSQ out");

            var btcIn = takerBtcInputAmount + makerBtcInputAmount + bsqTradeFeeAmount;
            var btcOut = takerBtcOutputAmount + makerBtcOutputAmount + btcTradeFeeAmount;
            atomicProcessModel.setTxFee(btcIn - btcOut);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
