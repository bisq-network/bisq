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

package bisq.core.trade.protocol.bisq_v5.tasks;

import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v5.model.StagedPayoutTxParameters;

import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class CreateRedirectTxs extends TradeTask {
    public CreateRedirectTxs(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            TradingPeer tradingPeer = processModel.getTradePeer();

            // Get receiver amounts and addresses.
            TransactionOutput warningTxOutput = processModel.getWarningTx().getOutput(0);
            TransactionOutput peersWarningTxOutput = tradingPeer.getWarningTx().getOutput(0);

            long inputAmount = peersWarningTxOutput.getValue().value;
            checkArgument(warningTxOutput.getValue().value == inputAmount,
                    "Different warningTx output amounts. Ours: {}; Peer's: {}", warningTxOutput.getValue().value, inputAmount);

            long depositTxFee = trade.getTradeTxFeeAsLong(); // Used for fee rate calculation inside getDelayedPayoutTxReceiverService
            long inputAmountMinusFeeBumpAmount = inputAmount - StagedPayoutTxParameters.REDIRECT_TX_FEE_BUMP_OUTPUT_VALUE;
            int selectionHeight = processModel.getBurningManSelectionHeight();
            List<Tuple2<Long, String>> burningMen = processModel.getDelayedPayoutTxReceiverService().getReceivers(
                    selectionHeight,
                    inputAmountMinusFeeBumpAmount,
                    depositTxFee,
                    StagedPayoutTxParameters.REDIRECT_TX_MIN_WEIGHT,
                    true,
                    true);

            log.info("Create redirectionTxs using selectionHeight {} and receivers {}", selectionHeight, burningMen);

            // Create our redirect tx.
            String feeBumpAddress = processModel.getRedirectTxFeeBumpAddress();
            var feeBumpOutputAmountAndAddress = new Tuple2<>(StagedPayoutTxParameters.REDIRECT_TX_FEE_BUMP_OUTPUT_VALUE, feeBumpAddress);

            Transaction unsignedRedirectionTx = tradeWalletService.createUnsignedRedirectionTx(peersWarningTxOutput,
                    burningMen,
                    feeBumpOutputAmountAndAddress);
            processModel.setRedirectTx(unsignedRedirectionTx);

            // Create peer's redirect tx.
            String peersFeeBumpAddress = tradingPeer.getRedirectTxFeeBumpAddress();
            var peersFeeBumpOutputAmountAndAddress = new Tuple2<>(StagedPayoutTxParameters.REDIRECT_TX_FEE_BUMP_OUTPUT_VALUE, peersFeeBumpAddress);

            Transaction peersUnsignedRedirectionTx = tradeWalletService.createUnsignedRedirectionTx(warningTxOutput,
                    burningMen,
                    peersFeeBumpOutputAmountAndAddress);
            tradingPeer.setRedirectTx(peersUnsignedRedirectionTx);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
