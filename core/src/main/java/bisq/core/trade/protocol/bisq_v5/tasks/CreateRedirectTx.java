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

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
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

@Slf4j
public class CreateRedirectTx extends TradeTask {
    public CreateRedirectTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            String tradeId = processModel.getOffer().getId();
            TradingPeer tradingPeer = processModel.getTradePeer();

            TransactionOutput peersWarningTxOutput = tradingPeer.getWarningTx().getOutput(0);
            long inputAmount = peersWarningTxOutput.getValue().value;
            long depositTxFee = trade.getTradeTxFeeAsLong(); // Used for fee rate calculation inside getDelayedPayoutTxReceiverService
            int selectionHeight = processModel.getBurningManSelectionHeight();
            List<Tuple2<Long, String>> burningMen = processModel.getDelayedPayoutTxReceiverService().getReceivers(
                    selectionHeight,
                    inputAmount,
                    depositTxFee);
            log.info("Create redirectionTx using selectionHeight {} and receivers {}", selectionHeight, burningMen);

            AddressEntry feeBumpAddressEntry = btcWalletService.getOrCreateAddressEntry(tradeId, AddressEntry.Context.REDIRECT_TX_FEE_BUMP);
            Tuple2<Long, String> feeBumpOutputAmountAndAddress = new Tuple2<>(StagedPayoutTxParameters.REDIRECT_TX_FEE_BUMP_OUTPUT_VALUE, feeBumpAddressEntry.getAddressString());

            Transaction unsignedRedirectionTx = tradeWalletService.createUnsignedRedirectionTx(peersWarningTxOutput,
                    burningMen,
                    feeBumpOutputAmountAndAddress);
            processModel.setRedirectTx(unsignedRedirectionTx);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
