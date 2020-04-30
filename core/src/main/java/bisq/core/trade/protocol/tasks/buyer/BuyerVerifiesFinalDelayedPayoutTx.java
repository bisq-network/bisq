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

package bisq.core.trade.protocol.tasks.buyer;

import bisq.core.trade.DelayedPayoutTxValidation;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerVerifiesFinalDelayedPayoutTx extends TradeTask {
    @SuppressWarnings({"unused"})
    public BuyerVerifiesFinalDelayedPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Transaction delayedPayoutTx = trade.getDelayedPayoutTx();
            // Check again tx
            DelayedPayoutTxValidation.validatePayoutTx(trade,
                    delayedPayoutTx,
                    processModel.getDaoFacade(),
                    processModel.getBtcWalletService());

            complete();
        } catch (DelayedPayoutTxValidation.DonationAddressException |
                DelayedPayoutTxValidation.MissingDelayedPayoutTxException |
                DelayedPayoutTxValidation.InvalidTxException |
                DelayedPayoutTxValidation.InvalidLockTimeException |
                DelayedPayoutTxValidation.AmountMismatchException e) {
            failed(e.getMessage());
        } catch (Throwable t) {
            failed(t);
        }
    }
}
