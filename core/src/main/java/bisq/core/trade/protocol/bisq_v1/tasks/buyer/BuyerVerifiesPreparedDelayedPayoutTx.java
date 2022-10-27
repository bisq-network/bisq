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

import bisq.core.trade.DelayedPayoutReceiversUtil;
import bisq.core.trade.bisq_v1.TradeDataValidation;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerVerifiesPreparedDelayedPayoutTx extends TradeTask {
    public BuyerVerifiesPreparedDelayedPayoutTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Transaction sellersPreparedDelayedPayoutTx = checkNotNull(processModel.getPreparedDelayedPayoutTx());
            TradeDataValidation.validateDelayedPayoutTx(trade,
                    sellersPreparedDelayedPayoutTx,
                    processModel.getDaoFacade(),
                    processModel.getBtcWalletService());

            // If the deposit tx is non-malleable, we already know its final ID, so should check that now
            // before sending any further data to the seller, to provide extra protection for the buyer.
            boolean depositTxNonMalleable = isDepositTxNonMalleable();

            Transaction buyersPreparedDelayedPayoutTx = null;
            if (DelayedPayoutReceiversUtil.isActivated()) {
                buyersPreparedDelayedPayoutTx = DelayedPayoutReceiversUtil.createPreparedDelayedPayoutTx(
                        processModel.getTradeWalletService(),
                        processModel.getBtcWalletService(),
                        processModel.getDaoFacade(),
                        trade,
                        Optional.ofNullable(processModel.getDepositTx()),
                        processModel.getPreparedDepositTx(),
                        processModel.getIssuanceList()
                );
                TradeDataValidation.validateDelayedPayoutTx(trade,
                        buyersPreparedDelayedPayoutTx,
                        processModel.getDaoFacade(),
                        processModel.getBtcWalletService());

                TradeDataValidation.validateBothPreparedDelayedPayoutTxsAreEqual(buyersPreparedDelayedPayoutTx,
                        sellersPreparedDelayedPayoutTx,
                        depositTxNonMalleable);
            }

            if (depositTxNonMalleable) {
                Transaction preparedDepositTx = processModel.getBtcWalletService().getTxFromSerializedTx(
                        processModel.getPreparedDepositTx());
                TradeDataValidation.validatePayoutTxInput(preparedDepositTx, sellersPreparedDelayedPayoutTx);

                if (buyersPreparedDelayedPayoutTx != null) {
                    TradeDataValidation.validatePayoutTxInput(preparedDepositTx, buyersPreparedDelayedPayoutTx);
                }
            } else {
                log.info("Deposit tx is malleable, so we skip sellersPreparedDelayedPayoutTx input validation.");
            }

            complete();
        } catch (TradeDataValidation.ValidationException e) {
            failed(e.getMessage());
        } catch (Throwable t) {
            failed(t);
        }
    }

    private boolean isDepositTxNonMalleable() {
        var buyerInputs = checkNotNull(processModel.getRawTransactionInputs());
        var sellerInputs = checkNotNull(processModel.getTradePeer().getRawTransactionInputs());

        return buyerInputs.stream().allMatch(processModel.getTradeWalletService()::isP2WH) &&
                sellerInputs.stream().allMatch(processModel.getTradeWalletService()::isP2WH);
    }
}
