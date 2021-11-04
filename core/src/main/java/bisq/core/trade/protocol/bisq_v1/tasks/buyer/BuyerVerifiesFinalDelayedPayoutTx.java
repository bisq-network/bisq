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

package bisq.core.trade.protocol.bisq_v1.tasks.buyer;

import bisq.core.trade.bisq_v1.TradeDataValidation;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerVerifiesFinalDelayedPayoutTx extends TradeTask {
    public BuyerVerifiesFinalDelayedPayoutTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Transaction delayedPayoutTx = trade.getDelayedPayoutTx();
            checkNotNull(delayedPayoutTx, "trade.getDelayedPayoutTx() must not be null");
            // Check again tx
            TradeDataValidation.validateDelayedPayoutTx(trade,
                    delayedPayoutTx,
                    processModel.getDaoFacade(),
                    processModel.getBtcWalletService());

            // Now as we know the deposit tx we can also verify the input
            Transaction depositTx = trade.getDepositTx();
            checkNotNull(depositTx, "trade.getDepositTx() must not be null");
            TradeDataValidation.validatePayoutTxInput(depositTx, delayedPayoutTx);

            complete();
        } catch (TradeDataValidation.ValidationException e) {
            failed(e.getMessage());
        } catch (Throwable t) {
            failed(t);
        }
    }
}
