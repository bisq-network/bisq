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

package bisq.core.trade.protocol.bisq_v5.tasks.buyer;

import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerVerifiesWarningAndRedirectTxs extends TradeTask {
    public BuyerVerifiesWarningAndRedirectTxs(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            //todo

          /*  Transaction sellersPreparedDelayedPayoutTx = checkNotNull(processModel.getPreparedDelayedPayoutTx());
            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            TradeDataValidation.validateDelayedPayoutTx(trade,
                    sellersPreparedDelayedPayoutTx,
                    btcWalletService);

            Transaction preparedDepositTx = btcWalletService.getTxFromSerializedTx(processModel.getPreparedDepositTx());
            long inputAmount = preparedDepositTx.getOutput(0).getValue().value;
            long tradeTxFeeAsLong = trade.getTradeTxFeeAsLong();
            List<Tuple2<Long, String>> delayedPayoutTxReceivers = processModel.getDelayedPayoutTxReceiverService().getReceivers(
                    processModel.getBurningManSelectionHeight(),
                    inputAmount,
                    tradeTxFeeAsLong);

            long lockTime = trade.getLockTime();
            Transaction buyersPreparedDelayedPayoutTx = processModel.getTradeWalletService().createDelayedUnsignedPayoutTx(
                    preparedDepositTx,
                    delayedPayoutTxReceivers,
                    lockTime);
            if (!buyersPreparedDelayedPayoutTx.getTxId().equals(sellersPreparedDelayedPayoutTx.getTxId())) {
                String errorMsg = "TxIds of buyersPreparedDelayedPayoutTx and sellersPreparedDelayedPayoutTx must be the same.";
                log.error("{} \nbuyersPreparedDelayedPayoutTx={}, \nsellersPreparedDelayedPayoutTx={}, " +
                                "\nBtcWalletService.chainHeight={}, " +
                                "\nDaoState.chainHeight={}, " +
                                "\nisDaoStateIsInSync={}",
                        errorMsg, buyersPreparedDelayedPayoutTx, sellersPreparedDelayedPayoutTx,
                        processModel.getBtcWalletService().getBestChainHeight(),
                        processModel.getDaoFacade().getChainHeight(),
                        processModel.getDaoFacade().isDaoStateReadyAndInSync());
                throw new IllegalArgumentException(errorMsg);
            }

            // If the deposit tx is non-malleable, we already know its final ID, so should check that now
            // before sending any further data to the seller, to provide extra protection for the buyer.
            if (isDepositTxNonMalleable()) {
                TradeDataValidation.validatePayoutTxInput(preparedDepositTx, sellersPreparedDelayedPayoutTx);
            } else {
                log.info("Deposit tx is malleable, so we skip sellersPreparedDelayedPayoutTx input validation.");
            }*/

            complete();
       /* } catch (TradeDataValidation.ValidationException e) {
            failed(e.getMessage());*/
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
