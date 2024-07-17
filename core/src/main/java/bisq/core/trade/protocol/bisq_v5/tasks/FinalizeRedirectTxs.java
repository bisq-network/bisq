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
import bisq.core.trade.model.bisq_v1.BuyerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v5.model.StagedPayoutTxParameters;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FinalizeRedirectTxs extends TradeTask {
    public FinalizeRedirectTxs(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            TradingPeer tradingPeer = processModel.getTradePeer();

            // Get pubKeys and claim delay.
            boolean amBuyer = trade instanceof BuyerTrade;
            byte[] buyerPubKey = amBuyer ? processModel.getMyMultiSigPubKey() : tradingPeer.getMultiSigPubKey();
            byte[] sellerPubKey = amBuyer ? tradingPeer.getMultiSigPubKey() : processModel.getMyMultiSigPubKey();
            long claimDelay = StagedPayoutTxParameters.getClaimDelay();

            // Finalize our redirect tx.
            TransactionOutput peersWarningTxOutput = tradingPeer.getWarningTx().getOutput(0);
            Transaction redirectTx = processModel.getRedirectTx();
            byte[] buyerSignature = processModel.getRedirectTxBuyerSignature();
            byte[] sellerSignature = processModel.getRedirectTxSellerSignature();

            Transaction finalizedRedirectTx = tradeWalletService.finalizeRedirectionTx(peersWarningTxOutput,
                    redirectTx,
                    amBuyer,
                    claimDelay,
                    buyerPubKey,
                    sellerPubKey,
                    buyerSignature,
                    sellerSignature);
            processModel.setFinalizedRedirectTx(finalizedRedirectTx);

            // Finalize peer's redirect tx.
            TransactionOutput warningTxOutput = processModel.getWarningTx().getOutput(0);
            Transaction peersRedirectTx = tradingPeer.getRedirectTx();
            byte[] peerBuyerSignature = tradingPeer.getRedirectTxBuyerSignature();
            byte[] peerSellerSignature = tradingPeer.getRedirectTxSellerSignature();

            Transaction peersFinalizedRedirectTx = tradeWalletService.finalizeRedirectionTx(warningTxOutput,
                    peersRedirectTx,
                    !amBuyer,
                    claimDelay,
                    buyerPubKey,
                    sellerPubKey,
                    peerBuyerSignature,
                    peerSellerSignature);
            tradingPeer.setFinalizedRedirectTx(peersFinalizedRedirectTx);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
