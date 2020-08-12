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

package bisq.core.trade.protocol.tasks.buyer;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.crypto.DeterministicKey;

import com.google.common.base.Preconditions;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerSignPayoutTx extends TradeTask {

    @SuppressWarnings({"unused"})
    public BuyerSignPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            Preconditions.checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not be null");
            Preconditions.checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");
            Offer offer = checkNotNull(trade.getOffer(), "offer must not be null");

            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();

            Coin buyerPayoutAmount = offer.getBuyerSecurityDeposit().add(trade.getTradeAmount());
            Coin sellerPayoutAmount = offer.getSellerSecurityDeposit();

            String buyerPayoutAddressString = walletService.getOrCreateAddressEntry(id,
                    AddressEntry.Context.TRADE_PAYOUT).getAddressString();
            final String sellerPayoutAddressString = processModel.getTradingPeer().getPayoutAddressString();

            DeterministicKey buyerMultiSigKeyPair = walletService.getMultiSigKeyPair(id, processModel.getMyMultiSigPubKey());

            byte[] buyerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(buyerMultiSigPubKey,
                    walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG).getPubKey()),
                    "buyerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);
            byte[] sellerMultiSigPubKey = processModel.getTradingPeer().getMultiSigPubKey();

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

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

