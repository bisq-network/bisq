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

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.bisq_v1.TradeDataValidation;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Transaction;

import java.util.List;

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

            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            Transaction finalDelayedPayoutTx = trade.getDelayedPayoutTx();
            checkNotNull(finalDelayedPayoutTx, "trade.getDelayedPayoutTx() must not be null");

            // Check again tx
            TradeDataValidation.validateDelayedPayoutTx(trade,
                    finalDelayedPayoutTx,
                    btcWalletService);

            Transaction depositTx = trade.getDepositTx();
            checkNotNull(depositTx, "trade.getDepositTx() must not be null");
            // Now as we know the deposit tx we can also verify the input
            TradeDataValidation.validatePayoutTxInput(depositTx, finalDelayedPayoutTx);

            long inputAmount = depositTx.getOutput(0).getValue().value;
            long tradeTxFeeAsLong = trade.getTradeTxFeeAsLong();
            int selectionHeight = processModel.getBurningManSelectionHeight();
            List<Tuple2<Long, String>> delayedPayoutTxReceivers = processModel.getDelayedPayoutTxReceiverService().getReceivers(
                    selectionHeight,
                    inputAmount,
                    tradeTxFeeAsLong);
            log.info("Verify delayedPayoutTx using selectionHeight {} and receivers {}", selectionHeight, delayedPayoutTxReceivers);
            long lockTime = trade.getLockTime();
            Transaction buyersDelayedPayoutTx = processModel.getTradeWalletService().createDelayedUnsignedPayoutTx(
                    depositTx,
                    delayedPayoutTxReceivers,
                    lockTime);

            if (!buyersDelayedPayoutTx.getTxId().equals(finalDelayedPayoutTx.getTxId())) {
                String errorMsg = "TxIds of buyersDelayedPayoutTx and finalDelayedPayoutTx must be the same.";
                log.error("{} \nbuyersDelayedPayoutTx={}, \nfinalDelayedPayoutTx={}, " +
                                "\nBtcWalletService.chainHeight={}, " +
                                "\nDaoState.chainHeight={}, " +
                                "\nisDaoStateIsInSync={}",
                        errorMsg, buyersDelayedPayoutTx, finalDelayedPayoutTx,
                        processModel.getBtcWalletService().getBestChainHeight(),
                        processModel.getDaoFacade().getChainHeight(),
                        processModel.getDaoFacade().isDaoStateReadyAndInSync());
                throw new IllegalArgumentException(errorMsg);
            }

            complete();
        } catch (TradeDataValidation.ValidationException e) {
            failed(e.getMessage());
        } catch (Throwable t) {
            failed(t);
        }
    }
}
