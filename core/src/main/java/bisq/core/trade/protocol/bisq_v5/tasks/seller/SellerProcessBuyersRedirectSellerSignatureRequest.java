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

package bisq.core.trade.protocol.bisq_v5.tasks.seller;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v5.messages.BuyersRedirectSellerSignatureRequest;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.Validator.checkTradeId;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerProcessBuyersRedirectSellerSignatureRequest extends TradeTask {
    public SellerProcessBuyersRedirectSellerSignatureRequest(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            TradingPeer tradingPeer = processModel.getTradePeer();
            BtcWalletService btcWalletService = processModel.getBtcWalletService();

            BuyersRedirectSellerSignatureRequest response = (BuyersRedirectSellerSignatureRequest) processModel.getTradeMessage();
            checkNotNull(response);
            checkTradeId(processModel.getOfferId(), response);

            byte[] sellersWarningTxBuyerSignature = checkNotNull(response.getSellersWarningTxBuyerSignature());
            processModel.setWarningTxBuyerSignature(sellersWarningTxBuyerSignature);

            byte[] sellersRedirectTxBuyerSignature = checkNotNull(response.getSellersRedirectTxBuyerSignature());
            processModel.setRedirectTxBuyerSignature(sellersRedirectTxBuyerSignature);

            byte[] buyersRedirectTx = checkNotNull(response.getBuyersRedirectTx());
            tradingPeer.setRedirectTx(btcWalletService.getTxFromSerializedTx(buyersRedirectTx));

            byte[] buyersRedirectTxBuyerSignature = checkNotNull(response.getBuyersRedirectTxBuyerSignature());
            tradingPeer.setRedirectTxBuyerSignature(buyersRedirectTxBuyerSignature);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
