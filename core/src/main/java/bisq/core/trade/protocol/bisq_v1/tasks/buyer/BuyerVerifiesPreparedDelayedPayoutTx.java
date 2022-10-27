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

import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.trade.DelayedPayoutReceiversUtil;
import bisq.core.trade.bisq_v1.TradeDataValidation;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Transaction;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
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

            Transaction preparedDelayedPayoutTx = checkNotNull(processModel.getPreparedDelayedPayoutTx());
            TradeDataValidation.validateDelayedPayoutTx(trade,
                    preparedDelayedPayoutTx,
                    processModel.getDaoFacade(),
                    processModel.getBtcWalletService());

            // If the deposit tx is non-malleable, we already know its final ID, so should check that now
            // before sending any further data to the seller, to provide extra protection for the buyer.
            boolean depositTxNonMalleable = isDepositTxNonMalleable();
            if (depositTxNonMalleable) {
                Transaction preparedDepositTx = processModel.getBtcWalletService().getTxFromSerializedTx(
                        processModel.getPreparedDepositTx());
                TradeDataValidation.validatePayoutTxInput(preparedDepositTx, preparedDelayedPayoutTx);
            } else {
                log.info("Deposit tx is malleable, so we skip preparedDelayedPayoutTx input validation.");
            }

            if (DelayedPayoutReceiversUtil.isActivated()) {
                // We create the delayed payout tx the same way as the seller and compare it with the one which we
                // received from the seller.
                TradeWalletService tradeWalletService = processModel.getTradeWalletService();
                long lockTime = trade.getLockTime();
                Transaction depositTx = processModel.getDepositTx();
                if (depositTx == null) {
                    depositTx = processModel.getBtcWalletService().getTxFromSerializedTx(
                            processModel.getPreparedDepositTx());
                }
                long inputAmount = depositTx.getOutput(0).getValue().value;
                List<Tuple2<Long, String>> receivers = DelayedPayoutReceiversUtil.getReceivers(processModel.getDaoFacade(),
                        processModel.getIssuanceList(),
                        inputAmount,
                        trade.getTradeTxFeeAsLong());
                Transaction buyersPreparedDelayedPayoutTx = tradeWalletService.createDelayedUnsignedPayoutTx(depositTx,
                        receivers,
                        lockTime);

                // preparedDepositTx has an unconnected input, so we only compare the outputs
                checkArgument(preparedDelayedPayoutTx.getOutputs().toString().equals(buyersPreparedDelayedPayoutTx.getOutputs().toString()),
                        "Tx outputs of preparedDelayedPayoutTx and buyersPreparedDelayedPayoutTx must be the same");
                if (depositTxNonMalleable) {
                    // If not malleable we check also the txIds
                    checkArgument(preparedDelayedPayoutTx.getTxId().toString().equals(buyersPreparedDelayedPayoutTx.getTxId().toString()),
                            "Tx IDs of preparedDelayedPayoutTx and buyersPreparedDelayedPayoutTx must be the same");
                }
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
