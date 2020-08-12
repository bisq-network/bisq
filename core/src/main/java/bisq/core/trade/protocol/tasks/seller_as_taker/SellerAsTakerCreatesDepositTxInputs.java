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

package bisq.core.trade.protocol.tasks.seller_as_taker;

import bisq.core.btc.model.InputsAndChangeOutput;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerAsTakerCreatesDepositTxInputs extends TradeTask {
    @SuppressWarnings({"unused"})
    public SellerAsTakerCreatesDepositTxInputs(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            if (trade.getTradeAmount() != null) {
                Coin txFee = trade.getTxFee();
                Coin takerInputAmount = checkNotNull(trade.getOffer()).getSellerSecurityDeposit()
                        .add(txFee)
                        .add(txFee)
                        .add(trade.getTradeAmount());
                InputsAndChangeOutput result = processModel.getTradeWalletService().takerCreatesDepositTxInputs(
                        processModel.getTakeOfferFeeTx(),
                        takerInputAmount,
                        txFee);

                processModel.setRawTransactionInputs(result.rawTransactionInputs);
                processModel.setChangeOutputValue(result.changeOutputValue);
                processModel.setChangeOutputAddress(result.changeOutputAddress);

                complete();
            } else {
                failed("trade.getTradeAmount() = null");
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
