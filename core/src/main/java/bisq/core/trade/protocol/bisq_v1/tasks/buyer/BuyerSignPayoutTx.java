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

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.crypto.DeterministicKey;

import com.google.common.base.Preconditions;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerSignPayoutTx extends TradeTask {

    public BuyerSignPayoutTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            Preconditions.checkNotNull(trade.getAmount(), "trade.getTradeAmount() must not be null");
            Preconditions.checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");
            Offer offer = Preconditions.checkNotNull(trade.getOffer(), "offer must not be null");

            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();

            Coin buyerPayoutAmount = offer.getBuyerSecurityDeposit().add(trade.getAmount());
            Coin sellerPayoutAmount = offer.getSellerSecurityDeposit();

            String buyerPayoutAddressString = walletService.getOrCreateAddressEntry(id,
                    AddressEntry.Context.TRADE_PAYOUT).getAddressString();
            final String sellerPayoutAddressString = processModel.getTradePeer().getPayoutAddressString();

            DeterministicKey buyerMultiSigKeyPair = walletService.getMultiSigKeyPair(id, processModel.getMyMultiSigPubKey());

            byte[] buyerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            Preconditions.checkArgument(Arrays.equals(buyerMultiSigPubKey,
                            walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG).getPubKey()),
                    "buyerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);
            byte[] sellerMultiSigPubKey = processModel.getTradePeer().getMultiSigPubKey();

            byte[] payoutTxSignature = processModel.getTradeWalletService().buyerSignsPayoutTx(
                    trade.getDepositTx(),
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    buyerPayoutAddressString,
                    sellerPayoutAddressString,
                    buyerMultiSigKeyPair,
                    buyerMultiSigPubKey,
                    sellerMultiSigPubKey);
            processModel.setPayoutTxSignature(payoutTxSignature);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

