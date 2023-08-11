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

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v5.messages.StagedPayoutTxRequest;
import bisq.core.util.Validator;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerProcessStagedPayoutTxRequest extends TradeTask {
    public BuyerProcessStagedPayoutTxRequest(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            TradingPeer tradingPeer = processModel.getTradePeer();

            StagedPayoutTxRequest request = (StagedPayoutTxRequest) processModel.getTradeMessage();
            checkNotNull(request);
            Validator.checkTradeId(processModel.getOfferId(), request);

            byte[] sellersWarningTx = checkNotNull(request.getSellersWarningTx());
            tradingPeer.setWarningTx(btcWalletService.getTxFromSerializedTx(sellersWarningTx));
            byte[] sellersWarningTxSellerSignature = checkNotNull(request.getSellersWarningTxSellerSignature());
            tradingPeer.setWarningTxSellerSignature(sellersWarningTxSellerSignature);
            byte[] sellersRedirectionTx = checkNotNull(request.getSellersRedirectionTx());
            tradingPeer.setRedirectTx(btcWalletService.getTxFromSerializedTx(sellersRedirectionTx));
            byte[] sellersRedirectionTxSellerSignature = checkNotNull(request.getSellersRedirectionTxSellerSignature());
            tradingPeer.setRedirectTxSellerSignature(sellersRedirectionTxSellerSignature);
            byte[] buyersWarningTxSellerSignature = checkNotNull(request.getBuyersWarningTxSellerSignature());
            processModel.setWarningTxSellerSignature(buyersWarningTxSellerSignature);

            // todo
            // When we receive that message the taker has published the taker fee, so we apply it to the trade.
            // The takerFeeTx was sent in the first message. It should be part of DelayedPayoutTxSignatureRequest
            // but that cannot be changed due backward compatibility issues. It is a left over from the old trade protocol.
            trade.setTakerFeeTxId(processModel.getTakeOfferFeeTxId());

            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
