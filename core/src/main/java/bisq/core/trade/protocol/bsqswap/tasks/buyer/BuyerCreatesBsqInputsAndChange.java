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

package bisq.core.trade.protocol.bsqswap.tasks.buyer;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.BsqSwapCalculation;
import bisq.core.trade.protocol.bsqswap.tasks.BsqSwapTask;

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
            runInterceptHook();

            BsqWalletService bsqWalletService = protocolModel.getBsqWalletService();
            BtcWalletService btcWalletService = protocolModel.getBtcWalletService();

            Coin required = BsqSwapCalculation.getBuyersRequiredBsqInputs(trade);
            Tuple2<List<RawTransactionInput>, Coin> tuple = bsqWalletService.getBuyersBsqInputsForBsqSwapTx(required);

            protocolModel.setInputs(tuple.first);
            protocolModel.setChange(tuple.second.value);
            protocolModel.setBsqAddress(bsqWalletService.getUnusedAddress().toString());
            protocolModel.setBtcAddress(btcWalletService.getFreshAddressEntry().getAddressString());

            long payout = BsqSwapCalculation.getBuyersBtcPayoutAmount(getSellersTradeFee(),
                    trade,
                    getBuyersTxSize(),
                    getBuyersTradeFee());
            protocolModel.setPayout(payout);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private int getBuyersTxSize() {
        return BsqSwapCalculation.getTxSize(protocolModel.getTradeWalletService(),
                protocolModel.getInputs(),
                protocolModel.getChange());
    }

    protected abstract long getSellersTradeFee();

    protected abstract long getBuyersTradeFee();
}
