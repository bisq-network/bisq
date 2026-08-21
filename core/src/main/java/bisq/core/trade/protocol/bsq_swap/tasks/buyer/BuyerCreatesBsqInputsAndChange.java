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

package bisq.core.trade.protocol.bsq_swap.tasks.buyer;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.trade.bsq_swap.BsqSwapCalculation;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.tasks.BsqSwapTask;

import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BuyerCreatesBsqInputsAndChange extends BsqSwapTask {

    @SuppressWarnings({"unused"})
    public BuyerCreatesBsqInputsAndChange(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            BsqWalletService bsqWalletService = protocolModel.getBsqWalletService();
            BtcWalletService btcWalletService = protocolModel.getBtcWalletService();

            long buyersTradeFee = getBuyersTradeFee();
            Coin required = BsqSwapCalculation.getBuyersBsqInputValue(trade, buyersTradeFee);
            Tuple2<List<RawTransactionInput>, Coin> inputsAndChange = bsqWalletService.getBuyersBsqInputsForBsqSwapTx(required);

            List<RawTransactionInput> inputs = inputsAndChange.first;
            protocolModel.setInputs(inputs);

            long change = inputsAndChange.second.value;
            if (Restrictions.isDust(Coin.valueOf(change))) {
                // If change would be dust we give spend it as miner fees
                change = 0;
            }
            protocolModel.setChange(change);

            protocolModel.setBsqAddress(bsqWalletService.getUnusedAddress().toString());
            protocolModel.setBtcAddress(btcWalletService.getFreshAddressEntry().getAddressString());

            int buyersTxSize = BsqSwapCalculation.getVBytesSize(inputs, change);
            long btcPayout = BsqSwapCalculation.getBuyersBtcPayoutValue(trade, buyersTxSize, buyersTradeFee).getValue();
            protocolModel.setPayout(btcPayout);

            long buyersTxFee = BsqSwapCalculation.getAdjustedTxFee(trade.getTxFeePerVbyte(), buyersTxSize, buyersTradeFee);
            protocolModel.setTxFee(buyersTxFee);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    protected abstract long getSellersTradeFee();

    protected abstract long getBuyersTradeFee();
}
