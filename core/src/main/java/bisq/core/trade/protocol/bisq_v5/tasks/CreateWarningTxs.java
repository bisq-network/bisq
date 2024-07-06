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

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.trade.model.bisq_v1.BuyerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v5.model.StagedPayoutTxParameters;

import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateWarningTxs extends TradeTask {
    public CreateWarningTxs(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            boolean amBuyer = trade instanceof BuyerTrade;
            TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            TradingPeer tradingPeer = processModel.getTradePeer();

            Transaction depositTx = btcWalletService.getTxFromSerializedTx(processModel.getPreparedDepositTx());
            TransactionOutput depositTxOutput = depositTx.getOutput(0);
            long lockTime = trade.getLockTime();
            byte[] buyerPubKey = amBuyer ? processModel.getMyMultiSigPubKey() : tradingPeer.getMultiSigPubKey();
            byte[] sellerPubKey = amBuyer ? tradingPeer.getMultiSigPubKey() : processModel.getMyMultiSigPubKey();
            long claimDelay = StagedPayoutTxParameters.CLAIM_DELAY; // FIXME: Make sure this is a low value off mainnet
            long miningFee = StagedPayoutTxParameters.getWarningTxMiningFee(trade.getDepositTxFeeRate());

            // Create our warning tx.
            String feeBumpAddress = processModel.getWarningTxFeeBumpAddress();
            var feeBumpOutputAmountAndAddress = new Tuple2<>(StagedPayoutTxParameters.WARNING_TX_FEE_BUMP_OUTPUT_VALUE, feeBumpAddress);

            Transaction unsignedWarningTx = tradeWalletService.createUnsignedWarningTx(amBuyer,
                    depositTxOutput,
                    lockTime,
                    buyerPubKey,
                    sellerPubKey,
                    claimDelay,
                    miningFee,
                    feeBumpOutputAmountAndAddress);
            processModel.setWarningTx(unsignedWarningTx);

            // Create peer's warning tx.
            String peersFeeBumpAddress = tradingPeer.getWarningTxFeeBumpAddress();
            var peersFeeBumpOutputAmountAndAddress = new Tuple2<>(StagedPayoutTxParameters.WARNING_TX_FEE_BUMP_OUTPUT_VALUE, peersFeeBumpAddress);

            Transaction peersUnsignedWarningTx = tradeWalletService.createUnsignedWarningTx(!amBuyer,
                    depositTxOutput,
                    lockTime,
                    buyerPubKey,
                    sellerPubKey,
                    claimDelay,
                    miningFee,
                    peersFeeBumpOutputAmountAndAddress);
            tradingPeer.setWarningTx(peersUnsignedWarningTx);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
