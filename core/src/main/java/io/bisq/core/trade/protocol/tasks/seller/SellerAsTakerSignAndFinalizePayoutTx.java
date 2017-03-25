/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.seller;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.Offer;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.TradingPeer;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerAsTakerSignAndFinalizePayoutTx extends TradeTask {

    @SuppressWarnings({"WeakerAccess", "unused"})
    public SellerAsTakerSignAndFinalizePayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not be null");
            Offer offer = trade.getOffer();
            Coin sellerPayoutAmount = offer.getSellerSecurityDeposit();
            Coin buyerPayoutAmount = offer.getBuyerSecurityDeposit().add(trade.getTradeAmount());
            BtcWalletService walletService = processModel.getWalletService();
            String id = processModel.getOffer().getId();
            String sellerPayoutAddressString = walletService.getOrCreateAddressEntry(id,
                    AddressEntry.Context.TRADE_PAYOUT).getAddressString();
            byte[] sellerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            DeterministicKey multiSigKeyPair = walletService.getMultiSigKeyPair(id, sellerMultiSigPubKey);
            checkArgument(Arrays.equals(sellerMultiSigPubKey,
                            walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG).getPubKey()),
                    "sellerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);
            TradingPeer tradingPeer = processModel.tradingPeer;
            final byte[] buyerSignature = tradingPeer.getSignature();
            final byte[] buyerMultiSigPubKey = tradingPeer.getMultiSigPubKey();
            Transaction transaction = processModel.getTradeWalletService().sellerSignsAndFinalizesPayoutTx(
                    trade.getDepositTx(),
                    buyerSignature,
                    buyerPayoutAmount,
                    sellerPayoutAmount,
                    tradingPeer.getPayoutAddressString(),
                    sellerPayoutAddressString,
                    multiSigKeyPair,
                    buyerMultiSigPubKey,
                    sellerMultiSigPubKey,
                    trade.getArbitratorPubKey()
            );

            trade.setPayoutTx(transaction);
            trade.setState(Trade.State.SELLER_COMMITTED_PAYOUT_TX);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
